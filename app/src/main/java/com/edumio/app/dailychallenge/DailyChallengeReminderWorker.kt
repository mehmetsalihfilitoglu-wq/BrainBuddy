package com.edumio.app.dailychallenge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.edumio.app.core.NotificationPrefs
import java.util.TimeZone

/**
 * Posts a single Daily-Challenge reminder for its slot, but ONLY when [DailyChallengeReminderPolicy]
 * allows it: notifications enabled, today's challenge not yet completed, and this slot hasn't already
 * fired today. Completing the challenge flips the completed marker, which suppresses every remaining
 * slot for the day.
 */
class DailyChallengeReminderWorker(
    private val appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val slot = SLOT // v1 posts exactly ONE reminder a day
        val notifPrefs = NotificationPrefs(appContext)
        val enabled = notifPrefs.areMotivationNotificationsEnabled() && notifPrefs.isDailyReminderEnabled()

        val zone = TimeZone.getDefault()
        val localDate = localDate(zone)
        val reminderPrefs = DailyChallengeReminderPrefs(appContext)

        val analytics = DailyChallengeAnalyticsProvider.get(appContext)
        val completed = reminderPrefs.isCompletedOn(localDate)
        // Local wall-clock minute, read HERE rather than when the work was queued: WorkManager may run
        // this long after the intended time (Doze / device asleep), which is how 04:02 and 08:10
        // happened. The window check below turns such a late run into a no-op.
        val nowMinute = nowMinuteOfDay(zone)
        if (!DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = enabled,
                completedToday = completed,
                alreadyFiredToday = reminderPrefs.hasSlotFired(localDate, slot),
                nowMinuteOfDay = nowMinute,
            )
        ) {
            analytics.track(
                DcEvents.REMINDER_SUPPRESSED,
                mapOf(
                    DcEvents.P_SLOT to slot,
                    DcEvents.P_REASON to when {
                        !enabled -> "disabled"
                        completed -> "completed"
                        !DailyChallengeReminderPolicy.isWithinAllowedWindow(nowMinute) -> "outside_window"
                        else -> "already_fired"
                    },
                ),
            )
            // Still re-arm: a suppressed run must not end the daily schedule.
            DailyChallengeReminderScheduler.enqueueNext(appContext)
            return Result.success()
        }

        val posted = showNotification(TITLE, BODY, slot)
        if (posted) {
            reminderPrefs.markSlotFired(localDate, slot)
            analytics.track(DcEvents.REMINDER_SHOWN, mapOf(DcEvents.P_SLOT to slot, DcEvents.P_LOCAL_DATE to localDate))
        } else {
            analytics.track(DcEvents.REMINDER_SUPPRESSED, mapOf(DcEvents.P_SLOT to slot, DcEvents.P_REASON to "post_failed"))
        }
        // Arm tomorrow's reminder against the CURRENT local clock, so timezone and DST changes are
        // picked up automatically and exactly one reminder stays queued at any time.
        DailyChallengeReminderScheduler.enqueueNext(appContext)
        return Result.success()
    }

    private fun localDate(zone: TimeZone): String = DailyChallengeDates.localDate(zone)

    private fun nowMinuteOfDay(zone: TimeZone): Int {
        val c = java.util.Calendar.getInstance(zone)
        return c.get(java.util.Calendar.HOUR_OF_DAY) * 60 + c.get(java.util.Calendar.MINUTE)
    }

    /** Posts the reminder. Never throws — a notification failure must not fail/retry the worker. */
    private fun showNotification(title: String, text: String, slot: Int): Boolean = try {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "EDUmio Günün Görevi", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(com.edumio.app.R.drawable.ic_notification_mascot)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_BASE + slot, notification)
        true
    } catch (_: Throwable) {
        false // notifications unavailable/blocked → give up quietly, do not crash or retry
    }

    companion object {
        const val KEY_SLOT = "dc_reminder_slot"
        /** v1 has exactly one reminder a day; the slot index is retained only for the fired-marker key. */
        private const val SLOT = 0
        /** Calm, non-guilt copy. The old second/third reminders ("Günü kaçırma", "Serini koru") are gone. */
        private const val TITLE = "Günün Görevi hazır"
        private const val BODY = "Bugünün 5 sorusu seni bekliyor."
        private const val CHANNEL_ID = "dc_daily_challenge"
        private const val NOTIF_ID_BASE = 4100
    }
}
