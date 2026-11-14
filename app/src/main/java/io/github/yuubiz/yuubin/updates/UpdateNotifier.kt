package io.github.yuubiz.yuubin.updates

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.text.ListFormatter
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import io.github.yuubiz.yuubin.MainActivity
import io.github.yuubiz.yuubin.R
import io.github.yuubiz.yuubin.core.UpdateNotice
import io.github.yuubiz.yuubin.core.updateNotice
import io.github.yuubiz.yuubin.install.AppRow

/** Posts the "updates available" notification and remembers, per package, the release tag it last announced. */
class UpdateNotifier(private val context: Context) {
    private val record = context.getSharedPreferences("update_notices", Context.MODE_PRIVATE)
    private val manager = NotificationManagerCompat.from(context)

    fun notifyNew(rows: List<AppRow>) {
        val notified = record.all.mapNotNull { (pkg, tag) -> (tag as? String)?.let { pkg to it } }.toMap()
        val notice = updateNotice(rows.map { it.app to it.status }, notified) ?: return
        // Nothing is recorded while notifications are off, so updates found meanwhile are announced once they are allowed.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.update_channel_name))
                .build(),
        )
        // A channel the user switched off shows nothing, so it must not count as announced either.
        if (manager.getNotificationChannelCompat(CHANNEL_ID)?.importance == NotificationManagerCompat.IMPORTANCE_NONE) return
        manager.notify(NOTIFICATION_ID, notification(notice))
        record.edit { notice.notified.forEach { (pkg, tag) -> putString(pkg, tag) } }
    }

    private fun notification(notice: UpdateNotice) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.resources.getQuantityString(R.plurals.update_notification_title, notice.apps.size))
            .setContentText(ListFormatter.getInstance().format(notice.apps.map { it.displayName }))
            .setContentIntent(openList())
            .setAutoCancel(true)
            .build()

    // The launcher's own intent, so a running task comes to the front instead of gaining a second list.
    private fun openList(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val CHANNEL_ID = "updates"
        const val NOTIFICATION_ID = 1
    }
}
