package com.mioacademy.app.report

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import com.mioacademy.app.R
import com.mioacademy.app.core.EmailReportPrefs
import com.mioacademy.app.ui.ReportShareActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class ReportWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val prefs = EmailReportPrefs(appContext)
        val email = prefs.reportEmail().ifBlank { null }
            ?: com.mioacademy.app.auth.AuthProvider.get(appContext).currentEmail()
        if (email.isNullOrBlank()) return@withContext ListenableWorker.Result.success()

        val isDaily = inputData.getBoolean(KEY_IS_DAILY, true)
        val (html, text) = if (isDaily) ReportGenerator.generateDailyReport(appContext)
        else ReportGenerator.generateWeeklyReport(appContext)
        val subject = if (isDaily) "MioAcademy Günlük Rapor" else "MioAcademy Haftalık Rapor"

        // Server not configured: save report and show notification for manual share
        val file = File(appContext.filesDir, "pending_report.txt")
        val subjFile = File(appContext.filesDir, "pending_report_subject.txt")
        file.writeText(text)
        subjFile.writeText(subject)

        ensureNotificationChannel()
        val intent = Intent(appContext, ReportShareActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            appContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(appContext.getString(R.string.report_ready_title))
            .setContentText(appContext.getString(R.string.report_ready_text))
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, notification)

        ListenableWorker.Result.success()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.report_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    companion object {
        const val KEY_IS_DAILY = "is_daily"
        private const val CHANNEL_ID = "bb_reports"
        private const val NOTIF_ID = 2001
    }
}
