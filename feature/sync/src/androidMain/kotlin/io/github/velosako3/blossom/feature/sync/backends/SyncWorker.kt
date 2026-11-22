// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.velosako3.blossom.feature.sync.backends

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Hourly background sync on Android while a backend is set up. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        sync?.invoke() // reports its own errors in Settings → Sync; the next run is in an hour either way
        return Result.success()
    }

    companion object {
        private const val WORK = "blossom-sync"
        /** Set by the app graph: cloud and LAN sync (does nothing while neither is set up). */
        @Volatile var sync: (suspend () -> Unit)? = null

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context.applicationContext)
            if (!enabled) { wm.cancelUniqueWork(WORK); return }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
