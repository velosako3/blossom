// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.core.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first

/**
 * Keeps blossom running while offline downloads finish with the app in the background. The [DownloadManager] does the
 * work; this only waits for it to go idle, as a data-sync foreground job with a quiet notification.
 */
class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val manager = manager ?: return Result.success()
        runCatching { setForeground(getForegroundInfo()) }
        manager.kick(retryPaused = false)
        manager.busy.first { !it }
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, notificationTitle, NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setContentTitle(notificationTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(ID, n)
    }

    companion object {
        private const val CHANNEL = "offline"
        private const val ID = 4201
        private const val WORK = "blossom-offline-downloads"

        /** Set by the app graph. */
        @Volatile var manager: DownloadManager? = null
        /** Localized by the app ("Saving songs for offline"). */
        @Volatile var notificationTitle: String = "Saving songs for offline"

        /** Hooks [manager] up so the worker starts whenever downloads begin. */
        fun install(context: Context, manager: DownloadManager) {
            this.manager = manager
            val app = context.applicationContext
            manager.onWorkStarted = {
                val wifiOnly = manager.settings.value.wifiOnly
                val request = OneTimeWorkRequestBuilder<DownloadWorker>()
                    .setConstraints(Constraints(requiredNetworkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED))
                    .build()
                WorkManager.getInstance(app).enqueueUniqueWork(WORK, ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
