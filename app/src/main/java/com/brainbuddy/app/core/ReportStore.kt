package com.brainbuddy.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Tracks lock events and blocked app attempts for reports. */
class ReportStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordLockEvent() {
        val arr = JSONArray(prefs.getString(KEY_LOCK_EVENTS, "[]"))
        arr.put(JSONObject().put("tsMs", System.currentTimeMillis()))
        trimAndSave(arr, KEY_LOCK_EVENTS, 500)
    }

    fun recordBlockedAppAttempt(pkg: String) {
        val arr = JSONArray(prefs.getString(KEY_ATTEMPTS, "[]"))
        arr.put(JSONObject().put("pkg", pkg).put("tsMs", System.currentTimeMillis()))
        trimAndSave(arr, KEY_ATTEMPTS, 500)
    }

    fun getLockEventsSince(sinceMs: Long): Int {
        val arr = JSONArray(prefs.getString(KEY_LOCK_EVENTS, "[]"))
        var count = 0
        for (i in 0 until arr.length()) {
            if (arr.getJSONObject(i).optLong("tsMs", 0) >= sinceMs) count++
        }
        return count
    }

    fun getBlockedAttemptsSince(sinceMs: Long): Map<String, Int> {
        val arr = JSONArray(prefs.getString(KEY_ATTEMPTS, "[]"))
        val counts = mutableMapOf<String, Int>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("tsMs", 0) >= sinceMs) {
                val pkg = o.optString("pkg", "")
                counts[pkg] = (counts[pkg] ?: 0) + 1
            }
        }
        return counts
    }

    private fun trimAndSave(arr: JSONArray, key: String, max: Int) {
        val trimmed = JSONArray()
        val start = (arr.length() - max).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString(key, trimmed.toString()).apply()
    }

    companion object {
        private const val PREFS = "bb_reports"
        private const val KEY_LOCK_EVENTS = "lock_events"
        private const val KEY_ATTEMPTS = "blocked_attempts"
    }
}
