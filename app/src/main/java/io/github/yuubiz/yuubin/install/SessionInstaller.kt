package io.github.yuubiz.yuubin.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File

/** PackageInstaller sessions owned by this app. Blocking; call off the main thread. */
class SessionInstaller(private val context: Context) {
    private val installer: PackageInstaller get() = context.packageManager.packageInstaller

    /** Creates a session holding [apk] and returns its id. [commit] it once the id is recorded for the status broadcast. */
    fun stage(apk: File, packageName: String): Int {
        val size = apk.length()
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(size)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, size).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
            }
        } catch (e: Exception) {
            abandon(id)
            throw e
        }
        return id
    }

    fun commit(sessionId: Int) {
        installer.openSession(sessionId).use { it.commit(statusReceiver(sessionId).intentSender) }
    }

    fun exists(sessionId: Int): Boolean = installer.getSessionInfo(sessionId) != null

    fun abandon(sessionId: Int) {
        try {
            installer.abandonSession(sessionId)
        } catch (_: SecurityException) {
            // The session is already gone.
        }
    }

    fun abandonAll() = installer.mySessions.forEach { abandon(it.sessionId) }

    private fun statusReceiver(sessionId: Int): PendingIntent {
        // The installer writes the status extras into the intent, so it must be mutable; being explicit to our own
        // receiver is what makes a mutable PendingIntent allowed when targeting API 34+.
        val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            sessionId,
            Intent(context, InstallStatusReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
    }
}
