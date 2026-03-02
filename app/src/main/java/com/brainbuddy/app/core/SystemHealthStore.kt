package com.brainbuddy.app.core

import android.content.Context

/** Persists last system health check results for badge/status display. */
class SystemHealthStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun saveResults(
        accessibilityOk: Boolean,
        usageStatsOk: Boolean,
        batteryOk: Boolean,
        gateTestOk: Boolean,
        blockListOk: Boolean,
        lastCheckedMs: Long
    ) {
        prefs.edit()
            .putBoolean(KEY_ACCESSIBILITY, accessibilityOk)
            .putBoolean(KEY_USAGE_STATS, usageStatsOk)
            .putBoolean(KEY_BATTERY, batteryOk)
            .putBoolean(KEY_GATE_TEST, gateTestOk)
            .putBoolean(KEY_BLOCK_LIST, blockListOk)
            .putLong(KEY_LAST_CHECKED, lastCheckedMs)
            .apply()
    }

    /** Block list is optional; others are required for full health. */
    fun isAllOk(): Boolean =
        prefs.getBoolean(KEY_ACCESSIBILITY, false) &&
            prefs.getBoolean(KEY_USAGE_STATS, false) &&
            prefs.getBoolean(KEY_BATTERY, false) &&
            prefs.getBoolean(KEY_GATE_TEST, false)

    fun isBlockListOk(): Boolean = prefs.getBoolean(KEY_BLOCK_LIST, false)

    fun getLastCheckedMs(): Long = prefs.getLong(KEY_LAST_CHECKED, 0L)

    companion object {
        private const val PREFS = "bb_system_health"
        private const val KEY_ACCESSIBILITY = "accessibility_ok"
        private const val KEY_USAGE_STATS = "usage_stats_ok"
        private const val KEY_BATTERY = "battery_ok"
        private const val KEY_GATE_TEST = "gate_test_ok"
        private const val KEY_BLOCK_LIST = "block_list_ok"
        private const val KEY_LAST_CHECKED = "last_checked_ms"
    }
}
