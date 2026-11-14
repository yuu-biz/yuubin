package io.github.yuubiz.yuubin.core

import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class GitHubReleasesTest {
    private val server = MockWebServer()
    private lateinit var releases: GitHubReleases

    @Before
    fun setUp() {
        server.start()
        releases = GitHubReleases(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() = server.close()

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/github/$name")) { "missing fixture $name" }.readText()

    private fun respond(code: Int = 200, body: String = "", vararg headers: Pair<String, String>) {
        server.enqueue(
            MockResponse.Builder().code(code).body(body).apply { headers.forEach { (k, v) -> addHeader(k, v) } }.build(),
        )
    }

    private fun lookup(app: CatalogApp = Catalog.folderPlayerFork): ReleaseLookup = runBlocking { releases.latest(app) }

    private fun failure(app: CatalogApp = Catalog.folderPlayerFork): LookupFailure? =
        (lookup(app) as? ReleaseLookup.Unavailable)?.reason

    private val goodDigest = "sha256:" + "ab".repeat(32)

    private fun asset(name: String, digest: String? = goodDigest, size: Long = 100): String =
        """{"name":"$name","browser_download_url":"https://github.com/d/$name","size":$size,"digest":${digest?.let { "\"$it\"" } ?: "null"}}"""

    private fun release(vararg assets: String): String =
        """{"tag_name":"v1.0.0","html_url":"https://github.com/r","body":null,"assets":[${assets.joinToString(",")}]}"""

    @Test
    fun `the real response shape yields the APK asset and release metadata`() {
        respond(body = fixture("folder-player-v1.10.2.json"))

        val release = (lookup() as ReleaseLookup.Found).release

        assertEquals("v1.10.2", release.tag)
        assertEquals(Version(1, 10, 2, null), release.version)
        assertEquals("FolderPlayerFork-1.10.2.apk", release.apk.name)
        assertEquals(24885333L, release.apk.sizeBytes)
        assertEquals(Sha256.parse("2e32f2189f5c3ced7d9f0d3fb84507eb10208f4fa629b3cfe603f83a2ed24094"), release.apk.sha256)
        assertEquals(
            "https://github.com/yuu-biz/Folder-Player/releases/download/v1.10.2/FolderPlayerFork-1.10.2.apk",
            release.apk.url,
        )
        assertEquals("https://github.com/yuu-biz/Folder-Player/releases/tag/v1.10.2", release.pageUrl)
        assertEquals("## Changes\n\n- Synthetic release notes for tests.", release.notes)
    }

    @Test
    fun `a response still trickling at the call timeout is a network failure`() {
        releases = GitHubReleases(OkHttpClient(), server.url("/"), callTimeout = 500.milliseconds)
        // About 19 s for the whole body, yet every read returns well within the default read timeout.
        server.enqueue(
            MockResponse.Builder().body(fixture("folder-player-v1.10.2.json")).throttleBody(16, 100, TimeUnit.MILLISECONDS).build(),
        )

        assertEquals(LookupFailure.NETWORK, failure())
    }

    @Test
    fun `the request targets releases latest with the GitHub media type and a generic user agent`() {
        respond(body = fixture("folder-player-v1.10.2.json"))
        lookup()

        val request = server.takeRequest()
        assertEquals("/repos/yuu-biz/Folder-Player/releases/latest", request.url.encodedPath)
        assertEquals("application/vnd.github+json", request.headers["Accept"])
        assertEquals("Yuubin", request.headers["User-Agent"])
        assertEquals(null, request.headers["Authorization"])
    }

    @Test
    fun `a tag that is not a version still yields a release`() {
        respond(body = release(asset("FolderPlayerFork-1.0.0.apk")).replace("v1.0.0", "nightly"))
        val release = (lookup() as ReleaseLookup.Found).release
        assertEquals("nightly", release.tag)
        assertEquals(null, release.version)
        assertEquals("", release.notes)
    }

    @Test
    fun `404 means not found, which is what a private repository answers`() {
        respond(code = 404, body = """{"message":"Not Found"}""")
        assertEquals(LookupFailure.NOT_FOUND, failure(Catalog.yuubin))
    }

    @Test
    fun `403 with no remaining quota is rate limiting`() {
        respond(code = 403, body = "{}", "x-ratelimit-remaining" to "0")
        assertEquals(LookupFailure.RATE_LIMITED, failure())
    }

    @Test
    fun `429 with retry-after is rate limiting`() {
        respond(code = 429, body = "{}", "retry-after" to "60")
        assertEquals(LookupFailure.RATE_LIMITED, failure())
    }

    @Test
    fun `403 without rate-limit signals is not reported as rate limiting`() {
        respond(code = 403, body = "{}", "x-ratelimit-remaining" to "42")
        assertEquals(LookupFailure.NETWORK, failure())
    }

    @Test
    fun `a server error is reported like a network failure`() {
        respond(code = 502, body = "bad gateway")
        assertEquals(LookupFailure.NETWORK, failure())
    }

    @Test
    fun `an unreachable server is a network failure`() {
        val unreachable = GitHubReleases(OkHttpClient(), server.url("/"))
        server.close()
        assertEquals(LookupFailure.NETWORK, (runBlocking { unreachable.latest(Catalog.folderPlayerFork) } as ReleaseLookup.Unavailable).reason)
    }

    @Test
    fun `no asset matching the pattern is no APK asset`() {
        respond(body = release(asset("ffmpeg-7.1.5.tar.xz"), asset("SHA256SUMS.txt")))
        assertEquals(LookupFailure.NO_APK_ASSET, failure())
    }

    @Test
    fun `the pattern must match the whole asset name`() {
        respond(body = release(asset("FolderPlayerFork-1.10.2.apk.idsig"), asset("old-FolderPlayerFork-1.10.2.apk")))
        assertEquals(LookupFailure.NO_APK_ASSET, failure())
    }

    @Test
    fun `two matching assets are ambiguous`() {
        respond(body = release(asset("FolderPlayerFork-1.10.2.apk"), asset("FolderPlayerFork-1.10.3.apk")))
        assertEquals(LookupFailure.AMBIGUOUS_APK_ASSET, failure())
    }

    @Test
    fun `a missing digest is no digest`() {
        respond(body = release(asset("FolderPlayerFork-1.10.2.apk", digest = null)))
        assertEquals(LookupFailure.NO_DIGEST, failure())
    }

    @Test
    fun `an absent digest key is no digest`() {
        respond(body = release("""{"name":"FolderPlayerFork-1.10.2.apk","browser_download_url":"https://x/a","size":1}"""))
        assertEquals(LookupFailure.NO_DIGEST, failure())
    }

    @Test
    fun `a digest of another algorithm or malformed hex is no digest`() {
        listOf("sha512:" + "ab".repeat(32), "sha256:xyz", "ab".repeat(32)).forEach { digest ->
            respond(body = release(asset("FolderPlayerFork-1.10.2.apk", digest = digest)))
            assertEquals(digest, LookupFailure.NO_DIGEST, failure())
        }
    }

    @Test
    fun `invalid JSON is malformed`() {
        respond(body = "<html>not json</html>")
        assertEquals(LookupFailure.MALFORMED, failure())
    }

    @Test
    fun `JSON without the required fields is malformed`() {
        respond(body = """{"assets":[]}""")
        assertEquals(LookupFailure.MALFORMED, failure())
    }

    @Test
    fun `an empty APK asset is malformed`() {
        respond(body = release(asset("FolderPlayerFork-1.10.2.apk", size = 0)))
        assertEquals(LookupFailure.MALFORMED, failure())
    }
}
