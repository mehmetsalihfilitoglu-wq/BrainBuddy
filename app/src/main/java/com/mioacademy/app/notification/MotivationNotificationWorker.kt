package com.mioacademy.app.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mioacademy.app.R
import com.mioacademy.app.core.AnalyticsStore
import com.mioacademy.app.core.GamificationStore
import com.mioacademy.app.core.NotificationPrefs
import java.util.concurrent.TimeUnit

class MotivationNotificationWorker(
    private val appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result {
        val prefs = NotificationPrefs(appContext)
        if (!prefs.areMotivationNotificationsEnabled()) return ListenableWorker.Result.success()

        val type = inputData.getString(KEY_TYPE) ?: return ListenableWorker.Result.success()
        when (type) {
            TYPE_DAILY_REMINDER -> {
                if (!prefs.isDailyReminderEnabled()) return ListenableWorker.Result.success()
                val analytics = AnalyticsStore(appContext)
                val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
                val hasTestToday = analytics.getSessions().any { TimeUnit.MILLISECONDS.toDays(it.tsMs) == today }
                if (!hasTestToday) {
                    showNotification(appContext.getString(R.string.notif_daily_reminder_title), appContext.getString(R.string.notif_daily_reminder_text))
                }
            }
            TYPE_STREAK_WARNING -> {
                if (!prefs.isStreakWarningEnabled()) return ListenableWorker.Result.success()
                val gam = GamificationStore(appContext)
                val streak = gam.streakDays()
                if (streak > 0 && streak <= 2) {
                    showNotification(appContext.getString(R.string.notif_streak_title), appContext.getString(R.string.notif_streak_text))
                }
            }
        }
        return ListenableWorker.Result.success()
    }

    private fun showNotification(title: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notif_motivation_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (appContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        (appContext.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, notification)
    }

    companion object {
        const val KEY_TYPE = "type"
        const val TYPE_DAILY_REMINDER = "daily_reminder"
        const val TYPE_STREAK_WARNING = "streak_warning"
        private const val CHANNEL_ID = "bb_motivation"
        private const val NOTIF_ID = 3001
    }
}
