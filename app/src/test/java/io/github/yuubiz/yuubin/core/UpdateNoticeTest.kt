package io.github.yuubiz.yuubin.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateNoticeTest {
    private val sha = requireNotNull(Sha256.parse("ab".repeat(32)))
    private val a = CatalogApp("com.example.a", "A", GitHubRepo("o", "a"), Regex("a\\.apk"), sha)
    private val b = CatalogApp("com.example.b", "B", GitHubRepo("o", "b"), Regex("b\\.apk"), sha)

    private fun release(tag: String) = Release(tag, Version.parse(tag), ApkAsset("x.apk", "https://x/x.apk", 1, sha), "", "https://x")
    private fun installed(versionName: String, signerMatches: Boolean = true) = Installed.Yes(versionName, 1, signerMatches)
    private fun update(from: String, to: String) = AppStatus.UpdateAvailable(installed(from), release(to))

    @Test
    fun `a new update is announced and recorded`() {
        val notice = updateNotice(listOf(a to update("1.0.0", "v1.1.0")), notified = emptyMap())

        assertEquals(UpdateNotice(listOf(a), mapOf(a.packageName to "v1.1.0")), notice)
    }

    @Test
    fun `an update already announced is not announced again`() {
        assertNull(updateNotice(listOf(a to update("1.0.0", "v1.1.0")), notified = mapOf(a.packageName to "v1.1.0")))
    }

    @Test
    fun `a newer release of an announced app is announced`() {
        val notice = updateNotice(listOf(a to update("1.0.0", "v1.2.0")), notified = mapOf(a.packageName to "v1.1.0"))

        assertEquals(UpdateNotice(listOf(a), mapOf(a.packageName to "v1.2.0")), notice)
    }

    @Test
    fun `only installed apps with an update count`() {
        val statuses = listOf(
            a to AppStatus.NotInstalled(release("v1.1.0")),
            a to AppStatus.UpToDate(installed("1.1.0"), release("v1.1.0")),
            a to AppStatus.SignerMismatch(installed("1.0.0", signerMatches = false)),
            a to AppStatus.ReleaseUnavailable(installed("1.0.0"), LookupFailure.NETWORK),
            a to null,
        )
        statuses.forEach { assertNull("$it", updateNotice(listOf(it), notified = emptyMap())) }
    }

    @Test
    fun `a new update lists every pending update and keeps earlier records`() {
        val gone = "com.example.gone" to "v9.0.0"
        val notice = updateNotice(
            listOf(a to update("1.0.0", "v1.1.0"), b to update("2.0.0", "v2.1.0")),
            notified = mapOf(a.packageName to "v1.1.0", gone),
        )

        assertEquals(UpdateNotice(listOf(a, b), mapOf(a.packageName to "v1.1.0", b.packageName to "v2.1.0", gone)), notice)
    }

    @Test
    fun `a failed lookup in between does not announce the same version twice`() {
        val first = requireNotNull(updateNotice(listOf(a to update("1.0.0", "v1.1.0")), notified = emptyMap()))
        val failed = updateNotice(listOf(a to AppStatus.ReleaseUnavailable(installed("1.0.0"), LookupFailure.RATE_LIMITED)), first.notified)

        assertNull(failed)
        assertNull(updateNotice(listOf(a to update("1.0.0", "v1.1.0")), first.notified))
    }
}
