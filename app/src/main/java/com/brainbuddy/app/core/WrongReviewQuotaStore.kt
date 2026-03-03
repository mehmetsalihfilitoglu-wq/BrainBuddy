package com.brainbuddy.app.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily quota for Wrong Answer Review screen (Yanlış Cevapları İncele).
 * - Premium: unlimited
 * - Non-premium: 3 free per day, then +1 per rewarded ad (unlimited ads)
 */
class WrongReviewQuotaStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val premiumStore = PremiumStore(context)

    /** Call on every entry to WrongAnswers screen. Resets if new local day. */
    fun ensureDailyReset() {
        val todayKey = todayKey()
        val lastKey = prefs.getString(KEY_LAST_RESET_DATE, "")
        if (lastKey != todayKey) {
            prefs.edit()
                .putString(KEY_LAST_RESET_DATE, todayKey)
                .putInt(KEY_REMAINING_VIEWS, FREE_PER_DAY)
                .apply()
        }
    }

    /** Returns remaining views (Int.MAX_VALUE for premium). */
    fun getRemainingViews(): Int {
        if (premiumStore.isPremium()) return Int.MAX_VALUE
        ensureDailyReset()
        return prefs.getInt(KEY_REMAINING_VIEWS, FREE_PER_DAY).coerceAtLeast(0)
    }

    /** Returns true if user can reveal one more item. */
    fun canReveal(): Boolean = getRemainingViews() > 0

    /** Consume one view. Returns true if consumed. Never goes negative. */
    fun consumeOne(): Boolean {
        if (premiumStore.isPremium()) return true
        ensureDailyReset()
        val remaining = prefs.getInt(KEY_REMAINING_VIEWS, FREE_PER_DAY)
        if (remaining <= 0) return false
        prefs.edit().putInt(KEY_REMAINING_VIEWS, (remaining - 1).coerceAtLeast(0)).apply()
        return true
    }

    /** Add +1 from rewarded ad. */
    fun addFromAd(): Boolean {
        if (premiumStore.isPremium()) return true
        ensureDailyReset()
        val current = prefs.getInt(KEY_REMAINING_VIEWS, FREE_PER_DAY)
        prefs.edit().putInt(KEY_REMAINING_VIEWS, current + 1).apply()
        return true
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val PREFS = "bb_wrong_review_quota"
        private const val KEY_LAST_RESET_DATE = "last_reset_date"
        private const val KEY_REMAINING_VIEWS = "remaining_views"
        const val FREE_PER_DAY = 3
    }
}
