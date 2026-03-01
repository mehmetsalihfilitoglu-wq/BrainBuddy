package com.brainbuddy.app.core

import android.content.Context

/** Parent Kill Switch: temporarily disable all blocking. Auto-expire options. */
class KillSwitchPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isKillSwitchActive(): Boolean {
        if (!prefs.getBoolean(KEY_ACTIVE, false)) return false
        val expireAt = prefs.getLong(KEY_EXPIRE_AT_MS, Long.MAX_VALUE)
        return System.currentTimeMillis() < expireAt
    }

    fun setKillSwitchActive(active: Boolean, expireOption: ExpireOption = ExpireOption.MANUAL) {
        val expireAt = when (expireOption) {
            ExpireOption.MINUTES_15 -> System.currentTimeMillis() + 15 * 60 * 1000L
            ExpireOption.HOUR_1 -> System.currentTimeMillis() + 60 * 60 * 1000L
            ExpireOption.HOURS_24 -> System.currentTimeMillis() + 24 * 60 * 60 * 1000L
            ExpireOption.MANUAL -> Long.MAX_VALUE
        }
        prefs.edit()
            .putBoolean(KEY_ACTIVE, active)
            .putLong(KEY_EXPIRE_AT_MS, if (active) expireAt else 0L)
            .putString(KEY_EXPIRE_OPTION, expireOption.name)
            .apply()
    }

    fun deactivateKillSwitch() = setKillSwitchActive(false)

    fun getExpireOption(): ExpireOption {
        return try {
            ExpireOption.valueOf(prefs.getString(KEY_EXPIRE_OPTION, ExpireOption.MANUAL.name) ?: ExpireOption.MANUAL.name)
        } catch (_: Exception) { ExpireOption.MANUAL }
    }

    enum class ExpireOption { MINUTES_15, HOUR_1, HOURS_24, MANUAL }

    companion object {
        private const val PREFS = "bb_kill_switch"
        private const val KEY_ACTIVE = "kill_switch_active"
        private const val KEY_EXPIRE_AT_MS = "kill_switch_expire_at_ms"
        private const val KEY_EXPIRE_OPTION = "kill_switch_expire_option"
    }
}
