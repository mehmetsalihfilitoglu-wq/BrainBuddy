package com.brainbuddy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.TamperStore
import com.brainbuddy.app.gate.GateActivity
import com.brainbuddy.app.gate.GateHelper

/**
 * Foreground app blocker: detects when blocked app comes to foreground,
 * launches GateActivity for quiz.
 */
class ForegroundAppBlockerService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val blockedStore = BlockedAppsStore(this)
        if (!blockedStore.isBlocked(pkg)) return

        val prefs = ProtectionPrefs(this)
        if (!prefs.isProtectionEnabled()) return
        if (prefs.userLocked()) {
            // Show LockScreen instead - handled by MainActivity routing
            return
        }

        if (GateHelper.gateRequiredNow(this)) {
            TamperStore(this).logEvent(TamperStore.TamperType.BYPASS_ATTEMPT)
            com.brainbuddy.app.core.ReportStore(this).recordBlockedAppAttempt(pkg)
            val intent = Intent(this, GateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                putExtra(GateActivity.EXTRA_BLOCKED_PACKAGE, pkg)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        val prefs = ProtectionPrefs(this)
        if (prefs.isPermissionLocked()) {
            prefs.setPermissionDisabledLockReason("")
            prefs.setUserLocked(false)
        }
    }

    override fun onDestroy() {
        // Service disabled - PermissionMonitor will set lock
        super.onDestroy()
    }
}
