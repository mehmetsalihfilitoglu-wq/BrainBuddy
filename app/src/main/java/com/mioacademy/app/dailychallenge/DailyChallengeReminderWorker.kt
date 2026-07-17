package com.mioacademy.app.dailychallenge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mioacademy.app.core.NotificationPrefs
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
        val slot = inputData.getInt(KEY_SLOT, -1)
        if (slot < 0 || slot >= DailyChallengeReminderPolicy.slotCount()) return Result.success()

        val notifPrefs = NotificationPrefs(appContext)
        val enabled = notifPrefs.areMotivationNotificationsEnabled() && notifPrefs.isDailyReminderEnabled()

        val zone = TimeZone.getDefault()
        val localDate = localDate(zone)
        val reminderPrefs = DailyChallengeReminderPrefs(appContext)

        val analytics = DailyChallengeAnalyticsProvider.get(appContext)
        val completed = reminderPrefs.isCompletedOn(localDate)
        if (!DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = enabled,
                completedToday = completed,
                alreadyFiredThisSlotToday = reminderPrefs.hasSlotFired(localDate, slot),
            )
        ) {
            analytics.track(
                DcEvents.REMINDER_SUPPRESSED,
                mapOf(
                    DcEvents.P_SLOT to slot,
                    DcEvents.P_REASON to when {
                        !enabled -> "disabled"; completed -> "completed"; else -> "already_fired"
                    },
                ),
            )
            return Result.success()
        }

        val (title, text) = copyForSlot(slot)
        val posted = showNotification(title, text, slot)
        if (posted) {
            reminderPrefs.markSlotFired(localDate, slot)
            analytics.track(DcEvents.REMINDER_SHOWN, mapOf(DcEvents.P_SLOT to slot, DcEvents.P_LOCAL_DATE to localDate))
        } else {
            analytics.track(DcEvents.REMINDER_SUPPRESSED, mapOf(DcEvents.P_SLOT to slot, DcEvents.P_REASON to "post_failed"))
        }
        return Result.success()
    }

    private fun localDate(zone: TimeZone): String = DailyChallengeDates.localDate(zone)

    private fun copyForSlot(slot: Int): Pair<String, String> = when (slot) {
        0 -> "Günün Görevi hazır" to "Bugünün 5 yeni sorusu seni bekliyor. Güne güçlü başla!"
        1 -> "Günü kaçırma" to "Bugünkü 5 soruyu henüz çözmedin. 5 dakikanı ayır."
        else -> "Serini koru" to "Gün bitmeden bugünkü Günün Görevi'ni tamamla ve serini sürdür."
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
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_BASE + slot, notification)
        true
    } catch (_: Throwable) {
        false // notifications unavailable/blocked → give up quietly, do not crash or retry
    }

    companion object {
        const val KEY_SLOT = "dc_reminder_slot"
        private const val CHANNEL_ID = "dc_daily_challenge"
        private const val NOTIF_ID_BASE = 4100
    }
}
