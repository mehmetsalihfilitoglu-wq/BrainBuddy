package com.edumio.app.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Context.wrongReviewGateDataStore by preferencesDataStore(name = "wrong_review_gate_v2")

private val KEY_USED_TODAY = intPreferencesKey("used_today")
private val KEY_LAST_RESET_DATE = stringPreferencesKey("last_reset_date")

/**
 * Centralized access controller for wrong-answer review.
 *
 * Single source of truth for:
 * - Premium bypass
 * - Daily quota (3/day for free users)
 * - Daily reset logic
 * - Quota consumption (atomic, no double-spend)
 *
 * NO UI code should directly mutate quota. All access goes through this manager.
 */
class WrongReviewAccessManager(private val context: Context) {

    private val dataStore = context.wrongReviewGateDataStore
    private val premiumStore = PremiumStore(context)

    // ── Premium check ────────────────────────────────────────────

    fun isPremium(): Boolean = premiumStore.isPremium()

    // ── Daily reset ──────────────────────────────────────────────

    fun handleDailyReset() {
        runBlocking {
            dataStore.edit { prefs ->
                val today = todayKey()
                val last = prefs[KEY_LAST_RESET_DATE] ?: ""
                if (last != today) {
                    prefs[KEY_LAST_RESET_DATE] = today
                    prefs[KEY_USED_TODAY] = 0
                }
            }
        }
    }

    // ── Quota queries ────────────────────────────────────────────

    /** How many reviews used today. Always 0 for premium. */
    fun getUsedToday(): Int {
        if (premiumStore.isPremium()) return 0
        handleDailyReset()
        return runBlocking {
            dataStore.data.map { prefs ->
                (prefs[KEY_USED_TODAY] ?: 0).coerceAtLeast(0)
            }.first()
        }
    }

    /** Remaining daily reviews. Int.MAX_VALUE for premium. */
    fun getRemainingDaily(): Int {
        if (premiumStore.isPremium()) return Int.MAX_VALUE
        return (FREE_PER_DAY - getUsedToday()).coerceAtLeast(0)
    }

    /** True if the user can unlock one more question today. */
    fun canUnlock(): Boolean {
        if (premiumStore.isPremium()) return true
        return getRemainingDaily() > 0
    }

    // ── Quota mutation ───────────────────────────────────────────

    /**
     * Atomically consume one daily review slot.
     * Returns true if consumed. Returns false if limit reached.
     * Premium users always return true (no consumption).
     */
    fun consumeUnlock(): Boolean {
        if (premiumStore.isPremium()) return true
        handleDailyReset()
        var consumed = false
        runBlocking {
            dataStore.edit { prefs ->
                val used = (prefs[KEY_USED_TODAY] ?: 0).coerceAtLeast(0)
                if (used < FREE_PER_DAY) {
                    prefs[KEY_USED_TODAY] = used + 1
                    consumed = true
                }
            }
        }
        return consumed
    }

    // ── Internals ────────────────────────────────────────────────

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        const val FREE_PER_DAY = 3
    }
}
