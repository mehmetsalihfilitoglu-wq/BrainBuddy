package com.edumio.app.report

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.edumio.app.auth.AuthProvider
import com.edumio.app.core.EmailReportPrefs
import com.edumio.app.core.PremiumStore
import com.edumio.app.remote.RemoteConfig
import com.edumio.app.remote.RemoteConfigKeys
import com.edumio.app.remote.RemoteConfigProvider
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules and produces Premium learning reports:
 *  - Weekly: every Sunday 20:00 local time.
 *  - Monthly: the 1st of each month, 20:00 local time.
 *
 * The worker builds real-data models ([ReportBuilder]), renders the email
 * ([ReportRenderer]) and hands it to [ReportDeliveryProvider]. Today that queues the
 * report locally for manual share; the Firebase Cloud Function adapter will send the
 * email server-side (see docs/BACKEND_ARCHITECTURE.md §5). Gated by Premium + Remote
 * Config, so nothing fires for free users and it can be turned off remotely.
 *
 * Not auto-activated (like SyncWorker): call [scheduleAll] once auth + entitlement are wired.
 */
object PremiumReportScheduler {

    private const val WEEKLY_WORK = "edumio_original_weekly_report"
    private const val MONTHLY_WORK = "edumio_original_monthly_report"

    fun scheduleAll(context: Context) {
        scheduleWeekly(context)
        scheduleMonthly(context)
    }

    fun scheduleWeekly(context: Context) {
        val request = PeriodicWorkRequestBuilder<PremiumReportWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(delayTo(Calendar.SUNDAY), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(PremiumReportWorker.KEY_TYPE to ReportType.WEEKLY.name))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WEEKLY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Monthly uses a self-rescheduling one-shot so it always lands on the 1st. */
    fun scheduleMonthly(context: Context) {
        val request = OneTimeWorkRequestBuilder<PremiumReportWorker>()
            .setInitialDelay(delayToMonthStart(), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(PremiumReportWorker.KEY_TYPE to ReportType.MONTHLY.name))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(MONTHLY_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WEEKLY_WORK)
        WorkManager.getInstance(context).cancelUniqueWork(MONTHLY_WORK)
    }

    /** Milliseconds until the next [dayOfWeek] at 20:00 local time. */
    private fun delayTo(dayOfWeek: Int): Long {
        val now = System.currentTimeMillis()
        val cal = at2000()
        while (cal.get(Calendar.DAY_OF_WEEK) != dayOfWeek || cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis - now
    }

    private fun delayToMonthStart(): Long {
        val now = System.currentTimeMillis()
        val cal = at2000()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        if (cal.timeInMillis <= now) cal.add(Calendar.MONTH, 1)
        return cal.timeInMillis - now
    }

    private fun at2000(): Calendar = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 20)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}

class PremiumReportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val type = runCatching { ReportType.valueOf(inputData.getString(KEY_TYPE) ?: "") }
            .getOrNull() ?: return Result.success()

        val config: RemoteConfig = RemoteConfigProvider.get()
        val enabledKey = if (type == ReportType.WEEKLY)
            RemoteConfigKeys.WEEKLY_REPORTS_ENABLED else RemoteConfigKeys.MONTHLY_REPORTS_ENABLED

        if (PremiumStore(ctx).isPremium() && config.getBoolean(enabledKey, true)) {
            val recipient = EmailReportPrefs(ctx).reportEmail().ifBlank { null }
                ?: AuthProvider.currentUser(ctx)?.email
            val payload = when (type) {
                ReportType.WEEKLY -> ReportRenderer.renderWeekly(ReportBuilder.buildWeekly(ctx), recipient)
                ReportType.MONTHLY -> ReportRenderer.renderMonthly(ReportBuilder.buildMonthly(ctx), recipient)
            }
            ReportDeliveryProvider.service(ctx).deliver(payload)
        }

        // Monthly is a one-shot → line up next month.
        if (type == ReportType.MONTHLY) PremiumReportScheduler.scheduleMonthly(ctx)
        return Result.success()
    }

    companion object {
        const val KEY_TYPE = "report_type"
    }
}
