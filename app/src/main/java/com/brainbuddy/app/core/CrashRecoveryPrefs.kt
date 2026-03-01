package com.brainbuddy.app.core

import android.content.Context
import org.json.JSONArray

/** Track crashes to auto-disable protection and avoid device bricking. */
object CrashRecoveryPrefs {
    private const val PREFS = "bb_crash_recovery"
    private const val KEY_CRASH_TIMES = "crash_times_json"
    private const val MAX_CRASH_ENTRIES = 20
    private const val CRASH_WINDOW_MS = 5 * 60 * 1000L  // 5 minutes
    private const val CRASH_THRESHOLD = 3
    private const val KEY_PROTECTION_DISABLED_BY_CRASH = "protection_disabled_by_crash"

    fun recordCrash(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_CRASH_TIMES, "[]"))
        arr.put(System.currentTimeMillis())
        val trimmed = JSONArray()
        val start = (arr.length() - MAX_CRASH_ENTRIES).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString(KEY_CRASH_TIMES, trimmed.toString()).apply()
    }

    fun shouldDisableProtectionDueToCrashes(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val arr = JSONArray(prefs.getString(KEY_CRASH_TIMES, "[]"))
        val now = System.currentTimeMillis()
        val windowStart = now - CRASH_WINDOW_MS
        var count = 0
        for (i in 0 until arr.length()) {
            val ts = arr.optLong(i, 0L)
            if (ts >= windowStart) count++
        }
        return count >= CRASH_THRESHOLD
    }

    fun isProtectionDisabledByCrash(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROTECTION_DISABLED_BY_CRASH, false)

    fun setProtectionDisabledByCrash(context: Context, disabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PROTECTION_DISABLED_BY_CRASH, disabled)
            .apply()
    }

    fun clearCrashHistory(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CRASH_TIMES)
            .apply()
    }
}
