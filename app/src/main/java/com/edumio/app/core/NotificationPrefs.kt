package com.edumio.app.core

import android.content.Context

/** Motivation notifications (daily reminder, streak warning). Parent can toggle. */
class NotificationPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun areMotivationNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_MOTIVATION_ENABLED, true)
    fun setMotivationNotificationsEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_MOTIVATION_ENABLED, enabled).apply()

    fun isDailyReminderEnabled(): Boolean = prefs.getBoolean(KEY_DAILY_REMINDER, true)
    fun setDailyReminderEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_DAILY_REMINDER, enabled).apply()

    fun isStreakWarningEnabled(): Boolean = prefs.getBoolean(KEY_STREAK_WARNING, true)
    fun setStreakWarningEnabled(enabled: Boolean) =
        prefs.edit().putBoolean(KEY_STREAK_WARNING, enabled).apply()

    fun lastDailyReminderSentMs(): Long = prefs.getLong(KEY_LAST_DAILY_REMINDER_MS, 0L)
    fun setLastDailyReminderSentMs(ms: Long) =
        prefs.edit().putLong(KEY_LAST_DAILY_REMINDER_MS, ms).apply()

    /** Whether we've already shown the OS notification-permission prompt once. */
    fun wasPermissionRequested(): Boolean = prefs.getBoolean(KEY_PERMISSION_REQUESTED, false)
    fun setPermissionRequested() = prefs.edit().putBoolean(KEY_PERMISSION_REQUESTED, true).apply()

    companion object {
        private const val PREFS = "bb_notification_prefs"
        private const val KEY_MOTIVATION_ENABLED = "motivation_enabled"
        private const val KEY_DAILY_REMINDER = "daily_reminder"
        private const val KEY_STREAK_WARNING = "streak_warning"
        private const val KEY_LAST_DAILY_REMINDER_MS = "last_daily_reminder_ms"
        private const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }
}
