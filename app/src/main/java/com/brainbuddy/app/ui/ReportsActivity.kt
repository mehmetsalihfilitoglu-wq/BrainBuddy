package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ReportStore
import com.brainbuddy.app.core.TestPerformance
import com.brainbuddy.app.databinding.ActivityReportsBinding
import com.brainbuddy.app.ui.BarChartView.BarData
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        val b = ActivityReportsBinding.inflate(layoutInflater)
        setContentView(b.root)
        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val reportStore = ReportStore(this)
        val analytics = AnalyticsStore(this)
        val now = System.currentTimeMillis()
        val dayAgo = now - TimeUnit.DAYS.toMillis(1)
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)

        val dailyLockEvents = reportStore.getLockEventsSince(dayAgo)
        val dailyAttempts = reportStore.getBlockedAttemptsSince(dayAgo)
        val weeklyAttempts = reportStore.getBlockedAttemptsSince(weekAgo)
        val totalBlockedCount = dailyAttempts.values.sum()

        val dailySessions = analytics.getSessions().count { it.tsMs >= dayAgo }
        val weeklySessions = analytics.getSessions().filter { it.tsMs >= weekAgo }
        val perfs = analytics.getTestPerformances().filter { it.tsMs >= weekAgo }

        val dailyPerfs = perfs.filter { it.tsMs >= dayAgo }
        val dailyCorrect = dailyPerfs.sumOf { it.correctCount }
        val dailyWrong = dailyPerfs.sumOf { it.wrongCount }
        val dailyBlank = dailyPerfs.sumOf { it.blankCount }
        val dailyTotal = dailyCorrect + dailyWrong + dailyBlank
        val dailyAccuracy = if (dailyTotal > 0) 100f * dailyCorrect / dailyTotal else 0f

        b.tvDailyTests.text = dailySessions.toString()
        b.tvDailyAccuracy.text = "%.0f%%".format(dailyAccuracy)
        b.tvDailyBlocked.text = totalBlockedCount.toString()

        b.miniStackedBar.correct = dailyCorrect
        b.miniStackedBar.wrong = dailyWrong
        b.miniStackedBar.blank = dailyBlank

        val last7 = perfs.takeLast(14)
        val byDay = mutableMapOf<Int, MutableList<TestPerformance>>()
        val cal = Calendar.getInstance()
        for (i in 0 until 7) {
            cal.timeInMillis = now
            cal.add(Calendar.DAY_OF_YEAR, -6 + i)
            val dayStart = cal.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            val dayEnd = dayStart + TimeUnit.DAYS.toMillis(1)
            val dayPerfs = last7.filter { it.tsMs in dayStart until dayEnd }
            byDay[i] = dayPerfs.toMutableList()
        }
        val trendValues = (0 until 7).map { dayIdx ->
            val list = byDay[dayIdx] ?: emptyList()
            if (list.isEmpty()) 0f
            else list.map { it.accuracy }.average().toFloat()
        }
        b.weeklyTrendChart.values = trendValues

        val weeklyXp = weeklySessions.sumOf { it.pointsEarned }
        b.tvWeeklyXp.text = "Toplam XP: $weeklyXp"

        val topApps = weeklyAttempts.entries
            .sortedByDescending { it.value }
            .take(3)
        val otherCount = weeklyAttempts.entries.drop(3).sumOf { it.value }
        val appBarData = buildList {
            topApps.forEach { (pkg, count) ->
                add(BarData(AppLabelResolver.getLabel(this@ReportsActivity, pkg).take(12), count, count.coerceAtLeast(1)))
            }
            if (otherCount > 0) {
                add(BarData("Diğer", otherCount, otherCount))
            }
        }
        b.appsBarChart.data = if (appBarData.isEmpty()) {
            listOf(BarData("-", 0, 1))
        } else appBarData

        val protectionPrefs = ProtectionPrefs(this)
        val lastWrongIds = protectionPrefs.lastFailedWrongIds()
        val lastSessionJson = protectionPrefs.lastFailedSessionJson()
        b.cardReviewWrong.visibility = if (lastWrongIds.isNotEmpty() && lastSessionJson.isNotEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        b.btnReviewWrongParent.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.quiz.WrongAnswerReviewActivity::class.java).apply {
                putStringArrayListExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_WRONG_IDS, ArrayList(lastWrongIds))
                putExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_SESSION_JSON, lastSessionJson)
                putExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_IS_PARENT_REVIEW, true)
            })
        }

        b.btnEmailSetup.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.ui.SettingsActivity::class.java))
        }

        b.btnShareReport.setOnClickListener {
            val passRate = if (perfs.isNotEmpty()) perfs.count { it.passed }.toFloat() / perfs.size * 100 else 0f
            val text = buildString {
                append("BrainBuddy Rapor\n")
                append("Günlük kilit: $dailyLockEvents | Engellenen: $totalBlockedCount\n")
                append("Testler (gün/hafta): $dailySessions / ${weeklySessions.size}\n")
                append("Doğruluk: %.0f%%\n".format(dailyAccuracy))
                append("Geçme oranı: %.0f%%".format(passRate))
            }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            startActivity(Intent.createChooser(send, getString(R.string.report_share)))
        }

        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
