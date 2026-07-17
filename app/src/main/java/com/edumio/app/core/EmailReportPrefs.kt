package com.edumio.app.core

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

    fun reportFrequency(): String = prefs.getString(KEY_FREQUENCY, FREQ_WEEKLY) ?: FREQ_WEEKLY
    fun setReportFrequency(freq: String) =
        prefs.edit().putString(KEY_FREQUENCY, freq).apply()

    fun reportHour(): Int = prefs.getInt(KEY_HOUR, 8).coerceIn(0, 23)
    fun setReportHour(hour: Int) =
        prefs.edit().putInt(KEY_HOUR, hour.coerceIn(0, 23)).apply()

    /** For UI spinner: "Haftalık" / "Aylık" */
    fun reportFrequencyLabel(): String = when (reportFrequency()) {
        FREQ_MONTHLY -> "Aylık"
        else -> "Haftalık"
    }

    fun setReportFrequencyFromLabel(label: String) {
        setReportFrequency(if (label.contains("Aylık")) FREQ_MONTHLY else FREQ_WEEKLY)
    }

    fun reportHourFormatted(): String = "%02d:00".format(reportHour())

    fun setReportHourFromFormatted(s: String) {
        val h = s.substringBefore(":").toIntOrNull()?.coerceIn(0, 23) ?: 8
        setReportHour(h)
    }

    /** Whether PDF report attachment is enabled (UI toggle). Defaults to true. */
    fun isAttachPdfEnabled(): Boolean = prefs.getBoolean(KEY_ATTACH_PDF, true)

    fun setAttachPdfEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ATTACH_PDF, enabled).apply()
    }

    companion object {
        private const val PREFS = "bb_email_report_prefs"
        private const val KEY_DAILY_ENABLED = "daily_report_enabled"
        private const val KEY_WEEKLY_ENABLED = "weekly_report_enabled"
        private const val KEY_REPORT_EMAIL = "report_email"
        private const val KEY_FREQUENCY = "report_frequency"
        private const val KEY_HOUR = "report_hour"
        private const val KEY_ATTACH_PDF = "attach_pdf"
        const val FREQ_WEEKLY = "weekly"
        const val FREQ_MONTHLY = "monthly"
    }
}
