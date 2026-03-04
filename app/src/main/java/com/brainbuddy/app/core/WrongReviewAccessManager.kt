package com.brainbuddy.app.core

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

private val Context.wrongReviewAccessDataStore by preferencesDataStore(name = "wrong_review_access")

private val KEY_OPEN_COUNT = intPreferencesKey("open_count")
private val KEY_LAST_DATE = stringPreferencesKey("last_date")

/**
 * Tracks daily opens of "Yanlış Cevapları İncele" detail screen.
 * - Free: max 3 opens per day; +1 per rewarded ad (stackable)
 * - Premium: unlimited
 */
class WrongReviewAccessManager(private val context: Context) {

    private val dataStore = context.wrongReviewAccessDataStore
    private val premiumStore = PremiumStore(context)

    /** Reset count if a new day. Call before any quota ops. */
    fun ensureDailyReset() {
        runBlocking {
            dataStore.edit { prefs ->
                val today = todayKey()
                val last = prefs[KEY_LAST_DATE] ?: ""
                if (last != today) {
                    prefs[KEY_LAST_DATE] = today
                    prefs[KEY_OPEN_COUNT] = 0
                }
            }
        }
    }

    /** Returns true if user can open wrong-answers detail (remaining > 0 or premium). */
    fun canOpen(): Boolean {
        if (premiumStore.isPremium()) return true
        ensureDailyReset()
        return runBlocking {
            dataStore.data.map { prefs ->
                val used = prefs[KEY_OPEN_COUNT] ?: 0
                used < MAX_OPENS_PER_DAY
            }.first()
        }
    }

    /** Remaining opens for today (0..n). Premium returns Int.MAX_VALUE. */
    fun getRemaining(): Int {
        if (premiumStore.isPremium()) return Int.MAX_VALUE
        ensureDailyReset()
        return runBlocking {
            dataStore.data.map { prefs ->
                val used = prefs[KEY_OPEN_COUNT] ?: 0
                (MAX_OPENS_PER_DAY - used).coerceAtLeast(0)
            }.first()
        }
    }

    /** Consume one open. Call when user successfully opens the detail screen. Returns true if consumed. */
    fun consumeOpen(): Boolean {
        if (premiumStore.isPremium()) return true
        ensureDailyReset()
        var consumed = false
        runBlocking {
            dataStore.edit { prefs ->
                val used = prefs[KEY_OPEN_COUNT] ?: 0
                if (used < MAX_OPENS_PER_DAY) {
                    prefs[KEY_OPEN_COUNT] = used + 1
                    consumed = true
                }
            }
        }
        return consumed
    }

    /** Add +1 extra open from rewarded ad (stackable). */
    fun addOneFromReward() {
        ensureDailyReset()
        runBlocking {
            dataStore.edit { prefs ->
                val used = prefs[KEY_OPEN_COUNT] ?: 0
                prefs[KEY_OPEN_COUNT] = (used - 1).coerceAtLeast(0)
            }
        }
    }

    private fun todayKey(): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        const val MAX_OPENS_PER_DAY = 3
    }
}
