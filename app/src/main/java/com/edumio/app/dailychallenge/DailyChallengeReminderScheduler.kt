package com.edumio.app.dailychallenge

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.edumio.app.core.NotificationPrefs
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Schedules the three local Daily-Challenge reminders (09:00 / 16:00 / 20:30 local) as one periodic
 * worker per slot. The workers stay scheduled every day; the per-slot [DailyChallengeReminderWorker]
 * gate suppresses any slot once the user completes today's challenge, so "remaining reminders are
 * cancelled after completion" while tomorrow's slots remain intact.
 *
 * No exact-alarm permission is needed — a daily periodic nudge is enough. Everything is gated by
 * [NotificationPrefs]; the user can turn reminders off in Settings.
 */
object DailyChallengeReminderScheduler {

    private const val WORK_PREFIX = "dc_reminder_slot_"

    /** Idempotent: safe to call on every app start. Honors the user's notification toggles. */
    fun schedule(context: Context) {
        val prefs = NotificationPrefs(context)
        if (!prefs.areMotivationNotificationsEnabled() || !prefs.isDailyReminderEnabled()) {
            cancel(context)
            return
        }
        val wm = WorkManager.getInstance(context)
        val nowMinutes = nowMinuteOfDay()
        for (slot in 0 until DailyChallengeReminderPolicy.slotCount()) {
            val slotMinutes = DailyChallengeReminderPolicy.SLOT_MINUTES[slot]
            val delayMin = DailyChallengeReminderPolicy.delayMinutesToSlot(slotMinutes, nowMinutes).toLong()
            wm.enqueueUniquePeriodicWork(
                WORK_PREFIX + slot,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<DailyChallengeReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayMin, TimeUnit.MINUTES)
                    .setInputData(workDataOf(DailyChallengeReminderWorker.KEY_SLOT to slot))
                    .build(),
            )
        }
    }

    fun reschedule(context: Context) {
        cancel(context)
        schedule(context)
    }

    fun cancel(context: Context) {
        val wm = WorkManager.getInstance(context)
        for (slot in 0 until DailyChallengeReminderPolicy.slotCount()) {
            wm.cancelUniqueWork(WORK_PREFIX + slot)
        }
    }

    /**
     * Call the moment today's challenge is completed. Flips the completed marker so every remaining
     * reminder slot suppresses itself. Tomorrow's slots are untouched (the periodic workers stay).
     */
    fun onChallengeCompleted(context: Context, localDate: String) {
        DailyChallengeReminderPrefs(context).markCompleted(localDate)
    }

    private fun nowMinuteOfDay(zone: TimeZone = TimeZone.getDefault()): Int {
        val c = Calendar.getInstance(zone)
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }
}
