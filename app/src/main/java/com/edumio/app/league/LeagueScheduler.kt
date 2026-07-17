package com.edumio.app.league

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Schedules weekly league reset for Monday 00:10 local time.
 */
object LeagueScheduler {

    private const val WORK_NAME = "edumio_league_weekly_reset"

    fun scheduleNextReset(context: Context) {
        val delayMs = getDelayUntilNextMonday0010()
        val request = OneTimeWorkRequestBuilder<LeagueWeeklyWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun getDelayUntilNextMonday0010(): Long {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 10)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        var next = cal.timeInMillis
        if (next <= now) {
            cal.add(Calendar.DAY_OF_MONTH, 7)
            next = cal.timeInMillis
        }
        return next - now
    }
}
