package com.brainbuddy.app.gate

import android.content.Context
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ScheduleStore
import com.brainbuddy.app.core.AppGroupPresets
import java.util.Calendar

/**
 * Single source of truth for gate status.
 * gateStatus = REQUIRED | PASSED_UNTIL(timestamp)
 * - On PASSED: PASSED_UNTIL(now + intervalMillis)
 * - On FAILED (wrongCount >= 4): REQUIRED (do NOT set lastPassedAt)
 * AccessibilityService enforces: if blocked app in foreground AND gate required, launch GateActivity.
 */
object GateManager {

    sealed class GateStatus {
        object REQUIRED : GateStatus()
        data class PASSED_UNTIL(val timestampMs: Long) : GateStatus()
    }

    private fun getStatus(context: Context): GateStatus {
        val prefs = ProtectionPrefs(context)
        // FAILED gate always blocks; permission lock (accessibility/usage disabled) requires Parent PIN
        if (prefs.userLocked()) return GateStatus.REQUIRED
        if (prefs.isPermissionLocked()) return GateStatus.REQUIRED

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

    /** True if user must pass gate quiz before using blocked apps. */
    fun gateRequiredNow(context: Context): Boolean {
        val now = System.currentTimeMillis()
        return when (val status = getStatus(context)) {
            is GateStatus.REQUIRED -> true
            is GateStatus.PASSED_UNTIL -> now > status.timestampMs
        }
    }

    /** Call when user PASSES gate quiz. Sets gateStatus = PASSED_UNTIL(now + interval). */
    fun onGatePassed(context: Context) {
        val prefs = ProtectionPrefs(context)
        prefs.setLastQuizPassedAtMs(System.currentTimeMillis())
        prefs.setUserLocked(false)
    }

    /** Call when user FAILS (wrongCount >= 4). Sets gateStatus = REQUIRED. Do NOT set lastPassedAt. */
    fun onGateFailed(context: Context) {
        val prefs = ProtectionPrefs(context)
        prefs.setUserLocked(true)
        // Explicitly do NOT set lastQuizPassedAtMs - keep gate REQUIRED
    }
}
