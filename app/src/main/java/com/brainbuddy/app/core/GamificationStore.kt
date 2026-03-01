package com.brainbuddy.app.core

import android.content.Context
import org.json.JSONArray
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
            prefs.edit().putInt(KEY_XP, prefs.getInt("points", 0).coerceIn(0, Int.MAX_VALUE)).apply()
        }
        return prefs.getInt(KEY_XP, 0).coerceIn(0, 2_000_000_000)
    }
    fun addXp(delta: Int) {
        val safeDelta = delta.coerceIn(Int.MIN_VALUE, Int.MAX_VALUE)
        val newXp = (xp().toLong() + safeDelta).coerceIn(0L, 2_000_000_000L).toInt()
        prefs.edit().putInt(KEY_XP, newXp).apply()
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
    fun lastActiveDate(): Long = prefs.getLong(KEY_LAST_ACTIVE_DATE, 0L)

    fun freezeTokens(): Int = prefs.getInt(KEY_FREEZE_TOKENS, 0)
    fun addFreezeTokens(delta: Int) {
        prefs.edit().putInt(KEY_FREEZE_TOKENS, (freezeTokens() + delta).coerceAtLeast(0)).apply()
    }
    fun useFreezeToken(): Boolean {
        if (freezeTokens() <= 0) return false
        prefs.edit().putInt(KEY_FREEZE_TOKENS, freezeTokens() - 1).apply()
        return true
    }

    fun milestoneBadges(): List<String> {
        val arr = prefs.getString(KEY_MILESTONE_BADGES, "[]") ?: "[]"
        val json = try { org.json.JSONArray(arr) } catch (_: Exception) { org.json.JSONArray() }
        return (0 until json.length()).mapNotNull { json.optString(it).takeIf { s -> s.isNotEmpty() } }
    }

    private fun addMilestoneBadge(badge: String) {
        val list = milestoneBadges().toMutableList()
        if (badge !in list) list.add(badge)
        prefs.edit().putString(KEY_MILESTONE_BADGES, JSONArray(list).toString()).apply()
    }

    fun recordQuizCompletion(nowMs: Long) {
        val lastMs = prefs.getLong(KEY_LAST_COMPLETED_MS, 0L)
        val today = dayIndex(nowMs)
        val lastDay = dayIndex(lastMs)
        val gap = today - lastDay
        val newStreak = when {
            lastMs == 0L -> 1
            lastDay == today -> streakDays()
            lastDay == today - 1 -> streakDays() + 1
            gap == 2L && freezeTokens() > 0 -> {
                useFreezeToken()
                streakDays() + 1
            }
            else -> 1
        }
        prefs.edit()
            .putLong(KEY_LAST_COMPLETED_MS, nowMs)
            .putLong(KEY_LAST_ACTIVE_DATE, today)
            .putInt(KEY_STREAK_DAYS, newStreak)
            .apply()
        unlockBadges()
        unlockMilestones(newStreak)
    }

    private fun unlockMilestones(streak: Int) {
        when {
            streak >= 100 -> addMilestoneBadge(MILESTONE_100)
            streak >= 30 -> addMilestoneBadge(MILESTONE_30)
            streak >= 7 -> addMilestoneBadge(MILESTONE_7)
        }
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
        private const val KEY_LAST_ACTIVE_DATE = "last_active_date"
        private const val KEY_FREEZE_TOKENS = "freeze_tokens"
        private const val KEY_BADGES = "badges"
        private const val KEY_MILESTONE_BADGES = "milestone_badges"
        const val MILESTONE_7 = "7-Day Streak"
        const val MILESTONE_30 = "30-Day Avatar Unlock"
        const val MILESTONE_100 = "Legend Streak"
    }
}