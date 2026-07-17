package com.edumio.app.core

import android.content.Context
import java.util.concurrent.TimeUnit

/** Weekly Reward Chest: awards freeze tokens based on weekly XP tier. */
class WeeklyRewardStore(context: Context) {
    private val prefs = ProfileScopedPrefs.weeklyReward(context)
    private val gamification = GamificationStore(context)
    private val analytics = AnalyticsStore(context)

    enum class Tier { NONE, BRONZE, SILVER, GOLD }

    private fun weekIndex(ms: Long): Long = TimeUnit.MILLISECONDS.toDays(ms) / 7

    fun getWeeklyXp(): Int {
        val now = System.currentTimeMillis()
        val thisWeek = weekIndex(now)
        return analytics.getSessions()
            .filter { weekIndex(it.tsMs) == thisWeek }
            .sumOf { it.pointsEarned }
    }

    fun getWeeklyTier(): Tier {
        val xp = getWeeklyXp()
        return when {
            xp >= 150 -> Tier.GOLD
            xp >= 80 -> Tier.SILVER
            xp >= 40 -> Tier.BRONZE
            else -> Tier.NONE
        }
    }

    fun claimWeeklyChest(): Int {
        val tier = getWeeklyTier()
        val currentWeek = weekIndex(System.currentTimeMillis())
        val lastClaimedWeek = prefs.getLong(KEY_LAST_CLAIMED_WEEK, -1L)
        if (currentWeek == lastClaimedWeek) return 0
        val tokens = when (tier) {
            Tier.GOLD -> 2
            Tier.SILVER -> 1
            Tier.BRONZE, Tier.NONE -> 0
        }
        if (tokens > 0) {
            gamification.addFreezeTokens(tokens)
            prefs.edit().putLong(KEY_LAST_CLAIMED_WEEK, currentWeek).apply()
        }
        return tokens
    }

    fun canClaimWeeklyChest(): Boolean {
        val tier = getWeeklyTier()
        if (tier == Tier.NONE || tier == Tier.BRONZE) return false
        val currentWeek = weekIndex(System.currentTimeMillis())
        val lastClaimedWeek = prefs.getLong(KEY_LAST_CLAIMED_WEEK, -1L)
        return currentWeek != lastClaimedWeek
    }

    companion object {
        private const val KEY_LAST_CLAIMED_WEEK = "last_claimed_week"
    }
}
