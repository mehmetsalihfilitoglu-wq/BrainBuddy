package com.brainbuddy.app.junior

import android.content.Context
import com.brainbuddy.app.core.ProfileStore

/**
 * Per-profile Junior module settings. Parent-controlled (PIN required).
 */
class JuniorPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val profileStore = ProfileStore(context)

    private fun profileKey(suffix: String): String =
        "profile_${profileStore.getCurrentProfileId()}_$suffix"

    fun isJuniorEnabled(): Boolean = prefs.getBoolean(profileKey(KEY_ENABLED), false)

    fun setJuniorEnabled(profileId: String, enabled: Boolean) {
        prefs.edit().putBoolean("profile_${profileId}_$KEY_ENABLED", enabled).apply()
    }

    fun setJuniorEnabledForCurrent(enabled: Boolean) {
        prefs.edit().putBoolean(profileKey(KEY_ENABLED), enabled).apply()
    }

    fun getJuniorDailyMinutes(profileId: String): Int =
        prefs.getInt("profile_${profileId}_$KEY_DAILY_MIN", 20).coerceIn(5, 60)

    fun setJuniorDailyMinutes(profileId: String, minutes: Int) {
        prefs.edit().putInt("profile_${profileId}_$KEY_DAILY_MIN", minutes.coerceIn(5, 60)).apply()
    }

    fun getJuniorDailyMinutes(): Int = prefs.getInt(profileKey(KEY_DAILY_MIN), 20).coerceIn(5, 60)

    fun setJuniorDailyMinutes(minutes: Int) {
        prefs.edit().putInt(profileKey(KEY_DAILY_MIN), minutes.coerceIn(5, 60)).apply()
    }

    fun isMicEnabled(profileId: String): Boolean =
        prefs.getBoolean("profile_${profileId}_$KEY_MIC_ENABLED", false)

    fun setMicEnabled(profileId: String, enabled: Boolean) {
        prefs.edit().putBoolean("profile_${profileId}_$KEY_MIC_ENABLED", enabled).apply()
    }

    fun isMicEnabled(): Boolean = prefs.getBoolean(profileKey(KEY_MIC_ENABLED), false)

    fun setMicEnabledForCurrent(enabled: Boolean) {
        prefs.edit().putBoolean(profileKey(KEY_MIC_ENABLED), enabled).apply()
    }

    fun isJuniorEnabledForProfile(profileId: String): Boolean =
        prefs.getBoolean("profile_${profileId}_$KEY_ENABLED", false)

    companion object {
        private const val PREFS = "bb_junior_prefs"
        private const val KEY_ENABLED = "junior_enabled"
        private const val KEY_DAILY_MIN = "junior_daily_min"
        private const val KEY_MIC_ENABLED = "junior_mic_enabled"
    }
}
