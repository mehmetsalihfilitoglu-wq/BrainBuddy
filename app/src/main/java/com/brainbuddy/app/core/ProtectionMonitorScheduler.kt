package com.brainbuddy.app.core

import android.content.Context
import com.brainbuddy.app.resilience.AccessibilityCheckWorker
import com.brainbuddy.app.resilience.AccessibilityMonitorService

/**
 * Starts/stops AccessibilityMonitorService and AccessibilityCheckWorker
 * when protection is enabled/disabled.
 */
object ProtectionMonitorScheduler {

    fun onProtectionChanged(context: Context, enabled: Boolean) {
        if (enabled) {
            AccessibilityMonitorService.start(context)
            AccessibilityCheckWorker.schedulePeriodic(context)
        } else {
            AccessibilityMonitorService.stop(context)
        }
    }
}
