package com.edumio.app.core

import android.content.Context

object OnboardingPrefs {
    private const val PREFS = "edu_onboarding_prefs"
    private const val KEY_IS_DONE = "is_onboarding_done"

    fun isDone(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_IS_DONE, false)

    fun setDone(context: Context, done: Boolean) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IS_DONE, done)
            .apply()
}
