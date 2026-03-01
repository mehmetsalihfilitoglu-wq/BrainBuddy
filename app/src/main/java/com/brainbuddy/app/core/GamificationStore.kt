package com.brainbuddy.app.core

import android.content.Context
import java.util.concurrent.TimeUnit
import kotlin.math.floor

class GamificationStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val xpPerLevel = 100
    private val baseXp = 10
    private val perCorrectXp = 2
    private val perWrongPenalty = 1
    private val streakBonusXp = 5

    fun xp(): Int {
        if (!prefs.contains(KEY_XP) && prefs.contains("points")) {
            prefs.edit().putInt(KEY_XP, prefs.getInt("points", 0)).apply()
        }
        return prefs.getInt(KEY_XP, 0)
    }
    fun addXp(delta: Int) {
        prefs.edit().putInt(KEY_XP, (xp() + delta).coerceAtLeast(0)).apply()
    }

    fun points(): Int = xp()
    fun addPoints(delta: Int) = addXp(delta)

    fun level(): Int {
        val x = xp().coerceAtLeast(0)
        return (floor(x / xpPerLevel.toDouble()) + 1).toInt().coerceAtLeast(1)
    }

    fun xpProgress(): Pair<Int, Int> {
        val x = xp()
        val currentLevelXp = (level() - 1) * xpPerLevel
        val xpIntoLevel = x - currentLevelXp
        return Pair(xpIntoLevel, xpPerLevel)
    }

    fun streakDays(): Int = prefs.getInt(KEY_STREAK_DAYS, 0)
    fun lastCompletedMs(): Long = prefs.getLong(KEY_LAST_COMPLETED_MS, 0L)

    fun recordQuizCompletion(nowMs: Long) {
        val lastMs = prefs.getLong(KEY_LAST_COMPLETED_MS, 0L)
        val today = dayIndex(nowMs)
        val newStreak = when {
            lastMs == 0L -> 1
            dayIndex(lastMs) == today -> streakDays()
            dayIndex(lastMs) == today - 1 -> streakDays() + 1
            else -> 1
        }
        prefs.edit()
            .putLong(KEY_LAST_COMPLETED_MS, nowMs)
            .putInt(KEY_STREAK_DAYS, newStreak)
            .apply()
        unlockBadges()
    }

    fun addXpForQuiz(correctCount: Int, wrongCount: Int, isStreakDay: Boolean) {
        var earned = baseXp + (correctCount * perCorrectXp) - (wrongCount * perWrongPenalty)
        earned = earned.coerceAtLeast(0)
        if (isStreakDay) earned += streakBonusXp
        addXp(earned)
    }

    fun badges(): Set<String> = prefs.getStringSet(KEY_BADGES, emptySet()) ?: emptySet()

    private fun unlockBadges() {
        val b = badges().toMutableSet()
        if (xp() >= 100) b.add("100_puan")
        if (xp() >= 500) b.add("500_puan")
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
        private const val KEY_XP = "xp"
        private const val KEY_STREAK_DAYS = "streak_days"
        private const val KEY_LAST_COMPLETED_MS = "last_completed_ms"
        private const val KEY_BADGES = "badges"
    }
}