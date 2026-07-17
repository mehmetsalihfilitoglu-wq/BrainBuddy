package com.edumio.app.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.edumio.app.core.NotificationPrefs
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules the safe, local study reminders via WorkManager.
 *
 * The worker itself ([MotivationNotificationWorker]) only shows a notification
 * when real data justifies it (no test today / streak at risk), so these are
 * never spam. Everything is gated by [NotificationPrefs] and the user can turn
 * them off in Settings. No exact-alarm permission is needed — periodic work is
 * enough for a daily nudge.
 */
object NotificationScheduler {

    private const val DAILY_WORK = "bb_daily_reminder"
    private const val STREAK_WORK = "bb_streak_warning"

    /** Idempotent: safe to call on every app start. Honors the user's toggles. */
    fun schedule(context: Context) {
        val prefs = NotificationPrefs(context)
        if (!prefs.areMotivationNotificationsEnabled()) {
            cancel(context)
            return
        }
        val wm = WorkManager.getInstance(context)

        if (prefs.isDailyReminderEnabled()) {
            wm.enqueueUniquePeriodicWork(
                DAILY_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MotivationNotificationWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayToHour(18), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        MotivationNotificationWorker.KEY_TYPE to MotivationNotificationWorker.TYPE_DAILY_REMINDER))
                    .build()
            )
        } else {
            wm.cancelUniqueWork(DAILY_WORK)
        }

        if (prefs.isStreakWarningEnabled()) {
            wm.enqueueUniquePeriodicWork(
                STREAK_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<MotivationNotificationWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayToHour(20), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        MotivationNotificationWorker.KEY_TYPE to MotivationNotificationWorker.TYPE_STREAK_WARNING))
                    .build()
            )
        } else {
            wm.cancelUniqueWork(STREAK_WORK)
        }
    }

    /** Re-apply after the user changes notification settings. */
    fun reschedule(context: Context) {
        cancel(context)
        schedule(context)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(DAILY_WORK)
        wm.cancelUniqueWork(STREAK_WORK)
    }

    /** Milliseconds from now until the next occurrence of [hour]:00 local time. */
    private fun delayToHour(hour: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0)
    }
}
