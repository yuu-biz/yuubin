package io.github.yuubiz.yuubin.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.yuubiz.yuubin.YuubinApp
import java.util.concurrent.TimeUnit

/**
 * The daily check: the same refresh as the list, then a notification for new updates. It never downloads or installs,
 * since replacing an app kills it, and the app in use may be the music player mid-playback.
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as YuubinApp).container
        container.updateNotifier.notifyNew(container.distributor.refresh())
        return Result.success()
    }

    companion object {
        /** Safe on every start: KEEP leaves an already scheduled check and its timing alone. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
