package com.brainbuddy.app.core

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Daily quota for parent viewing correct answers in wrong-question review.
 * - Premium: unlimited
 * - Non-premium: 3 free per day, then 1 per ad (unlimited ads)
 */
class DailyParentViewQuotaStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val premiumStore = PremiumStore(context)

    fun resetIfNewDay() {
        val todayKey = todayKey()
        val lastKey = prefs.getString(KEY_LAST_DAY, "")
        if (lastKey != todayKey) {
            prefs.edit()
                .putString(KEY_LAST_DAY, todayKey)
                .putInt(KEY_FREE_USED, 0)
                .putInt(KEY_AD_CREDITS, 0)
                .apply()
        }
    }

    fun getRemainingToday(): Int {
        if (premiumStore.isPremium()) return Int.MAX_VALUE
        resetIfNewDay()
        val freeUsed = prefs.getInt(KEY_FREE_USED, 0)
        val adCredits = prefs.getInt(KEY_AD_CREDITS, 0)
        return (FREE_PER_DAY - freeUsed + adCredits).coerceAtLeast(0)
    }

    /** Returns true if can view (consume). */
    fun canView(): Boolean = getRemainingToday() > 0

    /** Consume one view. Returns true if consumed. */
    fun consumeOne(): Boolean {
        if (premiumStore.isPremium()) return true
        resetIfNewDay()
        val freeUsed = prefs.getInt(KEY_FREE_USED, 0)
        val adCredits = prefs.getInt(KEY_AD_CREDITS, 0)
        val remaining = FREE_PER_DAY - freeUsed + adCredits
        if (remaining <= 0) return false
        if (freeUsed < FREE_PER_DAY) {
            prefs.edit().putInt(KEY_FREE_USED, freeUsed + 1).apply()
        } else {
            prefs.edit().putInt(KEY_AD_CREDITS, (adCredits - 1).coerceAtLeast(0)).apply()
        }
        return true
    }

    /** Call after user watched ad. Adds 1 extra view. */
    fun addFromAd(): Boolean {
        if (premiumStore.isPremium()) return true
        resetIfNewDay()
        val adCredits = prefs.getInt(KEY_AD_CREDITS, 0)
        prefs.edit().putInt(KEY_AD_CREDITS, adCredits + 1).apply()
        return true
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val PREFS = "bb_parent_view_quota"
        private const val KEY_LAST_DAY = "last_day"
        private const val KEY_FREE_USED = "free_used"
        private const val KEY_AD_CREDITS = "ad_credits"
        const val FREE_PER_DAY = 3
    }
}
