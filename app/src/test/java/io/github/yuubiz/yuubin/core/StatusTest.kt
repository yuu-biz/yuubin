package io.github.yuubiz.yuubin.core

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.reflect.KClass

class StatusTest {
    private val sha = requireNotNull(Sha256.parse("ab".repeat(32)))

    private fun found(tag: String) = ReleaseLookup.Found(
        Release(tag, Version.parse(tag), ApkAsset("a.apk", "https://x/a.apk", 1, sha), "", "https://x"),
    )

    private fun installed(versionName: String, signerMatches: Boolean = true) =
        Installed.Yes(versionName, versionCode = 1, signerMatches = signerMatches)

    private val notFound = ReleaseLookup.Unavailable(LookupFailure.NOT_FOUND)

    private data class Row(val installed: Installed, val lookup: ReleaseLookup, val expected: KClass<out AppStatus>)

    @Test
    fun `status table`() {
        val rows = listOf(
            Row(Installed.No, found("v1.10.2"), AppStatus.NotInstalled::class),
            Row(Installed.No, notFound, AppStatus.ReleaseUnavailable::class),
            Row(installed("1.10.1"), found("v1.10.2"), AppStatus.UpdateAvailable::class),
            Row(installed("1.9.9"), found("v1.10.0"), AppStatus.UpdateAvailable::class),
            Row(installed("1.10.2"), found("v1.10.2"), AppStatus.UpToDate::class),
            Row(installed("1.10.3"), found("v1.10.2"), AppStatus.UpToDate::class),
            Row(installed("1.0.0-rc.3"), found("v1.0.0"), AppStatus.UpdateAvailable::class),
            Row(installed("0.5.0-fork.1"), found("v0.5.0-fork.2"), AppStatus.UpdateAvailable::class),
            Row(installed("1.10.2"), notFound, AppStatus.ReleaseUnavailable::class),
            // Unparsable on either side: any difference from the tag (minus the v) is offered as an update.
            Row(installed("nightly-7"), found("v1.10.2"), AppStatus.UpdateAvailable::class),
            Row(installed("1.10.2"), found("nightly-8"), AppStatus.UpdateAvailable::class),
            Row(installed("nightly-8"), found("nightly-8"), AppStatus.UpToDate::class),
            Row(installed("build"), found("vbuild"), AppStatus.UpToDate::class),
            // A foreign signer wins over everything, even with no release to compare.
            Row(installed("1.10.1", signerMatches = false), found("v1.10.2"), AppStatus.SignerMismatch::class),
            Row(installed("1.10.2", signerMatches = false), notFound, AppStatus.SignerMismatch::class),
        )
        rows.forEach { row ->
            val status = appStatus(row.installed, row.lookup)
            assertEquals("${row.installed} / ${row.lookup}", row.expected, status::class)
        }
    }

    @Test
    fun `statuses carry what the list needs`() {
        val release = found("v1.10.2")
        val old = installed("1.10.1")
        assertEquals(AppStatus.UpdateAvailable(old, release.release), appStatus(old, release))
        assertEquals(AppStatus.ReleaseUnavailable(old, LookupFailure.NOT_FOUND), appStatus(old, notFound))
        assertEquals(AppStatus.ReleaseUnavailable(Installed.No, LookupFailure.RATE_LIMITED), appStatus(Installed.No, ReleaseLookup.Unavailable(LookupFailure.RATE_LIMITED)))
    }
}
