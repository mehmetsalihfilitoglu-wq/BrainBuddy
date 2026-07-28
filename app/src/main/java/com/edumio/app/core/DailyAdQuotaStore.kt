package com.edumio.app.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily ad quota for "Last Tests" (replay old test) flow only.
 * Max 3 ads per day. Independent from QuizRetryPolicy.
 */
class DailyAdQuotaStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val premiumStore = PremiumStore(context)

    fun resetIfNewDay() {
        val todayKey = todayKey()
        val lastKey = prefs.getString(KEY_LAST_DAY, "")
        if (lastKey != todayKey) {
            prefs.edit()
                .putString(KEY_LAST_DAY, todayKey)
                .putInt(KEY_COUNT, 0)
                .apply()
        }
    }

    fun getRemainingToday(): Int {
        if (premiumStore.isPremium()) return Int.MAX_VALUE
        resetIfNewDay()
        val used = prefs.getInt(KEY_COUNT, 0)
        return (MAX_PER_DAY - used).coerceAtLeast(0)
    }

    /** Call ONLY after reward received. Returns true if consumed. */
    fun consumeOne(): Boolean {
        if (premiumStore.isPremium()) return true
        resetIfNewDay()
        val used = prefs.getInt(KEY_COUNT, 0)
        if (used >= MAX_PER_DAY) return false
        prefs.edit().putInt(KEY_COUNT, used + 1).apply()
        return true
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val PREFS = "edu_daily_ad_quota"
        private const val KEY_LAST_DAY = "last_day_key"
        private const val KEY_COUNT = "count"
        const val MAX_PER_DAY = 3
    }
}
