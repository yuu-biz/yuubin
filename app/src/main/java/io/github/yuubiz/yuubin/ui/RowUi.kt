package io.github.yuubiz.yuubin.ui

import io.github.yuubiz.yuubin.core.AppStatus
import io.github.yuubiz.yuubin.core.InstallFailure
import io.github.yuubiz.yuubin.core.InstallProgress
import io.github.yuubiz.yuubin.core.Installed
import io.github.yuubiz.yuubin.core.LookupFailure
import io.github.yuubiz.yuubin.core.Release
import io.github.yuubiz.yuubin.install.AppRow

enum class RowAction { INSTALL, UPDATE, OPEN, APP_INFO, ALLOW_INSTALLS, DISMISS }

/** The installed and latest versions as display text; `installed == null` means not installed. */
data class VersionLine(val installed: String?, val latest: String?)

sealed interface RowMessage {
    data object UpToDate : RowMessage
    data object SignerMismatch : RowMessage
    data class ReleaseUnavailable(val reason: LookupFailure) : RowMessage
    data object Verifying : RowMessage
    data object AwaitingConfirmation : RowMessage
    data object Installing : RowMessage
    data object Succeeded : RowMessage
    data class Failed(val reason: InstallFailure, val detail: String?) : RowMessage
}

sealed interface RowBusy {
    /** The release or installed state is not known yet. */
    data object Loading : RowBusy

    /** [percent] is null when the total size is unknown. */
    data class Downloading(val percent: Int?) : RowBusy
    data object Working : RowBusy
}

/** Everything one list row shows; the composable adds text and layout only. */
data class RowUi(
    val packageName: String,
    val name: String,
    val versions: VersionLine?,
    val message: RowMessage?,
    val busy: RowBusy?,
    /** The button that moves the app forward; null while a task is running. */
    val primary: RowAction?,
    /** Dismisses or fixes a finished attempt, next to [primary] so a retry stays one tap away. */
    val secondary: RowAction?,
    /** Release notes as Markdown source, only for rows that offer an install or update. */
    val notes: String?,
    val notesExpanded: Boolean = false,
)

/** [canOpen] is asked only for rows that could show Open, so a download's progress ticks do not query PackageManager. */
fun AppRow.toRowUi(canOpen: (packageName: String) -> Boolean): RowUi {
    val packageName = app.packageName
    fun open() = RowAction.OPEN.takeIf { canOpen(packageName) }
    val versions = status?.let(::versionLine)
    val notes = when (status) {
        is AppStatus.NotInstalled -> status.release.notes
        is AppStatus.UpdateAvailable -> status.release.notes
        null, is AppStatus.UpToDate, is AppStatus.SignerMismatch, is AppStatus.ReleaseUnavailable -> null
    }?.takeIf { it.isNotBlank() }
    val statusMessage = status?.let(::statusMessage)
    val statusAction = when (status) {
        null -> null
        is AppStatus.NotInstalled -> RowAction.INSTALL
        is AppStatus.UpdateAvailable -> RowAction.UPDATE
        is AppStatus.UpToDate -> open()
        is AppStatus.SignerMismatch -> RowAction.APP_INFO
        is AppStatus.ReleaseUnavailable -> if (status.installed is Installed.Yes) open() else null
    }

    fun ui(message: RowMessage?, busy: RowBusy?, primary: RowAction?, secondary: RowAction?, showNotes: Boolean) =
        RowUi(packageName, app.displayName, versions, message, busy, primary, secondary, notes.takeIf { showNotes })

    return when (progress) {
        InstallProgress.Idle ->
            ui(statusMessage, RowBusy.Loading.takeIf { status == null }, statusAction, null, showNotes = true)
        is InstallProgress.Downloading -> {
            val percent = progress.total.takeIf { it > 0 }?.let { (progress.bytes * 100 / it).toInt().coerceIn(0, 100) }
            ui(null, RowBusy.Downloading(percent), null, null, showNotes = false)
        }
        InstallProgress.Verifying -> ui(RowMessage.Verifying, RowBusy.Working, null, null, showNotes = false)
        InstallProgress.AwaitingConfirmation -> ui(RowMessage.AwaitingConfirmation, RowBusy.Working, null, null, showNotes = false)
        InstallProgress.Installing -> ui(RowMessage.Installing, RowBusy.Working, null, null, showNotes = false)
        // The status still describes the version before the install until the installed state is re-read, so no primary action.
        InstallProgress.Succeeded -> ui(RowMessage.Succeeded, null, null, RowAction.DISMISS, showNotes = false)
        is InstallProgress.Failed -> {
            val secondary = if (progress.reason == InstallFailure.INSTALL_PERMISSION_MISSING) RowAction.ALLOW_INSTALLS else RowAction.DISMISS
            ui(RowMessage.Failed(progress.reason, progress.detail), null, statusAction, secondary, showNotes = true)
        }
    }
}

private fun displayVersion(release: Release): String = release.tag.removePrefix("v")

private fun versionLine(status: AppStatus): VersionLine = when (status) {
    is AppStatus.NotInstalled -> VersionLine(null, displayVersion(status.release))
    is AppStatus.UpdateAvailable -> VersionLine(status.installed.versionName, displayVersion(status.release))
    is AppStatus.UpToDate -> VersionLine(status.installed.versionName, null)
    is AppStatus.SignerMismatch -> VersionLine(status.installed.versionName, null)
    is AppStatus.ReleaseUnavailable -> VersionLine((status.installed as? Installed.Yes)?.versionName, null)
}

private fun statusMessage(status: AppStatus): RowMessage? = when (status) {
    is AppStatus.NotInstalled, is AppStatus.UpdateAvailable -> null
    is AppStatus.UpToDate -> RowMessage.UpToDate
    is AppStatus.SignerMismatch -> RowMessage.SignerMismatch
    is AppStatus.ReleaseUnavailable -> RowMessage.ReleaseUnavailable(status.reason)
}
