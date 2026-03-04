package com.brainbuddy.app.resilience

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters

/**
 * Şimdilik no-op.
 * İleride: protection ayarlarını / JSON cache’i / blok listelerini burada yeniden yükleyebilirsin.
 */
class ReloadSettingsWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        return try {
            val ctx = applicationContext
            val prefs = com.brainbuddy.app.core.ProtectionPrefs(ctx)
            val enabled = com.brainbuddy.app.accessibility.AccessibilityUtils.isServiceEnabled(
                ctx, com.brainbuddy.app.accessibility.ForegroundAppBlockerService::class.java
            )
            if (!enabled && prefs.isProtectionEnabledRaw()) {
                val lockStore = com.brainbuddy.app.core.LockModeDataStore(ctx)
                lockStore.setLockModeEnabled(true)
                lockStore.setLastKnownServiceEnabled(false)
                prefs.setUserLocked(true)
                prefs.setPermissionDisabledLockReason("accessibility_disabled")
                try {
                    com.brainbuddy.app.core.TamperStore(ctx).logEvent(
                        com.brainbuddy.app.core.TamperStore.TamperType.SERVICE_DISABLED
                    )
                } catch (_: Exception) { /* best-effort */ }
                try {
                    com.brainbuddy.app.core.ProtectionNotificationHelper.showProtectionOffNotification(ctx)
                } catch (_: Exception) { }
            } else if (enabled) {
                com.brainbuddy.app.core.LockModeDataStore(ctx).setLastKnownServiceEnabled(true)
            }
            // Clear stuck quiz-in-progress to avoid gate deadlock after reboot/update
            prefs.setQuizInProgress(false)
            ListenableWorker.Result.success()
        } catch (e: Exception) {
            android.util.Log.e("ReloadSettingsWorker", "doWork error", e)
            ListenableWorker.Result.retry()
        }
    }
}