package com.brainbuddy.app.core

import android.content.Context
import android.content.Intent
import com.brainbuddy.app.LockScreenActivity
import com.brainbuddy.app.accessibility.ForegroundAppBlockerService

/**
 * Monitors if ForegroundAppBlockerService is enabled.
 * When disabled, sets userLocked + permission reason, shows LockScreen.
 */
object PermissionMonitor {

    fun checkAndLockIfDisabled(context: Context): Boolean {
        val enabled = com.brainbuddy.app.accessibility.AccessibilityUtils.isServiceEnabled(
            context, ForegroundAppBlockerService::class.java
        )
        if (!enabled) {
            val prefs = ProtectionPrefs(context)
            prefs.setUserLocked(true)
            prefs.setPermissionDisabledLockReason("accessibility_disabled")
            TamperStore(context).logEvent(TamperStore.TamperType.SERVICE_DISABLED)
            return true
        }
        return false
    }

    fun isAccessibilityEnabled(context: Context): Boolean =
        com.brainbuddy.app.accessibility.AccessibilityUtils.isServiceEnabled(
            context, ForegroundAppBlockerService::class.java
        )
}
