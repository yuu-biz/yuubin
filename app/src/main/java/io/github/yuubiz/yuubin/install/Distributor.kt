package io.github.yuubiz.yuubin.install

import android.content.Intent
import io.github.yuubiz.yuubin.core.ApkAsset
import io.github.yuubiz.yuubin.core.AppStatus
import io.github.yuubiz.yuubin.core.CatalogApp
import io.github.yuubiz.yuubin.core.Download
import io.github.yuubiz.yuubin.core.InstallFailure
import io.github.yuubiz.yuubin.core.InstallProgress
import io.github.yuubiz.yuubin.core.Installed
import io.github.yuubiz.yuubin.core.Release
import io.github.yuubiz.yuubin.core.ReleaseLookup
import io.github.yuubiz.yuubin.core.appStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** One catalog app as the list shows it. [status] is null until both the release and the installed state are known. */
data class AppRow(val app: CatalogApp, val status: AppStatus?, val progress: InstallProgress)

/**
 * A system install dialog waiting to be shown. The foreground activity starts [intent], then calls `confirmationShown`,
 * or `confirmationFailed` when it could not be started.
 */
data class Confirmation(val packageName: String, val sessionId: Int, val intent: Intent)

/** The single owner of catalog state and of every [InstallProgress] transition. */
class Distributor(
    private val catalog: List<CatalogApp>,
    private val latestRelease: suspend (CatalogApp) -> ReleaseLookup,
    private val downloadApk: suspend (asset: ApkAsset, target: File, onProgress: (bytes: Long) -> Unit) -> Download,
    private val packages: AppPackages,
    private val sessions: InstallSessions,
    private val apkDir: File,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private data class Session(
        val id: Int,
        val versionCodeBefore: Long?,
        val confirmation: Intent? = null,
        val shown: Boolean = false,
    )

    private data class Slot(
        val installed: Installed? = null,
        val lookup: ReleaseLookup? = null,
        val progress: InstallProgress = InstallProgress.Idle,
        /** The committed session whose status broadcast this slot still accepts. */
        val session: Session? = null,
    )

    private data class Synced(val before: Slot, val installed: Installed)

    private val slots = MutableStateFlow(catalog.associate { it.packageName to Slot() })
    private val installedLock = Mutex()

    val rows: StateFlow<List<AppRow>> =
        slots.map(::toRows).stateIn(scope, SharingStarted.Eagerly, toRows(slots.value))

    val pendingConfirmation: StateFlow<Confirmation?> =
        slots.map(::firstUnshown).stateIn(scope, SharingStarted.Eagerly, null)

    // A committed session from an earlier process may still be waiting for the user or installing, so it is kept; its
    // result is not tracked, but reconcile and refresh read the installed version. Yuubin commits right after staging,
    // so an uncommitted one is a leftover. Below API 29 the two cannot be told apart and runInstall cleans up instead.
    private val startup = scope.launch(io) {
        sessions.mine().filter { it.committed == false }.forEach { sessions.abandon(it.id) }
        apkDir.deleteRecursively()
    }

    /** Looks up every release concurrently and re-reads the installed state. */
    suspend fun refresh() = coroutineScope {
        catalog.forEach { app ->
            launch { syncInstalled(app) }
            launch {
                val lookup = latestRelease(app)
                modify(app) { copy(lookup = lookup) }
            }
        }
    }

    /** Starts download, verification and the install session. Ignored while [app] already has one in flight. */
    fun install(app: CatalogApp) {
        val release = (slots.value.getValue(app.packageName).lookup as? ReleaseLookup.Found)?.release ?: return
        val permitted = packages.canRequestInstalls()
        val next = if (permitted) {
            InstallProgress.Downloading(0, release.apk.sizeBytes)
        } else {
            InstallProgress.Failed(InstallFailure.INSTALL_PERMISSION_MISSING)
        }
        val before = modify(app) { if (progress.inFlight) this else copy(progress = next, session = null) }
        if (before.progress.inFlight || !permitted) return
        scope.launch(io) { runInstall(app, release) }
    }

    /** Returns a finished row (succeeded or failed) to idle. */
    fun dismiss(app: CatalogApp) {
        modify(app) {
            if (progress is InstallProgress.Failed || progress is InstallProgress.Succeeded) copy(progress = InstallProgress.Idle) else this
        }
    }

    fun confirmationShown(confirmation: Confirmation) {
        modify(confirmation.packageName) {
            if (session?.id == confirmation.sessionId) copy(session = session.copy(shown = true)) else this
        }
    }

    /** The dialog could not be started, so nothing can finish the session: it is abandoned and the row fails. */
    fun confirmationFailed(confirmation: Confirmation, detail: String?) {
        val id = confirmation.sessionId
        val before = modify(confirmation.packageName) {
            if (session?.id == id) copy(progress = InstallProgress.Failed(InstallFailure.OTHER, detail), session = null) else this
        }
        // Detached from the slot first, so the abort broadcast that abandoning may send cannot overwrite the failure.
        if (before.session?.id == id) scope.launch(io) { sessions.abandon(id) }
    }

    /**
     * Call from the activity's `onResume`. Re-reads the installed state and settles installs whose result was lost:
     * a session that is gone ends as Succeeded or Idle according to PackageManager. A confirmation shown before this
     * call means the dialog was left; its row shows Installing for at most [CONFIRMED_INSTALL_TIMEOUT_MS], then Idle.
     * No session is abandoned here, so a late result still lands on its row.
     */
    fun reconcile() {
        // Taken now: a dialog the activity launches after this call must not count as already left.
        val shownBefore = slots.value.mapNotNull { (pkg, slot) ->
            slot.session?.takeIf { it.shown }?.let { pkg to it.id }
        }.toMap()
        scope.launch(io) {
            startup.join()
            catalog.forEach { app ->
                val left = shownBefore[app.packageName]
                val before = settle(app, dialogLeft = left)
                if (left != null && before.progress == InstallProgress.AwaitingConfirmation) {
                    scope.launch(io) {
                        delay(CONFIRMED_INSTALL_TIMEOUT_MS)
                        settle(app, timedOut = left)
                    }
                }
            }
        }
    }

    /**
     * Re-reads [app]'s installed state and session. [dialogLeft] and [timedOut] name the session they apply to.
     * Returns the slot as it was before.
     */
    private suspend fun settle(app: CatalogApp, dialogLeft: Int? = null, timedOut: Int? = null): Slot {
        val checked = slots.value.getValue(app.packageName).session?.id
        // Checked before the installed read, so a session that finishes in between is judged by the version it installed.
        val alive = checked != null && sessions.exists(checked)
        return syncInstalled(app) { installed ->
            when {
                session == null || session.id != checked -> this
                !alive -> copy(progress = settled(installed, session.versionCodeBefore), session = null)
                // PackageInstaller's dialog closes as soon as the user approves, while the session is still installing.
                // Left through Home instead, the dialog stays in recents; either way the result may still come.
                progress == InstallProgress.AwaitingConfirmation && dialogLeft == session.id ->
                    copy(progress = InstallProgress.Installing)
                progress == InstallProgress.Installing && timedOut == session.id ->
                    copy(progress = InstallProgress.Idle)
                else -> this
            }
        }.before
    }

    /** Called by [InstallStatusReceiver]; the process may be new, in which case the session is unknown and ignored. */
    fun onSessionStatus(sessionId: Int, status: SessionStatus) {
        val app = catalog.firstOrNull { slots.value.getValue(it.packageName).session?.id == sessionId } ?: return
        modify(app) {
            if (session?.id != sessionId) return@modify this
            when (status) {
                is SessionStatus.NeedsConfirmation -> copy(
                    progress = InstallProgress.AwaitingConfirmation,
                    session = session.copy(confirmation = status.intent, shown = false),
                )
                SessionStatus.Succeeded -> copy(progress = InstallProgress.Succeeded, session = null)
                // A row whose wait timed out is already idle; a later cancel of its session does not turn it into an error.
                is SessionStatus.Failed -> copy(progress = if (progress == InstallProgress.Idle) progress else status.failure, session = null)
            }
        }
        if (status == SessionStatus.Succeeded) {
            scope.launch(io) { syncInstalled(app) }
        }
    }

    private suspend fun runInstall(app: CatalogApp, release: Release) {
        startup.join()
        val file = File(apkDir, "${app.packageName}.apk")
        var staged: Int? = null
        try {
            // The package's earlier session, from this process or a dead one, would otherwise install alongside this one.
            sessions.mine().filter { it.packageName == app.packageName }.forEach { sessions.abandon(it.id) }
            val installed = syncInstalled(app).installed
            if (installed is Installed.Yes && !installed.signerMatches) return fail(app, InstallFailure.SIGNER_MISMATCH)

            val total = release.apk.sizeBytes
            val download = downloadApk(release.apk, file) { bytes ->
                modify(app) { copy(progress = InstallProgress.Downloading(bytes, total)) }
            }
            if (download is Download.Failed) return fail(app, download.reason, download.detail)

            modify(app) { copy(progress = InstallProgress.Verifying) }
            val versionCodeBefore = (installed as? Installed.Yes)?.versionCode
            packages.verify(file, app.packageName, app.signer, versionCodeBefore)?.let { return fail(app, it) }

            val id = sessions.stage(file, app.packageName)
            staged = id
            file.delete()
            // Recorded before commit: the status broadcast can arrive before commit() returns.
            modify(app) { copy(progress = InstallProgress.Installing, session = Session(id, versionCodeBefore)) }
            sessions.commit(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            staged?.let { id ->
                sessions.abandon(id)
                modify(app) { if (session?.id == id) copy(session = null) else this }
            }
            fail(app, InstallFailure.OTHER, e.message)
        } finally {
            file.delete()
        }
    }

    private fun fail(app: CatalogApp, reason: InstallFailure, detail: String? = null) {
        modify(app) { copy(progress = InstallProgress.Failed(reason, detail)) }
    }

    private fun settled(installed: Installed, versionCodeBefore: Long?): InstallProgress =
        if (installed is Installed.Yes && (versionCodeBefore == null || installed.versionCode > versionCodeBefore)) {
            InstallProgress.Succeeded
        } else {
            InstallProgress.Idle
        }

    private fun toRows(slots: Map<String, Slot>): List<AppRow> = catalog.map { app ->
        val slot = slots.getValue(app.packageName)
        val status = if (slot.installed != null && slot.lookup != null) appStatus(slot.installed, slot.lookup) else null
        AppRow(app, status, slot.progress)
    }

    private fun firstUnshown(slots: Map<String, Slot>): Confirmation? = catalog.firstNotNullOfOrNull { app ->
        val slot = slots.getValue(app.packageName)
        val session = slot.session
        val intent = session?.confirmation
        if (slot.progress == InstallProgress.AwaitingConfirmation && intent != null && !session.shown) {
            Confirmation(app.packageName, session.id, intent)
        } else {
            null
        }
    }

    /**
     * The only writer of [Slot.installed]. Reads PackageManager and applies [decide] to the slot holding that value.
     * Read and write happen under one lock, so a read taken before a slower one cannot be written after it.
     */
    private suspend fun syncInstalled(app: CatalogApp, decide: Slot.(Installed) -> Slot = { this }): Synced =
        installedLock.withLock {
            val installed = withContext(io) { packages.installed(app) }
            Synced(before = modify(app) { copy(installed = installed).decide(installed) }, installed = installed)
        }

    /** Applies [change] atomically and returns the slot as it was before. [change] may run more than once. */
    private fun modify(app: CatalogApp, change: Slot.() -> Slot): Slot = modify(app.packageName, change)

    private fun modify(packageName: String, change: Slot.() -> Slot): Slot =
        slots.getAndUpdate { it + (packageName to it.getValue(packageName).change()) }.getValue(packageName)

    private val InstallProgress.inFlight: Boolean
        get() = this is InstallProgress.Downloading || this == InstallProgress.Verifying ||
            this == InstallProgress.AwaitingConfirmation || this == InstallProgress.Installing

    private companion object {
        /** How long an approved-looking install may show Installing without a result before the row goes Idle. */
        const val CONFIRMED_INSTALL_TIMEOUT_MS = 120_000L
    }
}
