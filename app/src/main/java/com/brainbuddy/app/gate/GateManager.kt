package com.brainbuddy.app.gate

import android.content.Context
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ScheduleStore
import com.brainbuddy.app.core.AppGroupPresets
import java.util.Calendar

/**
 * Single source of truth for gate status.
 * Gate check lives ONLY in Accessibility/blocked-app interceptor flow.
 * - On PASSED: PASSED_UNTIL(now + interval), clear per-package lock for that package
 * - On FAILED (below parent pass threshold): add package to GateLockedStore (per-package only, NO global userLocked)
 * BrainBuddy screens are NEVER gated; gateRequiredNow is only used when user tries a BLOCKED app.
 */
object GateManager {

    sealed class GateStatus {
        object REQUIRED : GateStatus()
        data class PASSED_UNTIL(val timestampMs: Long) : GateStatus()
    }

    /** Status for blocked-app gate check. Does NOT use userLocked (quiz fail is per-package). */
    private fun getStatusForBlockedApp(context: Context): GateStatus {
        val prefs = ProtectionPrefs(context)
        // Permission lock: Parent PIN required (Accessibility disabled) - gate cannot run, handled by LockScreen
        if (prefs.isPermissionLocked()) return GateStatus.PASSED_UNTIL(Long.MAX_VALUE)

        if (!prefs.isProtectionEnabled()) return GateStatus.PASSED_UNTIL(Long.MAX_VALUE)

        val blockedStore = BlockedAppsStore(context)
        if (blockedStore.getBlockedPackages().isEmpty()) return GateStatus.PASSED_UNTIL(Long.MAX_VALUE)

        val scheduleStore = ScheduleStore(context)
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val minuteOfDay = hour * 60 + minute

        if (scheduleStore.isAlwaysBlockedNow(minuteOfDay, dayOfWeek, "all", blockedStore.getBlockedPackages(), AppGroupPresets)) {
            return GateStatus.REQUIRED
        }

        val lastPassed = prefs.lastQuizPassedAtMs()
        if (lastPassed == 0L) return GateStatus.REQUIRED

        val intervalMs = prefs.quizIntervalMinutes() * 60 * 1000L
        val passedUntil = lastPassed + intervalMs
        return GateStatus.PASSED_UNTIL(passedUntil)
    }

    /**
     * True if user must pass gate quiz before using this blocked app.
     * Only used from Accessibility/blocked-app interceptor. NEVER affects BrainBuddy.
     */
    fun gateRequiredNow(context: Context, blockedPackage: String): Boolean {
        // Per-package lock: user failed quiz for this package
        if (GateLockedStore(context).isGateLocked(blockedPackage)) return true

        val now = System.currentTimeMillis()
        return when (val status = getStatusForBlockedApp(context)) {
            is GateStatus.REQUIRED -> true
            is GateStatus.PASSED_UNTIL -> now > status.timestampMs
        }
    }

    /** Call when user PASSES gate quiz. Clears per-package lock, sets PASSED_UNTIL. */
    fun onGatePassed(context: Context, blockedPackage: String) {
        val prefs = ProtectionPrefs(context)
        prefs.setLastQuizPassedAtMs(System.currentTimeMillis())
        prefs.setUserLocked(false)
        GateLockedStore(context).removeGateLocked(blockedPackage)
    }

    /** Call when user FAILS the gate quiz (score below threshold). Per-package only: that blocked app stays gated. NO global lock. */
    fun onGateFailed(context: Context, blockedPackage: String) {
        if (blockedPackage.isNotBlank()) {
            GateLockedStore(context).addGateLocked(blockedPackage)
        }
        // Do NOT set userLocked - BrainBuddy must open normally after fail
    }
}
