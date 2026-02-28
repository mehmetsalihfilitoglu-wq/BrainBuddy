package com.brainbuddy.app.core

import android.content.Context

class TimeLimitPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun dailyMinutes(): Int = prefs.getInt(KEY_DAILY_MINUTES, 120).coerceIn(0, 180)

    fun setDailyMinutes(minutes: Int) =
        prefs.edit().putInt(KEY_DAILY_MINUTES, minutes.coerceIn(0, 180)).apply()

    companion object {
        private const val PREFS = "bb_time_limits"
        private const val KEY_DAILY_MINUTES = "daily_minutes"
    }
}
