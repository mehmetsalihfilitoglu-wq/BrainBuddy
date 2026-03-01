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
        val ctx = applicationContext
        val prefs = com.brainbuddy.app.core.ProtectionPrefs(ctx)
        val blocked = com.brainbuddy.app.core.BlockedAppsStore(ctx)
        if (!com.brainbuddy.app.accessibility.AccessibilityUtils.isServiceEnabled(
                ctx, com.brainbuddy.app.accessibility.ForegroundAppBlockerService::class.java
            ) && prefs.isProtectionEnabled()
        ) {
            prefs.setUserLocked(true)
            prefs.setPermissionDisabledLockReason("accessibility_disabled")
            com.brainbuddy.app.core.TamperStore(ctx).logEvent(
                com.brainbuddy.app.core.TamperStore.TamperType.SERVICE_DISABLED
            )
        }
        return ListenableWorker.Result.success()
    }
}