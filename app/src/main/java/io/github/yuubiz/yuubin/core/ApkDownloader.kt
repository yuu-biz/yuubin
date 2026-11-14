package io.github.yuubiz.yuubin.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

sealed interface Download {
    data class Done(val file: File) : Download
    data class Failed(val reason: InstallFailure, val detail: String? = null) : Download
}

/** Streams a release asset to a file, hashing as it writes. On any failure the file is gone. */
class ApkDownloader(client: OkHttpClient, callTimeout: Duration = 10.minutes) {
    // GitHub answers with a redirect to a signed URL; following it must never drop to cleartext. The call timeout
    // bounds the whole download, which a trickling server would otherwise keep alive past every read timeout.
    private val client = client.newBuilder()
        .followRedirects(true)
        .followSslRedirects(false)
        .callTimeout(callTimeout)
        .build()

    suspend fun download(asset: ApkAsset, target: File, onProgress: (bytes: Long) -> Unit): Download =
        withContext(Dispatchers.IO) {
            val url = asset.url.toHttpUrlOrNull()
            if (url == null || !url.isHttps) return@withContext Download.Failed(InstallFailure.DOWNLOAD, "not an https URL")
            var done = false
            try {
                target.parentFile?.mkdirs()
                val result = try {
                    fetch(url, asset, target, onProgress)
                } catch (e: IOException) {
                    Download.Failed(InstallFailure.DOWNLOAD, e.message)
                }
                done = result is Download.Done
                result
            } finally {
                if (!done) target.delete()
            }
        }

    private suspend fun fetch(url: HttpUrl, asset: ApkAsset, target: File, onProgress: (Long) -> Unit): Download {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return Download.Failed(InstallFailure.DOWNLOAD, "HTTP ${response.code}")
            val digest = MessageDigest.getInstance("SHA-256")
            var bytes = 0L
            response.body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read == -1) break
                        bytes += read
                        if (bytes > asset.sizeBytes) {
                            return Download.Failed(InstallFailure.HASH_MISMATCH, "larger than ${asset.sizeBytes} bytes")
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        onProgress(bytes)
                    }
                }
            }
            // A cut connection does not always surface as an IOException, so a short body counts as an incomplete
            // download, not as tampering.
            if (bytes < asset.sizeBytes) {
                return Download.Failed(InstallFailure.DOWNLOAD, "$bytes of ${asset.sizeBytes} bytes")
            }
            val actual = Sha256.of(digest.digest())
            if (actual != asset.sha256) return Download.Failed(InstallFailure.HASH_MISMATCH, "sha256 $actual")
            return Download.Done(target)
        }
    }
}
