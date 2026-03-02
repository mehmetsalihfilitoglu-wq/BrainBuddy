package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ReportStore
import com.brainbuddy.app.databinding.ActivityReportsBinding
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.RoomQuizDataStore
import com.brainbuddy.app.quiz.PastTestDetailActivity
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.ui.BarChartView.BarData
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

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
        val protectionPrefs = ProtectionPrefs(this)
        val dataStore = RoomQuizDataStore(this)
        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)

        val weeklyAttempts = reportStore.getBlockedAttemptsSince(weekAgo)
        val totalBlockedCount = weeklyAttempts.values.sum()

        val perfs = analytics.getTestPerformances().filter { it.tsMs >= weekAgo }
        val weeklySessions = analytics.getSessions().filter { it.tsMs >= weekAgo }
        val weeklyCorrect = perfs.sumOf { it.correctCount }
        val weeklyWrong = perfs.sumOf { it.wrongCount }
        val weeklyBlank = perfs.sumOf { it.blankCount }
        val weeklyTotal = weeklyCorrect + weeklyWrong + weeklyBlank
        val weeklyAccuracy = if (weeklyTotal > 0) 100f * weeklyCorrect / weeklyTotal else 0f

        // A) Haftalık Başarı
        if (weeklyTotal > 0) {
            b.weeklySuccessContent.visibility = View.VISIBLE
            b.weeklySuccessEmpty.visibility = View.GONE
            b.tvWeeklyCorrectWrongBlank.text = "Doğru $weeklyCorrect / Yanlış $weeklyWrong / Boş $weeklyBlank"
            b.chipWeeklyCorrect.text = "Doğru: $weeklyCorrect"
            b.chipWeeklyWrong.text = "Yanlış: $weeklyWrong"
            b.chipWeeklyBlank.text = "Boş: $weeklyBlank"
            b.tvDailyTests.text = perfs.size.toString()
            b.tvDailyAccuracy.text = "%.0f%%".format(weeklyAccuracy)
            b.weeklyProgress.setProgressCompat(weeklyAccuracy.roundToInt().coerceIn(0, 100), true)
            if (totalBlockedCount > 0) {
                b.blockedRow.visibility = View.VISIBLE
                b.tvDailyBlocked.text = totalBlockedCount.toString()
            } else {
                b.blockedRow.visibility = View.GONE
            }
        } else {
            b.weeklySuccessContent.visibility = View.GONE
            b.weeklySuccessEmpty.visibility = View.VISIBLE
            b.btnWeeklyEmptyCta.setOnClickListener { startQuiz() }
        }

        // B) Konulara Göre
        val topicCounts = analytics.getTopicMasteryWithCounts().filter { it.value.total > 0 }
        if (topicCounts.isNotEmpty()) {
            b.topicBarChart.visibility = View.VISIBLE
            b.topicsEmpty.visibility = View.GONE
            val topicData = topicCounts.entries
                .sortedByDescending { it.value.correct }
                .take(10)
                .map { (topic, tc) -> BarData(topic, tc.correct, tc.total) }
            b.topicBarChart.data = topicData
        } else {
            b.topicBarChart.visibility = View.GONE
            b.topicsEmpty.visibility = View.VISIBLE
            b.btnTopicsEmptyCta.setOnClickListener { startQuiz() }
        }

        // C) Son 10 Test Başarı Trendi
        val last10Perfs = analytics.getTestPerformances().takeLast(10)
        val trendAccuracies = last10Perfs.map { it.accuracy }
        if (trendAccuracies.size >= 3) {
            b.weeklyTrendChart.visibility = View.VISIBLE
            b.trendEmpty.visibility = View.GONE
            b.weeklyTrendChart.values = trendAccuracies
        } else {
            b.weeklyTrendChart.visibility = View.GONE
            b.trendEmpty.visibility = View.VISIBLE
            b.btnTrendEmptyCta.setOnClickListener { startQuiz() }
        }

        // D) Son Testler
        val snapshots = kotlinx.coroutines.runBlocking {
            dataStore.getLastSnapshots(10)
        }
        if (snapshots.isNotEmpty()) {
            b.recyclerRecentTests.visibility = View.VISIBLE
            b.recentTestsEmpty.visibility = View.GONE
            b.recyclerRecentTests.layoutManager = LinearLayoutManager(this)
            b.recyclerRecentTests.adapter = RecentTestsAdapter(snapshots) { snapshot ->
                val qIds = try {
                    if (snapshot.questionIdsJson.isNullOrBlank()) emptyList()
                    else (0 until JSONArray(snapshot.questionIdsJson).length()).map {
                        JSONArray(snapshot.questionIdsJson).getString(it)
                    }
                } catch (_: Exception) { emptyList() }
                startActivity(Intent(this, PastTestDetailActivity::class.java).apply {
                    putExtra(PastTestDetailActivity.EXTRA_TEST_ID, snapshot.testId)
                    putStringArrayListExtra(PastTestDetailActivity.EXTRA_QUESTION_IDS, ArrayList(qIds))
                })
            }
        } else {
            b.recyclerRecentTests.visibility = View.GONE
            b.recentTestsEmpty.visibility = View.VISIBLE
            b.btnRecentTestsEmptyCta.setOnClickListener { startQuiz() }
        }

        // E) Yanlış Cevapları İncele
        val lastWrongIds = protectionPrefs.lastFailedWrongIds()
        val lastSessionJson = protectionPrefs.lastFailedSessionJson()
        if (lastWrongIds.isNotEmpty() && lastSessionJson.isNotEmpty()) {
            b.wrongHasData.visibility = View.VISIBLE
            b.wrongEmpty.visibility = View.GONE
            b.btnReviewWrongParent.setOnClickListener {
                startActivity(Intent(this, com.brainbuddy.app.quiz.WrongAnswerReviewActivity::class.java).apply {
                    putStringArrayListExtra(
                        com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_WRONG_IDS,
                        ArrayList(lastWrongIds)
                    )
                    putExtra(
                        com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_SESSION_JSON,
                        lastSessionJson
                    )
                    putExtra(
                        com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_IS_PARENT_REVIEW,
                        true
                    )
                })
            }
        } else {
            b.wrongHasData.visibility = View.GONE
            b.wrongEmpty.visibility = View.VISIBLE
            b.btnWrongEmptyCta.setOnClickListener { startQuiz() }
        }

        // En çok denenen uygulamalar
        val topApps = weeklyAttempts.entries.sortedByDescending { it.value }.take(3)
        val otherCount = weeklyAttempts.entries.drop(3).sumOf { it.value }
        val appBarData = buildList {
            topApps.forEach { (pkg, count) ->
                add(BarData(AppLabelResolver.getLabel(this@ReportsActivity, pkg), count, count.coerceAtLeast(1)))
            }
            if (otherCount > 0) add(BarData("Diğer", otherCount, otherCount))
        }
        b.appsBarChart.data = if (appBarData.isEmpty()) listOf(BarData("-", 0, 1)) else appBarData

        val weeklyXp = weeklySessions.sumOf { it.pointsEarned }
        b.tvWeeklyXp.text = "Toplam XP: $weeklyXp"

        b.btnEmailSetup.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.btnShareReport.setOnClickListener {
            val passRate = if (perfs.isNotEmpty()) perfs.count { it.passed }.toFloat() / perfs.size * 100 else 0f
            val text = buildString {
                append("BrainBuddy Rapor\n")
                append("Engellenen: $totalBlockedCount\n")
                append("Testler (hafta): ${perfs.size}\n")
                append("Doğruluk: %.0f%%\n".format(weeklyAccuracy))
                append("Geçme oranı: %.0f%%".format(passRate))
            }
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    },
                    getString(R.string.report_share)
                )
            )
        }

        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun startQuiz() {
        startActivity(Intent(this, QuizActivity::class.java))
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private class RecentTestsAdapter(
        private val snapshots: List<com.brainbuddy.app.db.TestSnapshotEntity>,
        private val onItemClick: (com.brainbuddy.app.db.TestSnapshotEntity) -> Unit
    ) : RecyclerView.Adapter<RecentTestsAdapter.VH>() {

        private val dateFormat = SimpleDateFormat("d MMM yyyy", Locale("tr"))

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(R.id.tvTestDate)
            val tvScore: TextView = view.findViewById(R.id.tvTestScore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_test, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = snapshots[position]
            holder.tvDate.text = dateFormat.format(Date(s.createdAt))
            holder.tvScore.text = "${s.score}/${s.total}"
            holder.itemView.setOnClickListener { onItemClick(s) }
        }

        override fun getItemCount() = snapshots.size
    }
}
