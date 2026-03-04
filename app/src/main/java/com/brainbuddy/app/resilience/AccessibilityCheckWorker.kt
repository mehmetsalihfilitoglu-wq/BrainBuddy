package com.brainbuddy.app.resilience

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * B) Periodic WorkManager fallback: 15 dk'da bir AccessibilityService kontrolü.
 * ReloadSettingsWorker ile aynı lock mantığını uygular.
 */
class AccessibilityCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val ctx = applicationContext
            val prefs = com.brainbuddy.app.core.ProtectionPrefs(ctx)
            if (!prefs.isProtectionEnabledRaw()) return ListenableWorker.Result.success()

            val enabled = com.brainbuddy.app.accessibility.AccessibilityUtils.isServiceEnabled(
                ctx, com.brainbuddy.app.accessibility.ForegroundAppBlockerService::class.java
            )
            val lockStore = com.brainbuddy.app.core.LockModeDataStore(ctx)
            lockStore.setLastKnownServiceEnabled(enabled)

            if (!enabled) {
                lockStore.setLockModeEnabled(true)
                prefs.setUserLocked(true)
                prefs.setPermissionDisabledLockReason("accessibility_disabled")
                try { com.brainbuddy.app.core.TamperStore(ctx).logEvent(com.brainbuddy.app.core.TamperStore.TamperType.SERVICE_DISABLED) } catch (_: Exception) { }
                try { com.brainbuddy.app.core.ProtectionNotificationHelper.showProtectionOffNotification(ctx) } catch (_: Exception) { }
            }
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AccessibilityCheckWorker", "doWork error", e)
            ListenableWorker.Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "brainbuddy_accessibility_check"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<AccessibilityCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
