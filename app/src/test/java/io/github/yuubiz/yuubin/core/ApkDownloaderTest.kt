package io.github.yuubiz.yuubin.core

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import kotlin.random.Random

class ApkDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var downloader: ApkDownloader
    private lateinit var target: File
    private val progress = mutableListOf<Long>()

    private val apk = Random(42).nextBytes(300_000)

    @Before
    fun setUp() {
        // A literal IP in both the URL and the certificate: the loopback name may reverse-resolve to something else.
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName(LOOPBACK).build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        server.useHttps(serverTls.sslSocketFactory())
        server.start(InetAddress.getByName(LOOPBACK), 0)
        downloader = ApkDownloader(
            OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build(),
        )
        target = File(temp.root, "apk/app.apk")
    }

    @After
    fun tearDown() = server.close()

    private fun url(path: String): HttpUrl = server.url(path).newBuilder().host(LOOPBACK).build()

    private fun sha256(bytes: ByteArray): Sha256 = Sha256.of(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun asset(
        url: String = url("/a.apk").toString(),
        size: Long = apk.size.toLong(),
        sha256: Sha256 = sha256(apk),
    ) = ApkAsset("a.apk", url, size, sha256)

    private fun serve(body: ByteArray, configure: MockResponse.Builder.() -> Unit = {}) {
        server.enqueue(MockResponse.Builder().body(Buffer().write(body)).apply(configure).build())
    }

    private fun download(asset: ApkAsset): Download = runBlocking { downloader.download(asset, target) { progress += it } }

    private fun assertFailed(expected: InstallFailure, result: Download) {
        assertEquals(result.toString(), expected, (result as? Download.Failed)?.reason)
        assertFalse("the partial file is deleted", target.exists())
    }

    @Test
    fun `a body matching size and digest is kept and progress reaches the total`() {
        serve(apk)

        val result = download(asset())

        assertEquals(Download.Done(target), result)
        assertArrayEquals(apk, target.readBytes())
        assertEquals(apk.size.toLong(), progress.last())
        assertEquals(progress.sorted(), progress)
    }

    @Test
    fun `a stale file at the target is replaced`() {
        target.parentFile!!.mkdirs()
        target.writeBytes(ByteArray(10))
        serve(apk)

        download(asset())

        assertArrayEquals(apk, target.readBytes())
    }

    @Test
    fun `a body with another digest is rejected`() {
        serve(apk.copyOf().also { it[1000] = (it[1000] + 1).toByte() })
        assertFailed(InstallFailure.HASH_MISMATCH, download(asset()))
    }

    @Test
    fun `a body shorter than the release says is an incomplete download`() {
        serve(apk.copyOf(apk.size - 1))
        assertFailed(InstallFailure.DOWNLOAD, download(asset()))
    }

    @Test
    fun `a body longer than the release says is rejected`() {
        serve(apk + ByteArray(1))
        assertFailed(InstallFailure.HASH_MISMATCH, download(asset()))
    }

    @Test
    fun `a connection cut mid-body is a download failure`() {
        serve(apk) { onResponseBody(SocketEffect.ShutdownConnection) }
        assertFailed(InstallFailure.DOWNLOAD, download(asset()))
    }

    @Test
    fun `an HTTP error is a download failure`() {
        server.enqueue(MockResponse.Builder().code(404).body("missing").build())
        assertFailed(InstallFailure.DOWNLOAD, download(asset()))
    }

    @Test
    fun `an https redirect is followed`() {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", url("/signed/a.apk")).build())
        serve(apk)

        assertEquals(Download.Done(target), download(asset()))
        server.takeRequest()
        assertEquals("/signed/a.apk", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `a redirect to cleartext is not followed`() {
        val cleartext = url("/a.apk").newBuilder().scheme("http").build()
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", cleartext).build())

        assertFailed(InstallFailure.DOWNLOAD, download(asset()))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a cleartext asset URL is refused without a request`() {
        val cleartext = url("/a.apk").newBuilder().scheme("http").build().toString()

        assertFailed(InstallFailure.DOWNLOAD, download(asset(url = cleartext)))
        assertEquals(0, server.requestCount)
    }

    private companion object {
        const val LOOPBACK = "127.0.0.1"
    }
}
