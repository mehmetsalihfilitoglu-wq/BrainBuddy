package com.brainbuddy.app.gate

import android.content.Context
import android.content.Intent
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ScheduleStore
import com.brainbuddy.app.core.AppGroupPresets
import java.util.Calendar

object GateHelper {

    /**
     * Determines if gate is required now (quiz before using blocked apps).
     * Schedule "always blocked" overrides interval logic.
     */
    fun gateRequiredNow(context: Context): Boolean {
        val prefs = ProtectionPrefs(context)
        if (prefs.userLocked()) return true
        if (!prefs.isProtectionEnabled()) return false

        val blockedStore = BlockedAppsStore(context)
        val blocked = blockedStore.getBlockedPackages()
        if (blocked.isEmpty()) return false

        val scheduleStore = ScheduleStore(context)
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1 // 0=Sun
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val minuteOfDay = hour * 60 + minute

        if (scheduleStore.isAlwaysBlockedNow(
                minuteOfDay, dayOfWeek, "all", blocked, AppGroupPresets
            )
        ) return true

        val lastPassed = prefs.lastQuizPassedAtMs()
        if (lastPassed == 0L) return true
        val intervalMs = prefs.quizIntervalMinutes() * 60 * 1000L
        return System.currentTimeMillis() - lastPassed > intervalMs
    }

    fun openGate(context: Context, blockedPackage: String) {
        val intent = Intent(context, GateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_HISTORY)
            putExtra(GateActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
        }
        context.startActivity(intent)
    }
}
