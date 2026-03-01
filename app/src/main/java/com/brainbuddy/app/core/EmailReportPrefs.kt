package com.brainbuddy.app.core

import android.content.Context

/** Parent-only: daily/weekly email report settings. */
class EmailReportPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isDailyReportEnabled(): Boolean = prefs.getBoolean(KEY_DAILY_ENABLED, false)
    fun setDailyReportEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_DAILY_ENABLED, enabled).apply()

    fun isWeeklyReportEnabled(): Boolean = prefs.getBoolean(KEY_WEEKLY_ENABLED, false)
    fun setWeeklyReportEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_WEEKLY_ENABLED, enabled).apply()

    fun reportEmail(): String = prefs.getString(KEY_REPORT_EMAIL, "") ?: ""
    fun setReportEmail(email: String) =
        prefs.edit().putString(KEY_REPORT_EMAIL, email).apply()

    companion object {
        private const val PREFS = "bb_email_report_prefs"
        private const val KEY_DAILY_ENABLED = "daily_report_enabled"
        private const val KEY_WEEKLY_ENABLED = "weekly_report_enabled"
        private const val KEY_REPORT_EMAIL = "report_email"
    }
}
