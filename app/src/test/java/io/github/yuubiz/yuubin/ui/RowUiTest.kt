package io.github.yuubiz.yuubin.ui

import io.github.yuubiz.yuubin.core.ApkAsset
import io.github.yuubiz.yuubin.core.AppStatus
import io.github.yuubiz.yuubin.core.CatalogApp
import io.github.yuubiz.yuubin.core.GitHubRepo
import io.github.yuubiz.yuubin.core.InstallFailure
import io.github.yuubiz.yuubin.core.InstallProgress
import io.github.yuubiz.yuubin.core.Installed
import io.github.yuubiz.yuubin.core.LookupFailure
import io.github.yuubiz.yuubin.core.Release
import io.github.yuubiz.yuubin.core.Sha256
import io.github.yuubiz.yuubin.core.Version
import io.github.yuubiz.yuubin.install.AppRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowUiTest {
    private val sha = requireNotNull(Sha256.parse("ab".repeat(32)))
    private val app = CatalogApp("com.example.app", "Example", GitHubRepo("o", "r"), Regex(".*\\.apk"), sha)

    private fun release(tag: String = "v1.2.0", notes: String = "- fixes") =
        Release(tag, Version.parse(tag), ApkAsset("a.apk", "https://x/a.apk", 1000, sha), notes, "https://x")

    private fun installed(versionName: String = "1.1.0") = Installed.Yes(versionName, versionCode = 1, signerMatches = true)

    private val notInstalled = AppStatus.NotInstalled(release())
    private val update = AppStatus.UpdateAvailable(installed(), release())
    private val upToDate = AppStatus.UpToDate(installed("1.2.0"), release())
    private val mismatch = AppStatus.SignerMismatch(installed())

    private fun ui(status: AppStatus?, progress: InstallProgress = InstallProgress.Idle, canOpen: Boolean = true) =
        AppRow(app, status, progress).toRowUi { canOpen }

    @Test
    fun `idle rows offer the action their status calls for`() {
        assertEquals(RowAction.INSTALL, ui(notInstalled).primary)
        assertEquals(RowAction.UPDATE, ui(update).primary)
        assertEquals(RowAction.OPEN, ui(upToDate).primary)
        assertEquals(RowAction.APP_INFO, ui(mismatch).primary)
        assertNull(ui(AppStatus.ReleaseUnavailable(Installed.No, LookupFailure.NOT_FOUND)).primary)
        assertEquals(RowAction.OPEN, ui(AppStatus.ReleaseUnavailable(installed(), LookupFailure.NETWORK)).primary)
        assertNull(ui(null).primary)
    }

    @Test
    fun `open is hidden when the app has no launcher activity`() {
        assertNull(ui(upToDate, canOpen = false).primary)
        assertNull(ui(AppStatus.ReleaseUnavailable(installed(), LookupFailure.NETWORK), canOpen = false).primary)
    }

    @Test
    fun `PackageManager is asked only by rows that could show Open`() {
        var asked = 0
        AppRow(app, update, InstallProgress.Downloading(10, 100)).toRowUi { asked++; true }
        AppRow(app, notInstalled, InstallProgress.Idle).toRowUi { asked++; true }
        assertEquals(0, asked)
        AppRow(app, upToDate, InstallProgress.Idle).toRowUi { asked++; true }
        assertEquals(1, asked)
    }

    @Test
    fun `version lines carry installed and latest only when they differ`() {
        assertEquals(VersionLine(null, "1.2.0"), ui(notInstalled).versions)
        assertEquals(VersionLine("1.1.0", "1.2.0"), ui(update).versions)
        assertEquals(VersionLine("1.2.0", null), ui(upToDate).versions)
        assertEquals(VersionLine("1.1.0", null), ui(mismatch).versions)
        assertEquals(VersionLine(null, null), ui(AppStatus.ReleaseUnavailable(Installed.No, LookupFailure.NOT_FOUND)).versions)
        assertNull(ui(null).versions)
    }

    @Test
    fun `idle status messages`() {
        assertNull(ui(notInstalled).message)
        assertNull(ui(update).message)
        assertEquals(RowMessage.UpToDate, ui(upToDate).message)
        assertEquals(RowMessage.SignerMismatch, ui(mismatch).message)
        LookupFailure.entries.forEach {
            assertEquals(RowMessage.ReleaseUnavailable(it), ui(AppStatus.ReleaseUnavailable(Installed.No, it)).message)
        }
    }

    @Test
    fun `a row without status shows only a loading indicator`() {
        val row = ui(null)
        assertEquals(RowBusy.Loading, row.busy)
        assertNull(row.message)
        assertNull(row.primary)
        assertNull(row.secondary)
    }

    @Test
    fun `downloading shows a percentage and no button`() {
        val row = ui(update, InstallProgress.Downloading(250, 1000))
        assertEquals(RowBusy.Downloading(25), row.busy)
        assertNull(row.primary)
        assertNull(row.secondary)
        assertEquals(RowBusy.Downloading(100), ui(update, InstallProgress.Downloading(2000, 1000)).busy)
        assertEquals(RowBusy.Downloading(null), ui(update, InstallProgress.Downloading(5, 0)).busy)
    }

    @Test
    fun `verifying, awaiting confirmation and installing show a status line and no button`() {
        mapOf(
            InstallProgress.Verifying to RowMessage.Verifying,
            InstallProgress.AwaitingConfirmation to RowMessage.AwaitingConfirmation,
            InstallProgress.Installing to RowMessage.Installing,
        ).forEach { (progress, message) ->
            val row = ui(update, progress)
            assertEquals(message, row.message)
            assertEquals(RowBusy.Working, row.busy)
            assertNull(row.primary)
            assertNull(row.secondary)
        }
    }

    @Test
    fun `succeeded offers only dismiss, even while the status is still the old one`() {
        val row = ui(update, InstallProgress.Succeeded)
        assertEquals(RowMessage.Succeeded, row.message)
        assertNull(row.primary)
        assertEquals(RowAction.DISMISS, row.secondary)
    }

    @Test
    fun `every failure is dismissable and keeps the retry button`() {
        InstallFailure.entries.forEach { reason ->
            val row = ui(update, InstallProgress.Failed(reason, "boom"))
            assertEquals(RowMessage.Failed(reason, "boom"), row.message)
            assertEquals(RowAction.UPDATE, row.primary)
            val expected = if (reason == InstallFailure.INSTALL_PERMISSION_MISSING) RowAction.ALLOW_INSTALLS else RowAction.DISMISS
            assertEquals(reason.name, expected, row.secondary)
        }
    }

    @Test
    fun `release notes show for installable rows only and only when not blank`() {
        assertEquals("- fixes", ui(update).notes)
        assertEquals("- fixes", ui(notInstalled).notes)
        assertNull(ui(upToDate).notes)
        assertNull(ui(mismatch).notes)
        assertNull(ui(AppStatus.UpdateAvailable(installed(), release(notes = "  \n"))).notes)
        assertNull(ui(update, InstallProgress.Downloading(1, 2)).notes)
    }
}
