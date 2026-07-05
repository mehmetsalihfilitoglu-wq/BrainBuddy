package com.mioacademy.app.core

import android.content.Context
import java.util.concurrent.TimeUnit

class RewardedRetryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val premiumStore = PremiumStore(context)

    private val MAX_RETRIES_PER_DAY = 1

    fun getRetriesUsedToday(profileId: String): Int {
        if (premiumStore.isPremium()) return 0
        val dayKey = "retries_day_$profileId"
        val lastDay = prefs.getLong("${dayKey}_ts", 0L).coerceAtLeast(0L)
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()).coerceAtLeast(0L)
        if (lastDay != today) return 0
        return prefs.getInt(dayKey, 0).coerceIn(0, MAX_RETRIES_PER_DAY)
    }

    fun getRemainingRetriesToday(profileId: String): Int =
        (MAX_RETRIES_PER_DAY - getRetriesUsedToday(profileId)).coerceAtLeast(0)

    fun hasRetriedQuestion(quizId: String, questionId: String): Boolean {
        val key = "retry_${quizId}_${questionId}"
        return prefs.getBoolean(key, false)
    }

    fun canRetryWithAd(profileId: String, quizId: String, questionId: String): Boolean {
        if (premiumStore.isPremium()) return true
        if (getRetriesUsedToday(profileId) >= MAX_RETRIES_PER_DAY) return false
        if (hasRetriedQuestion(quizId, questionId)) return false
        return true
    }

    fun recordRetryUsed(profileId: String, quizId: String, questionId: String) {
        if (quizId.isBlank() || questionId.isBlank()) return
        prefs.edit().putBoolean("retry_${quizId}_${questionId}", true).apply()
        val historyKey = "retry_history"
        val existing = prefs.getStringSet(historyKey, emptySet()) ?: emptySet()
        val entry = "$profileId|$quizId|$questionId|${System.currentTimeMillis()}"
        prefs.edit().putStringSet(historyKey, existing + entry).apply()
        if (premiumStore.isPremium()) return
        val dayKey = "retries_day_$profileId"
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val nextCount = (getRetriesUsedToday(profileId) + 1).coerceIn(0, MAX_RETRIES_PER_DAY)
        prefs.edit()
            .putLong("${dayKey}_ts", today)
            .putInt(dayKey, nextCount)
            .apply()
    }

    /** Parent review: get retry history entries (profileId, quizId, questionId, timestampMs). */
    fun getRetryHistory(): List<RetryHistoryEntry> {
        val historyKey = "retry_history"
        val set = prefs.getStringSet(historyKey, emptySet()) ?: return emptyList()
        return set.mapNotNull { s ->
            val parts = s.split("|")
            if (parts.size >= 4) {
                val ts = parts[3].toLongOrNull() ?: return@mapNotNull null
                RetryHistoryEntry(parts[0], parts[1], parts[2], ts)
            } else null
        }.sortedByDescending { it.timestampMs }.take(100)
    }

    data class RetryHistoryEntry(val profileId: String, val quizId: String, val questionId: String, val timestampMs: Long)

    companion object {
        private const val PREFS = "bb_rewarded_retry"
    }
}
