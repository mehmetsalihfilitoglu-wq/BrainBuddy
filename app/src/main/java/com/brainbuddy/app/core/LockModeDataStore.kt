package com.brainbuddy.app.core

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.lockModeDataStore by preferencesDataStore(name = "lock_mode_prefs")

private val KEY_LOCK_MODE_ENABLED = booleanPreferencesKey("lock_mode_enabled")
private val KEY_LAST_KNOWN_SERVICE_ENABLED = booleanPreferencesKey("last_known_service_enabled")
private val KEY_PARENT_PIN_HASH = stringPreferencesKey("parent_pin_hash")

/**
 * Persistent state for Accessibility bypass protection.
 * - lockModeEnabled: true when AccessibilityService is off but protection was on; requires Parent PIN.
 * - lastKnownServiceEnabled: last known AccessibilityService state.
 * - parentPinHash: stored when PIN is set (from PinManager); empty when not set.
 */
class LockModeDataStore(private val context: Context) {

    private val dataStore = context.lockModeDataStore

    fun getLockModeEnabled(): Boolean = runBlocking {
        dataStore.data.map { it[KEY_LOCK_MODE_ENABLED] ?: false }.first()
    }

    suspend fun setLockModeEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LOCK_MODE_ENABLED] = enabled }
    }

    fun setLockModeEnabledSync(enabled: Boolean) {
        runBlocking { setLockModeEnabled(enabled) }
    }

    fun getLastKnownServiceEnabled(): Boolean = runBlocking {
        dataStore.data.map { it[KEY_LAST_KNOWN_SERVICE_ENABLED] ?: true }.first()
    }

    suspend fun setLastKnownServiceEnabled(enabled: Boolean) {
        dataStore.edit { it[KEY_LAST_KNOWN_SERVICE_ENABLED] = enabled }
    }

    fun setLastKnownServiceEnabledSync(enabled: Boolean) {
        runBlocking { setLastKnownServiceEnabled(enabled) }
    }

    fun getParentPinHash(): String = runBlocking {
        dataStore.data.map { it[KEY_PARENT_PIN_HASH] ?: "" }.first()
    }

    suspend fun setParentPinHash(hash: String) {
        dataStore.edit { it[KEY_PARENT_PIN_HASH] = hash }
    }

    /** Sync parent pin status from PinManager (isPinSet -> store "1", else "") */
    fun syncParentPinFromManager() {
        val pinManager = com.brainbuddy.app.security.PinManager(context)
        val value = if (pinManager.isPinSet()) "1" else ""
        runBlocking { setParentPinHash(value) }
    }

    /** Clear lock mode (e.g. after successful PIN verify). */
    suspend fun clearLockMode() {
        dataStore.edit {
            it[KEY_LOCK_MODE_ENABLED] = false
            it[KEY_LAST_KNOWN_SERVICE_ENABLED] = true
        }
    }

    fun clearLockModeSync() {
        runBlocking { clearLockMode() }
    }
}
