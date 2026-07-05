package com.mioacademy.app.core

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

private val Context.wrongReviewQuotaDataStore by preferencesDataStore(name = "wrong_review_quota")

private val KEY_REMAINING_REVEALS = intPreferencesKey("remaining_reveals")
private val KEY_LAST_RESET_DATE = stringPreferencesKey("last_reset_date")

/**
 * Daily quota for Wrong Answer Review screen (Yanlış Soruları Gör).
 * - Premium: unlimited (bypass at call site)
 * - Non-premium: 3 free per day, then +1 per rewarded ad
 *
 * Quota is consumed when a wrong question's details are REVEALED (expanded), not when opening the screen.
 */
class WrongReviewQuotaStore(private val context: Context) {

    private val dataStore = context.wrongReviewQuotaDataStore

    /** Reset if today != lastResetDate. Call on screen open and before quota ops. */
    fun ensureDailyReset() {
        runBlocking {
            dataStore.edit { prefs ->
                val today = todayKey()
                val last = prefs[KEY_LAST_RESET_DATE] ?: ""
                if (last != today) {
                    prefs[KEY_LAST_RESET_DATE] = today
                    prefs[KEY_REMAINING_REVEALS] = FREE_PER_DAY
                }
            }
        }
    }

    /** Returns remaining reveals (0..n). Caller should bypass for premium. */
    fun getRemaining(): Int {
        ensureDailyReset()
        return runBlocking {
            dataStore.data.map { prefs ->
                (prefs[KEY_REMAINING_REVEALS] ?: FREE_PER_DAY).coerceAtLeast(0)
            }.first()
        }
    }

    /** Consume one reveal. Returns true if consumed. Never goes negative. */
    fun consumeOne(): Boolean {
        ensureDailyReset()
        var consumed = false
        runBlocking {
            dataStore.edit { prefs ->
                val current = (prefs[KEY_REMAINING_REVEALS] ?: FREE_PER_DAY).coerceAtLeast(0)
                if (current > 0) {
                    prefs[KEY_REMAINING_REVEALS] = current - 1
                    consumed = true
                }
            }
        }
        return consumed
    }

    /** Add +1 from rewarded ad. */
    fun addOneFromReward() {
        ensureDailyReset()
        runBlocking {
            dataStore.edit { prefs ->
                val current = (prefs[KEY_REMAINING_REVEALS] ?: FREE_PER_DAY).coerceAtLeast(0)
                prefs[KEY_REMAINING_REVEALS] = current + 1
            }
        }
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        const val FREE_PER_DAY = 3
    }
}
