package io.github.yuubiz.yuubin.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import io.github.yuubiz.yuubin.YuubinApp
import io.github.yuubiz.yuubin.core.InstallFailure
import io.github.yuubiz.yuubin.core.InstallProgress

sealed interface SessionStatus {
    /** Launch [intent] from the foreground activity; a receiver must not start it. */
    data class NeedsConfirmation(val intent: Intent) : SessionStatus
    data object Succeeded : SessionStatus
    data class Failed(val failure: InstallProgress.Failed) : SessionStatus
}

/** Receives the status of sessions committed by [SessionInstaller]. Not exported. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val status = when (val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION ->
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                    ?.let { SessionStatus.NeedsConfirmation(it) }
                    ?: SessionStatus.Failed(InstallProgress.Failed(InstallFailure.OTHER, "no confirmation intent"))
            PackageInstaller.STATUS_SUCCESS -> SessionStatus.Succeeded
            else -> SessionStatus.Failed(InstallProgress.Failed(failureOf(code), message))
        }
        (context.applicationContext as YuubinApp).container.distributor.onSessionStatus(sessionId, status)
    }

    private fun failureOf(status: Int): InstallFailure = when (status) {
        PackageInstaller.STATUS_FAILURE_ABORTED -> InstallFailure.CANCELLED
        PackageInstaller.STATUS_FAILURE_CONFLICT -> InstallFailure.CONFLICT
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> InstallFailure.INCOMPATIBLE
        PackageInstaller.STATUS_FAILURE_STORAGE -> InstallFailure.STORAGE
        PackageInstaller.STATUS_FAILURE_BLOCKED -> InstallFailure.BLOCKED
        else -> InstallFailure.OTHER
    }
}
