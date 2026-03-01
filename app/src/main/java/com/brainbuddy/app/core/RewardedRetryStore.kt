package com.brainbuddy.app.core

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class RewardedRetryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val MAX_RETRIES_PER_DAY = 1

    fun getRetriesUsedToday(profileId: String): Int {
        val dayKey = "retries_day_$profileId"
        val lastDay = prefs.getLong("${dayKey}_ts", 0L)
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        if (lastDay != today) return 0
        return prefs.getInt(dayKey, 0)
    }

    fun getRemainingRetriesToday(profileId: String): Int =
        (MAX_RETRIES_PER_DAY - getRetriesUsedToday(profileId)).coerceAtLeast(0)

    fun hasRetriedQuestion(quizId: String, questionId: String): Boolean {
        val key = "retry_${quizId}_${questionId}"
        return prefs.getBoolean(key, false)
    }

    fun canRetryWithAd(profileId: String, quizId: String, questionId: String): Boolean {
        if (getRetriesUsedToday(profileId) >= MAX_RETRIES_PER_DAY) return false
        if (hasRetriedQuestion(quizId, questionId)) return false
        return true
    }

    fun recordRetryUsed(profileId: String, quizId: String, questionId: String) {
        val dayKey = "retries_day_$profileId"
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        prefs.edit().apply {
            putLong("${dayKey}_ts", today)
            putInt(dayKey, getRetriesUsedToday(profileId) + 1)
            putBoolean("retry_${quizId}_${questionId}", true)
            apply()
        }
    }

    companion object {
        private const val PREFS = "bb_rewarded_retry"
    }
}
