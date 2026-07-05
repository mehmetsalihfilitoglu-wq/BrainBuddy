package com.mioacademy.app.core

import android.content.Context

class AdsPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAdsEnabled(): Boolean = prefs.getBoolean(KEY_ADS_ENABLED, true)
    fun setAdsEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ADS_ENABLED, enabled).apply()

    companion object {
        private const val PREFS = "bb_ads_prefs"
        private const val KEY_ADS_ENABLED = "ads_enabled"
    }
}
