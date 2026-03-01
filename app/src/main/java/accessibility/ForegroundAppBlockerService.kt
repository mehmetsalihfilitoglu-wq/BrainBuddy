package com.brainbuddy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.brainbuddy.app.HomeActivity
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.KillSwitchPrefs
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.TamperStore
import com.brainbuddy.app.gate.GateActivity
import com.brainbuddy.app.gate.GateHelper

/**
 * Foreground app blocker: detects when blocked app comes to foreground,
 * launches GateActivity for quiz. Debounced to prevent race conditions.
 * Never calls finishAffinity/exitProcess. Uses robust intent flags.
 */
class ForegroundAppBlockerService : AccessibilityService() {

    companion object {
        private const val TAG = "ForegroundAppBlocker"
        private const val DEBOUNCE_MS = 800L
        private const val DELAY_AFTER_HOME_MS = 150L
    }
    private var lastGateLaunchMs = 0L
    private val handler = Handler(Looper.getMainLooper())

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            val pkg = event.packageName?.toString()?.trim() ?: return
            if (pkg.isEmpty() || pkg == packageName) return

            val blockedStore = BlockedAppsStore(this)
            if (!blockedStore.isBlocked(pkg)) return

            val prefs = ProtectionPrefs(this)
            if (!prefs.isProtectionEnabled()) return
            if (KillSwitchPrefs(this).isKillSwitchActive()) return  // Gate disabled by kill switch
            if (!GateHelper.gateRequiredNow(this)) return

            val now = System.currentTimeMillis()
            if (now - lastGateLaunchMs < DEBOUNCE_MS) return
            lastGateLaunchMs = now

            TamperStore(this).logEvent(TamperStore.TamperType.BYPASS_ATTEMPT)
            try {
                com.brainbuddy.app.core.ReportStore(this).recordBlockedAppAttempt(pkg)
            } catch (_: Exception) { /* best-effort */ }

            try {
                performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            } catch (_: Exception) { /* best-effort */ }

            handler.postDelayed({
                launchGate(pkg)
            }, DELAY_AFTER_HOME_MS)
        } catch (e: Exception) {
            Log.e(TAG, "onAccessibilityEvent error", e)
        }
    }

    private fun launchGate(blockedPackage: String) {
        try {
            val intent = Intent(this, GateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                putExtra(GateActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch GateActivity", e)
            try {
                val fallback = Intent(this, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("open_gate", true)
                    putExtra(GateActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
                }
                startActivity(fallback)
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback to HomeActivity also failed", e2)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
