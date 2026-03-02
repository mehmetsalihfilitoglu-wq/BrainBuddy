package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.lang.reflect.InvocationTargetException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

private val android.content.Context.reportsPrefsDataStore by preferencesDataStore(name = "reports_prefs")
private val KEY_REPORT_RANGE_DAYS = intPreferencesKey("report_range_days")

private enum class ReportRange(val days: Int) {
    TODAY(1),
    SEVEN(7),
    THIRTY(30);

    companion object {
        fun fromDays(days: Int): ReportRange = when (days) {
            1 -> TODAY
            30 -> THIRTY
            else -> SEVEN
        }
    }
}

class ReportsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        try {
            val b = ActivityReportsBinding.inflate(layoutInflater)
            setContentView(b.root)
            setSupportActionBar(b.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)

            val reportStore = ReportStore(this)
        val analytics = AnalyticsStore(this)
        val protectionPrefs = ProtectionPrefs(this)
        val dataStore = RoomQuizDataStore(this)

        val initialRange = try {
            kotlinx.coroutines.runBlocking {
                applicationContext.reportsPrefsDataStore.data
                    .map { prefs: Preferences ->
                        prefs[KEY_REPORT_RANGE_DAYS] ?: ReportRange.SEVEN.days
                    }
                    .first()
            }
        } catch (_: Throwable) { ReportRange.SEVEN.days }.let { ReportRange.fromDays(it) }

        var currentRange = initialRange
        var shareBlockedCount = 0
        var shareTestsCount = 0
        var shareWeeklyAccuracy = 0f
        var sharePassRate = 0f

        fun updateWeeklyHeaderForRange(range: ReportRange) {
            when (range) {
                ReportRange.TODAY -> {
                    b.tvWeeklySuccessTitle.text = "Günlük Özet"
                    b.chipWeeklyRange.text = "Bugün"
                }
                ReportRange.SEVEN -> {
                    b.tvWeeklySuccessTitle.text = getString(R.string.parent_weekly_success)
                    b.chipWeeklyRange.text = "Son 7 gün"
                }
                ReportRange.THIRTY -> {
                    b.tvWeeklySuccessTitle.text = "Aylık Özet"
                    b.chipWeeklyRange.text = "Son 30 gün"
                }
            }
        }

        fun applyRange(range: ReportRange) {
            currentRange = range

            b.chipGroupRange.check(
                when (range) {
                    ReportRange.TODAY -> b.chipRangeToday.id
                    ReportRange.SEVEN -> b.chipRange7.id
                    ReportRange.THIRTY -> b.chipRange30.id
                }
            )

            val now = System.currentTimeMillis()
            val sinceMs = now - TimeUnit.DAYS.toMillis(range.days.toLong())

            val weeklyAttempts = reportStore.getBlockedAttemptsSince(sinceMs)
            val totalBlockedCount = weeklyAttempts.values.sum()

            val perfs = analytics.getTestPerformances().filter { it.tsMs >= sinceMs }
            val weeklySessions = analytics.getSessions().filter { it.tsMs >= sinceMs }
            val weeklyCorrect = perfs.sumOf { it.correctCount }
            val weeklyWrong = perfs.sumOf { it.wrongCount }
            val weeklyBlank = perfs.sumOf { it.blankCount }
            val weeklyTotal = weeklyCorrect + weeklyWrong + weeklyBlank
            val weeklyAccuracy = if (weeklyTotal > 0) 100f * weeklyCorrect / weeklyTotal else 0f

            shareBlockedCount = totalBlockedCount
            shareTestsCount = perfs.size
            shareWeeklyAccuracy = weeklyAccuracy
            sharePassRate = if (perfs.isNotEmpty()) {
                perfs.count { it.passed }.toFloat() / perfs.size * 100f
            } else {
                0f
            }

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

            // C) Son 10 Test Başarı Trendi (premium chart + empty state)
            val last10Perfs = perfs.takeLast(10)
            val trendAccuracies = last10Perfs.map { it.accuracy }
            if (trendAccuracies.size >= 3) {
                b.trendContent.visibility = View.VISIBLE
                b.trendEmpty.visibility = View.GONE
                b.weeklyTrendChart.values = trendAccuracies
            } else {
                b.trendContent.visibility = View.GONE
                b.trendEmpty.visibility = View.VISIBLE
                b.btnTrendEmptyCta.setOnClickListener { startQuiz() }
            }

            // D) Son Testler
            val snapshots = try {
                kotlinx.coroutines.runBlocking { dataStore.getLastSnapshots(50) }
            } catch (_: Throwable) { emptyList() }
            val filteredSnapshots = snapshots
                .filter { it.createdAt >= sinceMs }
                .sortedByDescending { it.createdAt }
                .take(10)

            if (filteredSnapshots.isNotEmpty()) {
                b.recyclerRecentTests.visibility = View.VISIBLE
                b.recentTestsEmpty.visibility = View.GONE
                b.recyclerRecentTests.layoutManager = LinearLayoutManager(this)
                b.recyclerRecentTests.adapter = RecentTestsAdapter(filteredSnapshots) { snapshot ->
                    val qIds = try {
                        if (snapshot.questionIdsJson.isNullOrBlank()) emptyList()
                        else (0 until JSONArray(snapshot.questionIdsJson).length()).map {
                            JSONArray(snapshot.questionIdsJson).getString(it)
                        }
                    } catch (_: Exception) { emptyList() }
                    startActivity(Intent(this, PastTestDetailActivity::class.java).apply {
                        putExtra(PastTestDetailActivity.EXTRA_TEST_ID, snapshot.testId)
                        putStringArrayListExtra(
                            PastTestDetailActivity.EXTRA_QUESTION_IDS,
                            ArrayList(qIds)
                        )
                    })
                }
            } else {
                b.recyclerRecentTests.visibility = View.GONE
                b.recentTestsEmpty.visibility = View.VISIBLE
                b.btnRecentTestsEmptyCta.setOnClickListener { startQuiz() }
            }

            // En çok denenen uygulamalar
            val topApps = weeklyAttempts.entries.sortedByDescending { it.value }.take(10)
            if (topApps.isNotEmpty()) {
                b.recyclerTopApps.visibility = View.VISIBLE
                b.topAppsEmpty.visibility = View.GONE
                b.recyclerTopApps.layoutManager = LinearLayoutManager(this)
                b.recyclerTopApps.adapter =
                    TopAppsAdapter(topApps.toList(), weeklyAttempts.values.maxOrNull() ?: 1)
            } else {
                b.recyclerTopApps.visibility = View.GONE
                b.topAppsEmpty.visibility = View.VISIBLE
            }

            // Haftalık XP (seçili aralığa göre)
            val weeklyXp = weeklySessions.sumOf { it.pointsEarned }
            b.tvWeeklyXp.text = "Toplam XP: $weeklyXp"
        }

        fun persistRange(range: ReportRange) {
            try {
                kotlinx.coroutines.runBlocking {
                    applicationContext.reportsPrefsDataStore.edit { prefs ->
                        prefs[KEY_REPORT_RANGE_DAYS] = range.days
                    }
                }
            } catch (_: Throwable) { /* ignore DataStore write failure */ }
        }

        fun onRangeChanged(range: ReportRange) {
            updateWeeklyHeaderForRange(range)
            applyRange(range)
            persistRange(range)
        }

        b.chipRangeToday.setOnClickListener {
            if (currentRange != ReportRange.TODAY) onRangeChanged(ReportRange.TODAY)
        }
        b.chipRange7.setOnClickListener {
            if (currentRange != ReportRange.SEVEN) onRangeChanged(ReportRange.SEVEN)
        }
        b.chipRange30.setOnClickListener {
            if (currentRange != ReportRange.THIRTY) onRangeChanged(ReportRange.THIRTY)
        }

        updateWeeklyHeaderForRange(initialRange)
        applyRange(initialRange)

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

        // E) Yanlış Cevapları İncele
        val lastWrongIds = try {
            protectionPrefs.lastFailedWrongIds()
        } catch (_: Throwable) { emptyList() }
        val lastSessionJson = try {
            protectionPrefs.lastFailedSessionJson()
        } catch (_: Throwable) { "" }
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

        b.btnEmailSetup.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.btnShareReport.setOnClickListener {
            val text = buildString {
                append("BrainBuddy Rapor\n")
                append("Engellenen: $shareBlockedCount\n")
                append("Testler (${currentRange.days} gün): $shareTestsCount\n")
                append("Doğruluk: %.0f%%\n".format(shareWeeklyAccuracy))
                append("Geçme oranı: %.0f%%".format(sharePassRate))
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

        } catch (e: Throwable) {
            val root = when (e) {
                is InvocationTargetException -> (e.targetException ?: e.cause) ?: e
                else -> e
            }
            var cause: Throwable = root
            while (cause.cause != null) cause = cause.cause!!
            val stackTrace = cause.stackTraceToString().lines().take(15).joinToString("\n") { "  $it" }
            val details = "Class: ${cause.javaClass.name}\nMessage: ${cause.message}\n\n$stackTrace"
            Log.e("ReportsActivity", details, cause)
            val titleTv = TextView(this).apply {
                text = "RAPORLAR CRASH"
                textSize = 22f
                setPadding(48, 48, 48, 24)
            }
            val bodyTv = TextView(this).apply {
                text = details
                textSize = 12f
                setPadding(48, 24, 48, 48)
            }
            val scroll = ScrollView(this).apply { addView(bodyTv) }
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(titleTv)
                addView(scroll)
            }
            setContentView(layout)
        }
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
            val tvTitle: TextView = view.findViewById(R.id.tvTestTitle)
            val tvDate: TextView = view.findViewById(R.id.tvTestDate)
            val tvScore: TextView = view.findViewById(R.id.tvTestScore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_test, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val s = snapshots[position]
            val context = holder.itemView.context
            holder.tvTitle.text = context.getString(R.string.recent_test_title_format, position + 1)
            holder.tvDate.text = dateFormat.format(Date(s.createdAt))
            holder.tvScore.text = "${s.score}/${s.total}"
            holder.itemView.setOnClickListener { onItemClick(s) }
        }

        override fun getItemCount() = snapshots.size
    }

    private class TopAppsAdapter(
        private val items: List<Map.Entry<String, Int>>,
        private val maxCount: Int
    ) : RecyclerView.Adapter<TopAppsAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
            val tvAppName: TextView = view.findViewById(R.id.tvAppName)
            val tvAttemptCount: TextView = view.findViewById(R.id.tvAttemptCount)
            val progress: com.google.android.material.progressindicator.LinearProgressIndicator =
                view.findViewById(R.id.progressUsage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_top_app, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (pkg, count) = items[position]
            val context = holder.itemView.context
            val label = AppLabelResolver.getLabel(context, pkg)
            holder.tvAppName.text = label
            holder.tvAttemptCount.text = count.toString()

            // Icon: try load app icon, could be improved with actual PackageManager call elsewhere
            holder.ivIcon.setImageDrawable(context.packageManager.getApplicationIcon(pkg))

            val safeMax = if (maxCount <= 0) 1 else maxCount
            val percent = (count * 100f / safeMax).roundToInt().coerceIn(0, 100)
            holder.progress.setProgressCompat(percent, false)
        }

        override fun getItemCount(): Int = items.size
    }
}
