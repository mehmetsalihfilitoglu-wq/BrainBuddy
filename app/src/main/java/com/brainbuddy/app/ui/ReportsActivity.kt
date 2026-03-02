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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.core.InstalledAppsHelper
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.StatsRepository
import com.brainbuddy.app.databinding.ActivityReportsBinding
import com.brainbuddy.app.quiz.PastTestDetailActivity
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.ui.BarChartView.BarData
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val android.content.Context.reportsPrefsDataStore by preferencesDataStore(name = "reports_prefs")
private val KEY_REPORT_RANGE_DAYS = intPreferencesKey("report_range_days")

class ReportsActivity : AppCompatActivity() {

    private lateinit var statsRepo: StatsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        try {
            val b = ActivityReportsBinding.inflate(layoutInflater)
            setContentView(b.root)
            setSupportActionBar(b.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)

            statsRepo = StatsRepository(this)

            val initialRange = runBlocking {
                applicationContext.reportsPrefsDataStore.data
                    .map { prefs -> prefs[KEY_REPORT_RANGE_DAYS] ?: 7 }
                    .first()
            }
            val rangeDays = initialRange.let { StatsRepository.ReportRange.fromDays(it) }

            statsRepo.setRangeDays(rangeDays.days)
            statsRepo.notifyScreenOpened()

            var shareData = ShareData(0, 0, 0f, 0f, rangeDays.days)

            statsRepo.reportsFlow
                .onEach { model -> applyModel(b, model, shareData = { shareData = it }) }
                .launchIn(lifecycleScope)

            fun onRangeChanged(days: Int) {
                statsRepo.setRangeDays(days)
                statsRepo.notifyScreenOpened()
                persistRange(days)
            }

            b.chipRangeToday.setOnClickListener { onRangeChanged(1) }
            b.chipRange7.setOnClickListener { onRangeChanged(7) }
            b.chipRange30.setOnClickListener { onRangeChanged(30) }

            b.btnEmailSetup.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            b.btnShareReport.setOnClickListener {
                val text = buildString {
                    append("BrainBuddy Rapor\n")
                    append("Engellenen: ${shareData.blocked}\n")
                    append("Testler (${shareData.rangeDays} gün): ${shareData.tests}\n")
                    append("Doğruluk: %.0f%%\n".format(shareData.accuracy))
                    append("Geçme oranı: %.0f%%".format(shareData.passRate))
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
            var root: Throwable = e
            while (root.cause != null) root = root.cause!!
            Log.e("ReportsActivity", "Crash", e)
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@ReportsActivity).apply {
                    text = "RAPORLAR CRASH: ${root.message}"
                    setPadding(48, 48, 48, 48)
                })
            }
            setContentView(layout)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::statsRepo.isInitialized) {
            statsRepo.notifyScreenOpened()
            statsRepo.notifyAccountChanged()
        }
    }

    private data class ShareData(var blocked: Int, var tests: Int, var accuracy: Float, var passRate: Float, var rangeDays: Int)

    private fun applyModel(
        b: ActivityReportsBinding,
        model: StatsRepository.ReportsUiModel,
        shareData: (ShareData) -> Unit
    ) {
        val range = model.range
        when (range) {
            StatsRepository.ReportRange.TODAY -> {
                b.tvWeeklySuccessTitle.text = "Günlük Özet"
                b.chipWeeklyRange.text = "Bugün"
            }
            StatsRepository.ReportRange.SEVEN -> {
                b.tvWeeklySuccessTitle.text = getString(R.string.parent_weekly_success)
                b.chipWeeklyRange.text = "Son 7 gün"
            }
            StatsRepository.ReportRange.THIRTY -> {
                b.tvWeeklySuccessTitle.text = "Aylık Özet"
                b.chipWeeklyRange.text = "Son 30 gün"
            }
        }

        b.chipGroupRange.check(
            when (range) {
                StatsRepository.ReportRange.TODAY -> b.chipRangeToday.id
                StatsRepository.ReportRange.SEVEN -> b.chipRange7.id
                StatsRepository.ReportRange.THIRTY -> b.chipRange30.id
            }
        )

        shareData(ShareData(
            model.weeklySuccess.blockedCount,
            model.weeklySuccess.testCount,
            model.weeklySuccess.accuracyPercent,
            model.weeklySuccess.passRatePercent,
            range.days
        ))

        // A) Weekly success
        val ws = model.weeklySuccess
        if (!ws.isEmpty) {
            b.weeklySuccessContent.visibility = View.VISIBLE
            b.weeklySuccessEmpty.visibility = View.GONE
            b.chipWeeklyCorrect.text = "Doğru: ${ws.correct}"
            b.chipWeeklyWrong.text = "Yanlış: ${ws.wrong}"
            b.chipWeeklyBlank.text = "Boş: ${ws.blank}"
            b.tvDailyTests.text = ws.testCount.toString()
            b.tvDailyAccuracy.text = "%.0f%%".format(ws.accuracyPercent)
            b.weeklyProgress.setProgressCompat(ws.accuracyPercent.roundToInt().coerceIn(0, 100), true)
            if (ws.blockedCount > 0) {
                b.blockedRow.visibility = View.VISIBLE
                b.tvDailyBlocked.text = ws.blockedCount.toString()
            } else {
                b.blockedRow.visibility = View.GONE
            }
        } else {
            b.weeklySuccessContent.visibility = View.GONE
            b.weeklySuccessEmpty.visibility = View.VISIBLE
            b.btnWeeklyEmptyCta?.setOnClickListener { startQuiz() }
        }

        // B) Topics
        if (!model.topics.isEmpty) {
            b.topicBarChart.visibility = View.VISIBLE
            b.topicsEmpty.visibility = View.GONE
            val topicData = model.topics.topicCounts.entries
                .sortedByDescending { it.value.correct }
                .take(10)
                .map { (topic, tc) -> BarData(topic, tc.correct, tc.total) }
            b.topicBarChart.data = topicData
            b.tvTopicsCaption?.text = getString(R.string.topics_caption)
        } else {
            b.topicBarChart.visibility = View.GONE
            b.topicsEmpty.visibility = View.VISIBLE
            b.btnTopicsEmptyCta?.setOnClickListener { startQuiz() }
        }

        // C) Trend chart
        val tc = model.trendChart
        if (!tc.isEmpty) {
            b.trendContent.visibility = View.VISIBLE
            b.trendEmpty.visibility = View.GONE
            val dateFormat = SimpleDateFormat("d MMM", Locale("tr"))
            b.weeklyTrendChart.data = tc.points.map { p ->
                LineChartView.PointData(
                    xLabel = (tc.points.indexOf(p) + 1).toString(),
                    percent = p.percent,
                    tooltipText = "${p.testName} • ${dateFormat.format(Date(p.dateMs))} • ${p.correct}/${p.total} • %.0f%%".format(p.percent)
                )
            }
            val trendArrow = when (tc.trendDirection) {
                1 -> "↑"
                -1 -> "↓"
                else -> "→"
            }
            b.tvTrendKpis?.text = "Ort: %.0f%% • Son: %.0f%% • $trendArrow".format(tc.averagePercent, tc.lastTestPercent)
            b.tvTrendCaption?.text = getString(R.string.trend_chart_caption)
            b.weeklyTrendChart.onPointTapped = { text -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
        } else {
            b.trendContent.visibility = View.GONE
            b.trendEmpty.visibility = View.VISIBLE
            b.btnTrendEmptyCta?.setOnClickListener { startQuiz() }
        }

        // D) Recent tests
        if (!model.recentTests.isEmpty) {
            b.recyclerRecentTests.visibility = View.VISIBLE
            b.recentTestsEmpty.visibility = View.GONE
            b.recyclerRecentTests.layoutManager = LinearLayoutManager(this)
            b.recyclerRecentTests.adapter = RecentTestsAdapter(model.recentTests.snapshots) { snapshot ->
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
            b.btnRecentTestsEmptyCta?.setOnClickListener { startQuiz() }
        }

        // E) Top apps
        val ta = model.topApps
        if (!ta.isEmpty) {
            b.recyclerTopApps.visibility = View.VISIBLE
            b.topAppsEmpty.visibility = View.GONE
            b.recyclerTopApps.layoutManager = LinearLayoutManager(this)
            b.recyclerTopApps.adapter = TopAppsAdapter(ta.items, ta.maxCount)
        } else {
            b.recyclerTopApps.visibility = View.GONE
            b.topAppsEmpty.visibility = View.VISIBLE
        }

        // Wrong review
        if (model.wrongReview.hasData) {
            b.wrongHasData.visibility = View.VISIBLE
            b.wrongEmpty.visibility = View.GONE
            b.btnReviewWrongParent?.setOnClickListener {
                startActivity(Intent(this, com.brainbuddy.app.quiz.WrongAnswerReviewActivity::class.java).apply {
                    putStringArrayListExtra(
                        com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_WRONG_IDS,
                        ArrayList(model.wrongReview.wrongIds)
                    )
                    putExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_SESSION_JSON, model.wrongReview.sessionJson)
                    putExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_IS_PARENT_REVIEW, true)
                })
            }
        } else {
            b.wrongHasData.visibility = View.GONE
            b.wrongEmpty.visibility = View.VISIBLE
            b.btnWrongEmptyCta?.setOnClickListener { startQuiz() }
        }

        b.tvWeeklyXp.text = "Toplam XP: ${model.weeklyXp}"
    }

    private fun persistRange(days: Int) {
        lifecycleScope.launch {
            runCatching {
                applicationContext.reportsPrefsDataStore.edit { prefs -> prefs[KEY_REPORT_RANGE_DAYS] = days }
            }
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
            holder.tvTitle.text = holder.itemView.context.getString(R.string.recent_test_title_format, position + 1)
            holder.tvDate.text = dateFormat.format(Date(s.createdAt))
            holder.tvScore.text = "${s.score}/${s.total}"
            holder.itemView.setOnClickListener { onItemClick(s) }
        }

        override fun getItemCount() = snapshots.size
    }

    private class TopAppsAdapter(
        private val items: List<Pair<String, Int>>,
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
            return VH(LayoutInflater.from(parent.context).inflate(R.layout.item_top_app, parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (pkg, count) = items[position]
            val ctx = holder.itemView.context
            holder.tvAppName.text = AppLabelResolver.getLabel(ctx, pkg)
            holder.tvAttemptCount.text = count.toString()
            holder.ivIcon.setImageDrawable(InstalledAppsHelper.getAppIcon(ctx.packageManager, pkg, ctx))
            val safeMax = if (maxCount <= 0) 1 else maxCount
            holder.progress.setProgressCompat((count * 100f / safeMax).roundToInt().coerceIn(0, 100), false)
        }

        override fun getItemCount() = items.size
    }
}
