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

    private const val DAILY_WORK = "edu_daily_reminder"
    private const val STREAK_WORK = "edu_streak_warning"

    /**
     * v1 NOTIFICATION POLICY: ONE respectful reminder a day, owned entirely by
     * [com.edumio.app.dailychallenge.DailyChallengeReminderScheduler] (11:30 local, 11:00–20:00 window).
     *
     * This legacy motivation scheduler added a SECOND and THIRD daily notification (an 18:00 "daily
     * reminder" and a 20:00 "streak warning") on top of that, using PeriodicWorkRequests whose fire
     * time drifts into Doze maintenance windows — part of why reminders were observed at 04:02/08:10.
     * For v1 it is disabled and any previously-enqueued work is cancelled, including on upgrade.
     * Calling schedule() is therefore an explicit "make sure none of this is queued".
     */
    fun schedule(context: Context) {
        cancel(context)
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
