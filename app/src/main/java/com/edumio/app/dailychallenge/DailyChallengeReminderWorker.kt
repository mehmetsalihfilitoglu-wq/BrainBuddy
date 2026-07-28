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
        // 0 = the 11:30 reminder, 1 = the 18:30 follow-up. Two a day at most, and the second only if
        // the challenge is STILL incomplete — the completion check below suppresses it otherwise.
        val slot = inputData.getInt(KEY_SLOT, 0).coerceIn(0, 1)
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

        val posted = if (slot == 0) showNotification(TITLE, BODY, slot)
        else showNotification(SECOND_TITLE, SECOND_BODY, slot)
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
        /** Calm, non-guilt copy. The old "Günü kaçırma" / "Serini koru" reminders are gone for good. */
        private const val TITLE = "Günün Görevi hazır"
        private const val BODY = "Bugünün 5 sorusu seni bekliyor."
        private const val SECOND_TITLE = "Bugünün görevini tamamla"
        private const val SECOND_BODY = "5 soruluk görevini tamamlamak için hâlâ zamanın var."
        private const val CHANNEL_ID = "dc_daily_challenge"
        private const val NOTIF_ID_BASE = 4100
    }
}
