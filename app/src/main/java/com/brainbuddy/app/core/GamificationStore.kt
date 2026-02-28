package com.brainbuddy.app.core

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class GamificationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun points(): Int = prefs.getInt(KEY_POINTS, 0)
    fun addPoints(delta: Int) {
        prefs.edit().putInt(KEY_POINTS, (points() + delta).coerceAtLeast(0)).apply()
    }

    fun level(): Int {
        val p = points().coerceAtLeast(0)
        return (sqrt(p / 100.0) + 1).toInt().coerceAtLeast(1)
    }

    fun streakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)

    fun recordQuizCompletion(nowMs: Long) {
        val lastMs = prefs.getLong(KEY_LAST_COMPLETED_MS, 0L)
        val lastDay = dayIndex(lastMs)
        val today = dayIndex(nowMs)

        val newStreak = when {
            lastMs == 0L -> 1
            today == lastDay -> streakDays()
            today == lastDay + 1 -> streakDays() + 1
            else -> 1
        }

        prefs.edit()
            .putLong(KEY_LAST_COMPLETED_MS, nowMs)
            .putInt(KEY_STREAK_DAYS, newStreak)
            .apply()

        unlockBadges()
    }

    fun badges(): Set<String> =
        prefs.getStringSet(KEY_BADGES, emptySet()) ?: emptySet()

    private fun unlockBadges() {
        val b = badges().toMutableSet()
        if (points() >= 100) b.add("100_puan")
        if (points() >= 500) b.add("500_puan")
        if (level() >= 5) b.add("seviye_5")
        if (streakDays() >= 3) b.add("seri_3")
        if (streakDays() >= 7) b.add("seri_7")
        prefs.edit().putStringSet(KEY_BADGES, b).apply()
    }

    private fun dayIndex(ms: Long): Long {
        if (ms <= 0L) return -1
        return TimeUnit.MILLISECONDS.toDays(ms)
    }

    companion object {
        private const val PREFS = "bb_gamification"
        private const val KEY_POINTS = "points"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_LAST_COMPLETED_MS = "last_completed_ms"
        private const val KEY_BADGES = "badges"
    }
}