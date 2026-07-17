package com.edumio.app.core

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.wrongReportUnlockDataStore by preferencesDataStore(name = "wrong_report_unlock")

private val KEY_UNLOCKED_UNTIL = longPreferencesKey("wrong_report_unlocked_until_ms")

/**
 * Persists wrong-report screen unlock timestamp (e.g. 10 min after rewarded ad).
 * Premium users bypass this; non-premium need to watch ad to unlock.
 */
class WrongReportUnlockStore(private val context: Context) {

    private val dataStore = context.wrongReportUnlockDataStore

    /** Returns true if report detail screen is unlocked (timestamp > now). */
    fun isUnlocked(): Boolean = runBlocking {
        dataStore.data.map { prefs ->
            val until = prefs[KEY_UNLOCKED_UNTIL] ?: 0L
            until > System.currentTimeMillis()
        }.first()
    }

    /** Grant access for UNLOCK_DURATION_MS. Call after rewarded ad. */
    fun setUnlocked() {
        runBlocking {
            dataStore.edit { prefs ->
                prefs[KEY_UNLOCKED_UNTIL] = System.currentTimeMillis() + UNLOCK_DURATION_MS
            }
        }
    }

    companion object {
        private const val UNLOCK_DURATION_MS = 10 * 60 * 1000L // 10 minutes
    }
}
