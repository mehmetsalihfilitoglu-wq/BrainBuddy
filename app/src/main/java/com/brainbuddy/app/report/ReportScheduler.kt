package com.brainbuddy.app.report

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReportScheduler {

    fun schedule(context: Context) {
        val prefs = com.brainbuddy.app.core.EmailReportPrefs(context)
        // Cancel legacy workers (daily/weekly split) to avoid duplicates.
        WorkManager.getInstance(context).cancelUniqueWork("brainbuddy_daily_report")
        WorkManager.getInstance(context).cancelUniqueWork("brainbuddy_weekly_report")

        if (prefs.isWeeklyReportEnabled()) {
            // Use frequency to choose period: weekly (7 days) or monthly (30 days).
            val frequency = prefs.reportFrequency()
            val intervalDays = if (frequency == com.brainbuddy.app.core.EmailReportPrefs.FREQ_MONTHLY) 30L else 7L

            val work = PeriodicWorkRequestBuilder<ReportWorker>(intervalDays, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().build())
                // All automatic reports use the "weekly" generator for now (summary over range).
                .setInputData(androidx.work.workDataOf(ReportWorker.KEY_IS_DAILY to false))
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "brainbuddy_email_report",
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }
}
