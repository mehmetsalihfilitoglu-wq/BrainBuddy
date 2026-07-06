package com.mioacademy.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Runs a background sync. Periodic (a few times a day) plus an on-demand one-shot,
 * e.g. after finishing a quiz or on app start. Requires network so that once the
 * Firestore adapter is live it doesn't spin offline; with the local mirror it's cheap.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return when (SyncManager.syncNow(applicationContext, "worker")) {
            is SyncResult.Error -> Result.retry()
            else -> Result.success()
        }
    }

    companion object {
        private const val PERIODIC = "mioitalia_sync_periodic"
        private const val ONE_SHOT = "mioitalia_sync_oneshot"

        private val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Schedule the recurring background sync (idempotent). */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Trigger a sync as soon as constraints allow (e.g. after a quiz). */
        fun syncOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
