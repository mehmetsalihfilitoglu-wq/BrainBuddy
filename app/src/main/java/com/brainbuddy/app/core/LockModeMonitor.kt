package com.brainbuddy.app.core

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.brainbuddy.app.ui.PinLockActivity

/**
 * Central authority for accessibility-off lock mode.
 * - Check if ParentLockActivity (PinLockActivity verify) should be shown
 * - Launch it when needed (app launch, resume, boot, update)
 */
object LockModeMonitor {

    /**
     * Check if lock mode applies: protection was on, accessibility is off.
     * Updates LockModeDataStore and ProtectionPrefs when lock needed.
     * @return true if user must be shown ParentLockActivity
     */
    fun checkAndSetLockIfNeeded(context: Context): Boolean {
        return try {
            val prefs = ProtectionPrefs(context)
            if (!prefs.isProtectionEnabledRaw()) return false

            val enabled = com.brainbuddy.app.accessibility.AccessibilityUtils.isServiceEnabled(
                context, com.brainbuddy.app.accessibility.ForegroundAppBlockerService::class.java
            )
            val lockStore = LockModeDataStore(context)
            lockStore.setLastKnownServiceEnabledSync(enabled)

            if (!enabled) {
                lockStore.setLockModeEnabledSync(true)
                prefs.setUserLocked(true)
                prefs.setPermissionDisabledLockReason("accessibility_disabled")
                try { TamperStore(context).logEvent(TamperStore.TamperType.SERVICE_DISABLED) } catch (_: Exception) { }
                try { ProtectionNotificationHelper.showProtectionOffNotification(context) } catch (_: Exception) { }
                true
            } else {
                lockStore.setLockModeEnabledSync(false)
                false
            }
        } catch (_: Exception) { false }
    }

    /** True if lock mode is active (DataStore says lockModeEnabled). */
    fun isLockModeActive(context: Context): Boolean {
        return try {
            LockModeDataStore(context).getLockModeEnabled()
        } catch (_: Exception) { false }
    }

    /** Launch ParentLockActivity (PinLockActivity verify mode). Call when lock required. */
    fun launchParentLockActivity(activity: Activity) {
        val intent = Intent(activity, PinLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(PinLockActivity.EXTRA_MODE, "verify")
            putExtra(PinLockActivity.EXTRA_TARGET, "PermissionsChecklistActivity")
        }
        activity.startActivity(intent)
        activity.finishAffinity()
    }

    /** Launch from non-Activity context (e.g. Service, Receiver). */
    fun launchParentLockActivityFromContext(context: Context) {
        val intent = Intent(context, PinLockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            putExtra(PinLockActivity.EXTRA_MODE, "verify")
            putExtra(PinLockActivity.EXTRA_TARGET, "PermissionsChecklistActivity")
        }
        context.startActivity(intent)
    }

    /** Called when user successfully enters PIN - clear lock mode. */
    fun onParentPinVerified(context: Context) {
        LockModeDataStore(context).clearLockModeSync()
        ProtectionPrefs(context).apply {
            setPermissionDisabledLockReason("")
            setUserLocked(false)
        }
        ProtectionNotificationHelper.cancelProtectionOffNotification(context)
    }
}
