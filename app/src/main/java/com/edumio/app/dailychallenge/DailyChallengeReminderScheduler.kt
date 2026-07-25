package com.edumio.app.dailychallenge

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.edumio.app.core.NotificationPrefs
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Schedules EDUmio's ONE daily study reminder (11:30 local, allowed window 11:00–20:00).
 *
 * Uses a ONE-TIME request that re-arms itself after each run rather than a PeriodicWorkRequest.
 * A periodic request only guarantees "once per period" — its actual fire time drifts and Doze defers
 * it to the next maintenance window, which is how reminders were observed at 04:02 and 08:10. A
 * one-time request whose delay is recomputed against the local wall clock every time keeps the
 * reminder anchored to 11:30, and the worker re-checks the window before posting.
 *
 * Everything is keyed to ONE unique work name, so reboots, app updates and repeated launches can
 * never stack duplicates. Legacy 3-slot work from earlier versions is cancelled on every call.
 */
object DailyChallengeReminderScheduler {

    /** Single unique work name — enqueueUniqueWork(REPLACE) makes duplicates impossible. */
    private const val WORK_NAME = "dc_reminder_daily"

    /** Pre-v1 scheduled three periodic slots (09:00 / 16:00 / 20:30); they must be cancelled on upgrade. */
    private const val LEGACY_WORK_PREFIX = "dc_reminder_slot_"
    private const val LEGACY_SLOT_COUNT = 3

    /** Idempotent: safe on every app start. Honors the user's notification toggles. */
    fun schedule(context: Context) {
        cancelLegacy(context) // upgrade path: never leave the old 3-a-day periodic workers running
        val prefs = NotificationPrefs(context)
        if (!prefs.areMotivationNotificationsEnabled() || !prefs.isDailyReminderEnabled()) {
            cancel(context)
            return
        }
        enqueueNext(context)
    }

    /**
     * Arms the next reminder for the upcoming 11:30 local. If 11:30 has already passed today this
     * schedules TOMORROW — a missed reminder is never fired late as a catch-up.
     */
    fun enqueueNext(context: Context) {
        val delayMinutes = DailyChallengeReminderPolicy
            .delayMinutesToNextReminder(nowMinuteOfDay())
            .toLong()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE, // replacing always cancels the previous schedule first
            OneTimeWorkRequestBuilder<DailyChallengeReminderWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .build(),
        )
    }

    fun reschedule(context: Context) {
        cancel(context)
        schedule(context)
    }

    /** Cancels every pending EDUmio daily reminder, current and legacy. */
    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        cancelLegacy(context)
    }

    /**
     * Call the moment today's challenge is completed: marks the day done so today's reminder — if it
     * is still pending — suppresses itself instead of nagging a student who already finished.
     * Tomorrow's reminder is unaffected.
     */
    fun onChallengeCompleted(context: Context, localDate: String) {
        DailyChallengeReminderPrefs(context).markCompleted(localDate)
    }

    private fun cancelLegacy(context: Context) {
        val wm = WorkManager.getInstance(context)
        for (slot in 0 until LEGACY_SLOT_COUNT) wm.cancelUniqueWork(LEGACY_WORK_PREFIX + slot)
    }

    /** Local wall-clock minute-of-day — recomputed each call, so timezone/DST changes are picked up. */
    private fun nowMinuteOfDay(zone: TimeZone = TimeZone.getDefault()): Int {
        val c = Calendar.getInstance(zone)
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
}
