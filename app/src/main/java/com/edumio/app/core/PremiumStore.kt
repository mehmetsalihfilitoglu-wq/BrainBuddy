package com.edumio.app.core

import android.content.Context

class PremiumStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPremium(): Boolean = prefs.getBoolean(KEY_PREMIUM, false)
    fun setPremium(premium: Boolean) = prefs.edit().putBoolean(KEY_PREMIUM, premium).apply()

    companion object {
        private const val PREFS = "bb_premium"
        private const val KEY_PREMIUM = "is_premium"
    }
}
