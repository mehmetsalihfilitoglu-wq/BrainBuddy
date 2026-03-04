package com.brainbuddy.app.resilience

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.brainbuddy.app.core.ProtectionPrefs

/**
 * D) Restore gate state on BOOT_COMPLETED and MY_PACKAGE_REPLACED.
 * ReloadSettingsWorker checks accessibility, sets lockModeEnabled if disabled.
 * Starts AccessibilityMonitorService and AccessibilityCheckWorker when protection on.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val prefs = ProtectionPrefs(context)
                prefs.setQuizInProgress(false)
                val work = OneTimeWorkRequestBuilder<ReloadSettingsWorker>().build()
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "brainbuddy_reload_settings",
                    ExistingWorkPolicy.REPLACE,
                    work
                )
                if (prefs.isProtectionEnabledRaw()) {
                    AccessibilityMonitorService.start(context)
                    AccessibilityCheckWorker.schedulePeriodic(context)
                }
            }
        }
    }
}