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
        return try {
            val enabled = com.brainbuddy.app.accessibility.AccessibilityUtils.isServiceEnabled(
                context, ForegroundAppBlockerService::class.java
            )
            if (!enabled) {
                val prefs = ProtectionPrefs(context)
                prefs.setUserLocked(true)
                prefs.setPermissionDisabledLockReason("accessibility_disabled")
                try { TamperStore(context).logEvent(TamperStore.TamperType.SERVICE_DISABLED) } catch (_: Exception) { }
                try { ProtectionNotificationHelper.showProtectionOffNotification(context) } catch (_: Exception) { }
                true
            } else false
        } catch (_: Exception) { false }
    }

    fun cancelProtectionOffNotification(context: Context) {
        try { ProtectionNotificationHelper.cancelProtectionOffNotification(context) } catch (_: Exception) { }
    }

    fun isAccessibilityEnabled(context: Context): Boolean =
        com.brainbuddy.app.accessibility.AccessibilityUtils.isServiceEnabled(
            context, ForegroundAppBlockerService::class.java
        )
}
