package com.brainbuddy.app.resilience

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Legit resilience:
 * - cihaz açılınca / app update olunca settings reload işini tetikler.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val work = OneTimeWorkRequestBuilder<ReloadSettingsWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "brainbuddy_reload_settings",
            ExistingWorkPolicy.REPLACE,
            work
        )
    }
}