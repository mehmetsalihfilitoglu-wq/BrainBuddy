package com.mioacademy.app.core

import android.content.Context

/**
 * Sleep mode: start and end time (e.g. 22:00 - 07:00).
 * Stored as hour (0-23) and minute (0-59).
 */
class SleepPrefs(context: Context) {
    private val prefs = ProfileScopedPrefs.timeLimits(context)

    fun sleepStartHour(): Int = prefs.getInt(KEY_SLEEP_START_H, 22).coerceIn(0, 23)
    fun sleepStartMinute(): Int = prefs.getInt(KEY_SLEEP_START_M, 0).coerceIn(0, 59)
    fun sleepEndHour(): Int = prefs.getInt(KEY_SLEEP_END_H, 7).coerceIn(0, 23)
    fun sleepEndMinute(): Int = prefs.getInt(KEY_SLEEP_END_M, 0).coerceIn(0, 59)

    fun setSleepStart(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_SLEEP_START_H, hour.coerceIn(0, 23))
            .putInt(KEY_SLEEP_START_M, minute.coerceIn(0, 59))
            .apply()
    }

    fun setSleepEnd(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_SLEEP_END_H, hour.coerceIn(0, 23))
            .putInt(KEY_SLEEP_END_M, minute.coerceIn(0, 59))
            .apply()
    }

    fun extraMinutesGrantedAtMs(): Long = prefs.getLong(KEY_EXTRA_GRANTED_AT, 0L)
    fun setExtraMinutesGrantedAt(ms: Long) = prefs.edit().putLong(KEY_EXTRA_GRANTED_AT, ms).apply()

    companion object {
        private const val KEY_SLEEP_START_H = "sleep_start_h"
        private const val KEY_SLEEP_START_M = "sleep_start_m"
        private const val KEY_SLEEP_END_H = "sleep_end_h"
        private const val KEY_SLEEP_END_M = "sleep_end_m"
        private const val KEY_EXTRA_GRANTED_AT = "extra_minutes_granted_at"
    }
}
