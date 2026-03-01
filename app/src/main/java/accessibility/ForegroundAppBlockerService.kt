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
 * launches GateActivity for quiz. Debounced to prevent race conditions.
 */
class ForegroundAppBlockerService : AccessibilityService() {

    companion object {
        private const val DEBOUNCE_MS = 800L
    }
    private var lastGateLaunchMs = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = event.packageName?.toString()?.trim() ?: return
            if (pkg.isEmpty() || pkg == packageName) return

            val blockedStore = BlockedAppsStore(this)
            if (!blockedStore.isBlocked(pkg)) return

            val prefs = ProtectionPrefs(this)
            if (!prefs.isProtectionEnabled()) return
            // When userLocked (failed quiz), MUST still block: launch Gate so they must pass to use blocked app
            // Do NOT return - gateRequiredNow will be true when userLocked

            if (!GateHelper.gateRequiredNow(this)) return
            // Debounce: prevent multiple Gate launches in quick succession (race condition)
            val now = System.currentTimeMillis()
            if (now - lastGateLaunchMs < DEBOUNCE_MS) return
            lastGateLaunchMs = now

            TamperStore(this).logEvent(TamperStore.TamperType.BYPASS_ATTEMPT)
            try {
                com.brainbuddy.app.core.ReportStore(this).recordBlockedAppAttempt(pkg)
            } catch (_: Exception) { /* best-effort logging */ }
            val intent = Intent(this, GateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(GateActivity.EXTRA_BLOCKED_PACKAGE, pkg)
            }
            startActivity(intent)
        } catch (e: Exception) {
            android.util.Log.e("ForegroundAppBlocker", "onAccessibilityEvent error", e)
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
