package com.mioacademy.app.dailychallenge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mioacademy.app.core.NotificationPrefs
import java.util.Calendar
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

        if (!DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = enabled,
                completedToday = reminderPrefs.isCompletedOn(localDate),
                alreadyFiredThisSlotToday = reminderPrefs.hasSlotFired(localDate, slot),
            )
        ) {
            return Result.success()
        }

        val (title, text) = copyForSlot(slot)
        showNotification(title, text, slot)
        reminderPrefs.markSlotFired(localDate, slot)
        return Result.success()
    }

    private fun localDate(zone: TimeZone): String {
        val c = Calendar.getInstance(zone)
        return String.format("%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    private fun copyForSlot(slot: Int): Pair<String, String> = when (slot) {
        0 -> "Günün Görevi hazır" to "Bugünün 5 yeni sorusu seni bekliyor. Güne güçlü başla!"
        1 -> "Günü kaçırma" to "Bugünkü 5 soruyu henüz çözmedin. 5 dakikanı ayır."
        else -> "Serini koru" to "Gün bitmeden bugünkü Günün Görevi'ni tamamla ve serini sürdür."
    }

    private fun showNotification(title: String, text: String, slot: Int) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Günün Görevi", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()
        nm.notify(NOTIF_ID_BASE + slot, notification)
    }

    companion object {
        const val KEY_SLOT = "dc_reminder_slot"
        private const val CHANNEL_ID = "dc_daily_challenge"
        private const val NOTIF_ID_BASE = 4100
    }
}
