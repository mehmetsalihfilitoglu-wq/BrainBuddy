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
        WorkManager.getInstance(context).cancelUniqueWork("brainbuddy_daily_report")
        WorkManager.getInstance(context).cancelUniqueWork("brainbuddy_weekly_report")

        if (prefs.isDailyReportEnabled()) {
            val daily = PeriodicWorkRequestBuilder<ReportWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().build())
                .setInputData(androidx.work.workDataOf(ReportWorker.KEY_IS_DAILY to true))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "brainbuddy_daily_report",
                ExistingPeriodicWorkPolicy.KEEP,
                daily
            )
        }

        if (prefs.isWeeklyReportEnabled()) {
            val weekly = PeriodicWorkRequestBuilder<ReportWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().build())
                .setInputData(androidx.work.workDataOf(ReportWorker.KEY_IS_DAILY to false))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "brainbuddy_weekly_report",
                ExistingPeriodicWorkPolicy.KEEP,
                weekly
            )
        }
    }
}
