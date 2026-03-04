package com.brainbuddy.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.core.graphics.drawable.DrawableCompat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.BuildConfig
import com.brainbuddy.app.R
import com.brainbuddy.app.core.InstalledAppsHelper
import com.brainbuddy.app.core.ParentAccessGuard
import com.brainbuddy.app.core.StatsRepository
import com.brainbuddy.app.core.WrongReviewAccessManager
import com.brainbuddy.app.databinding.ActivityReportsBinding
import androidx.core.content.FileProvider
import com.brainbuddy.app.ads.RewardAdHelper
import com.brainbuddy.app.quiz.PastTestDetailActivity
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.report.PdfReportGenerator
import com.brainbuddy.app.ui.BarChartView.BarData
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val android.content.Context.reportsPrefsDataStore by preferencesDataStore(name = "reports_prefs")
private val KEY_REPORT_RANGE_DAYS = intPreferencesKey("report_range_days")

class ReportsActivity : AppCompatActivity() {

    private lateinit var statsRepo: StatsRepository

    /** Last selected point index (0-based) in trend chart. Null = collapsed. Survives applyModel/rotation. */
    private var selectedPointIndex: Int? = null
    private var wrongReviewOpenedOnce: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        try {
            val b = ActivityReportsBinding.inflate(layoutInflater)
            setContentView(b.root)
            val scroll = b.reportsMainScroll
            if (scroll is ScrollView) scroll.isVerticalScrollBarEnabled = false
            else if (scroll is androidx.core.widget.NestedScrollView) scroll.isVerticalScrollBarEnabled = false
            scroll.overScrollMode = View.OVER_SCROLL_NEVER
            setSupportActionBar(b.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)

            statsRepo = StatsRepository(this)
            wrongReviewAccessManager = WrongReviewAccessManager(this)
            val autoOpenWrongReview = intent.getBooleanExtra(EXTRA_OPEN_WRONG_REVIEW, false)

            val initialRange = runBlocking {
                applicationContext.reportsPrefsDataStore.data
                    .map { prefs -> prefs[KEY_REPORT_RANGE_DAYS] ?: 7 }
                    .first()
            }
            val rangeDays = initialRange.let { StatsRepository.ReportRange.fromDays(it) }

            statsRepo.setRangeDays(rangeDays.days)
            statsRepo.notifyScreenOpened()

            var shareData = ShareData(0, 0, 0f, 0f, rangeDays.days)
            var latestModel: StatsRepository.ReportsUiModel? = null

            statsRepo.reportsFlow
                .onEach { model ->
                    latestModel = model
                    applyModel(
                        b,
                        model,
                        shareData = { shareData = it },
                        autoOpenWrongReview = autoOpenWrongReview
                    )
                }
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
                startActivity(Intent(this, EmailReportsSetupActivity::class.java))
            }

            b.btnShareReport.setOnClickListener {
                val model = latestModel
                if (model != null) {
                    if (model.weeklySuccess.testCount == 0) {
                        Toast.makeText(this@ReportsActivity, R.string.report_no_data, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    b.btnShareReport.isEnabled = false
                    b.progressShareReport?.visibility = View.VISIBLE
                    lifecycleScope.launch {
                        val file = try {
                            PdfReportGenerator.generateAndGetFile(this@ReportsActivity, model)
                        } catch (e: Exception) {
                            Log.e("PDF_REPORT", "Report generation failed", e)
                            withContext(Dispatchers.IO) {
                                try {
                                    File(cacheDir, PdfReportGenerator.PDF_ERROR_FILENAME)
                                        .writeText("${e.message}\n\n${e.stackTraceToString()}")
                                } catch (_: Exception) { }
                            }
                            null
                        }
                        b.btnShareReport.isEnabled = true
                        b.progressShareReport?.visibility = View.GONE
                        if (file != null && file.exists() && file.length() > 500) {
                            try {
                            val authority = "${BuildConfig.APPLICATION_ID}.provider"
                            val uri = FileProvider.getUriForFile(this@ReportsActivity, authority, file)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(
                                Intent.createChooser(shareIntent, getString(R.string.report_share))
                            )
                            } catch (e: Exception) {
                                Log.e("PDF_REPORT", "Share failed", e)
                                showPdfErrorSnackbar(getString(R.string.report_pdf_error))
                            }
                        } else {
                            showPdfErrorDialogIfAvailable()
                            showPdfErrorSnackbar(getString(R.string.report_pdf_error))
                        }
                    }
                } else {
                    val accuracyStr = if (shareData.noGradedAnswers) "—" else "%.0f%%".format(shareData.accuracy)
                    val text = buildString {
                        append("BrainBuddy Rapor\n")
                        append("Engellenen: ${shareData.blocked}\n")
                        append("Testler (${shareData.rangeDays} gün): ${shareData.tests}\n")
                        append("Doğruluk: $accuracyStr\n")
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
            }

            b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

            b.btnPerformanceInfo.setOnClickListener { showPerformanceInfoBottomSheet() }

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

    private data class ShareData(var blocked: Int, var tests: Int, var accuracy: Float, var passRate: Float, var rangeDays: Int, var noGradedAnswers: Boolean = false)

    private fun applyModel(
        b: ActivityReportsBinding,
        model: StatsRepository.ReportsUiModel,
        shareData: (ShareData) -> Unit,
        autoOpenWrongReview: Boolean
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
            range.days,
            model.weeklySuccess.noGradedAnswers
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
            b.tvDailyAccuracy.text = when {
                ws.noGradedAnswers -> "—"
                else -> "%.0f%%".format(ws.accuracyPercent)
            }
            b.weeklyProgress.setProgressCompat(
                if (ws.noGradedAnswers) 0 else ws.accuracyPercent.roundToInt().coerceIn(0, 100),
                true
            )
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

        // B2) Performans Analizi
        val adv = model.advancedStats
        // Spec: Trend = son 3 test vs önceki 3 test → en az 6 tamamlanmış test yoksa "Yeterli veri yok"
        val hasAdvancedData = model.weeklySuccess.testCount >= 6
        if (hasAdvancedData) {
            b.cardAdvancedStats.visibility = View.VISIBLE
            b.advancedStatsContent.visibility = View.VISIBLE
            b.advancedStatsEmpty.visibility = View.GONE

            // A) Weak subjects – chips
            val weakSubjects = adv.weakSubjects.take(3)
            bindSubjectChips(b.flowWeakChips, weakSubjects, isWeak = true)
            val hasWeak = weakSubjects.isNotEmpty()
            b.tvWeakLabel.visibility = if (hasWeak) View.VISIBLE else View.GONE
            b.flowWeakChips.visibility = if (hasWeak) View.VISIBLE else View.GONE
            b.tvWeakNone.visibility = View.GONE

            // B) Strong subjects – chips
            val strongSubjects = adv.strongSubjects.take(3)
            bindSubjectChips(b.flowStrongChips, strongSubjects, isWeak = false)
            val hasStrong = strongSubjects.isNotEmpty()
            b.tvStrongLabel.visibility = if (hasStrong) View.VISIBLE else View.GONE
            b.flowStrongChips.visibility = if (hasStrong) View.VISIBLE else View.GONE
            b.tvStrongNone.visibility = View.GONE

            // C) Trend
            val (trendText, trendDrawable) = when (adv.trendDirection) {
                StatsRepository.TrendDirection.IMPROVING -> Pair(
                    getString(R.string.perf_trend_improving).let {
                        "Son 3 test: +%d%% ↑ ($it)".format(adv.trendDelta.roundToInt())
                    },
                    R.drawable.ic_trend_up
                )
                StatsRepository.TrendDirection.DECLINING -> Pair(
                    getString(R.string.perf_trend_declining).let {
                        "Son 3 test: %d%% ↓ ($it)".format(adv.trendDelta.roundToInt())
                    },
                    R.drawable.ic_trend_down
                )
                else -> Pair(
                    "Son 3 test: ±0% → (${getString(R.string.perf_trend_stable)})",
                    R.drawable.ic_trend_stable
                )
            }
            b.tvTrendSummary.text = trendText
            b.ivTrendArrow.setImageResource(trendDrawable)
            b.ivTrendArrow.setColorFilter(getColor(R.color.emerald_primary))

            // D) Smart Recommendation (hidden when no weak subject)
            val rec = adv.smartRecommendation
            if (rec != null) {
                b.sectionRecommendation.visibility = View.VISIBLE
                b.tvMostWrongTopic.text = rec.message
                b.btnMiniTestSuggest.setOnClickListener {
                    startQuizWithSubjectFilter(rec.subjectTr)
                }
            } else {
                b.sectionRecommendation.visibility = View.GONE
            }
        } else {
            b.cardAdvancedStats.visibility = View.GONE
        }

        // C) Trend chart
        val tc = model.trendChart
        if (!tc.isEmpty) {
            b.trendContent.visibility = View.VISIBLE
            b.trendEmpty.visibility = View.GONE
            val dateFormat = SimpleDateFormat("d MMM", Locale("tr"))
            b.weeklyTrendChart.data = tc.points.map { p ->
                val successFormula = if (p.total > 0) "${p.correct}/(${p.correct}+${p.wrong}) = %.0f%%".format(p.percent)
                else "—"
                LineChartView.PointData(
                    xLabel = (tc.points.indexOf(p) + 1).toString(),
                    percent = p.percent,
                    tooltipText = "${p.testName} • ${dateFormat.format(Date(p.dateMs))}\n" +
                        "Doğru: ${p.correct} / Yanlış: ${p.wrong} / Boş: ${p.blank}\n" +
                        "Başarı: $successFormula"
                )
            }
            val trendArrow = when (tc.trendDirection) {
                1 -> "↑"
                -1 -> "↓"
                else -> "→"
            }
            b.tvTrendKpis?.text = "Ort: %.0f%% • Son: %.0f%% • $trendArrow".format(tc.averagePercent, tc.lastTestPercent)
            b.tvTrendCaption?.text = getString(R.string.trend_chart_caption)
            b.tvTrendEmptyWarning?.visibility = if (tc.excludedEmptyTestsCount > 0) View.VISIBLE else View.GONE

            // Selected point detail card (no tooltip overlay)
            val cardBinding = b.selectedTestDetailCard
            val cardRoot = cardBinding.root

            fun updateDetailCard(point: StatsRepository.TrendPoint) {
                cardBinding.tvDetailTitle.text = "${point.testName} • ${dateFormat.format(Date(point.dateMs))}"
                cardBinding.tvDetailMetrics.text = "Doğru: ${point.correct}  •  Yanlış: ${point.wrong}  •  Boş: ${point.blank}"
                val successPercent = point.percent
                val overallAverage = tc.averagePercent
                val isAboveOrEqual = successPercent >= overallAverage
                val tintColor = if (isAboveOrEqual) getColor(R.color.emerald_primary) else getColor(R.color.bb_error)
                cardBinding.tvDetailSuccess.text = "${successPercent.roundToInt()}%"
                cardBinding.tvDetailSuccess.setTextColor(tintColor)
                val arrowRes = if (isAboveOrEqual) R.drawable.ic_trend_up else R.drawable.ic_trend_down
                val arrow = androidx.core.content.ContextCompat.getDrawable(this@ReportsActivity, arrowRes)?.mutate()
                arrow?.let {
                    DrawableCompat.setTint(it, tintColor)
                    val sizePx = (14 * resources.displayMetrics.density).toInt()
                    it.setBounds(0, 0, sizePx, sizePx)
                }
                cardBinding.tvDetailSuccess.setCompoundDrawables(null, null, arrow, null)
                val canNavigate = point.testId.isNotBlank() && point.questionIds.isNotEmpty()
                cardBinding.btnDetailGoToTest.visibility = if (canNavigate) View.VISIBLE else View.GONE
                cardBinding.btnDetailGoToTest.setOnClickListener {
                    if (canNavigate) {
                        startActivity(Intent(this@ReportsActivity, PastTestDetailActivity::class.java).apply {
                            putExtra(PastTestDetailActivity.EXTRA_TEST_ID, point.testId)
                            putStringArrayListExtra(PastTestDetailActivity.EXTRA_QUESTION_IDS, ArrayList(point.questionIds))
                        })
                    }
                }
            }

            fun showDetailCard(index: Int) {
                if (index !in tc.points.indices) return
                val wasVisible = cardRoot.visibility == View.VISIBLE
                val previousIndex = selectedPointIndex
                selectedPointIndex = index
                val point = tc.points[index]
                b.weeklyTrendChart.selectedIndex = index
                if (wasVisible && previousIndex != null && previousIndex != index) {
                    cardRoot.animate()
                        .alpha(0.4f)
                        .setDuration(60)
                        .withEndAction {
                            updateDetailCard(point)
                            cardRoot.animate()
                                .alpha(1f)
                                .setDuration(120)
                                .setInterpolator(AccelerateDecelerateInterpolator())
                                .start()
                        }
                        .start()
                } else {
                    updateDetailCard(point)
                    if (!wasVisible) {
                        cardRoot.visibility = View.VISIBLE
                        cardRoot.alpha = 0f
                        cardRoot.translationY = 12f * resources.displayMetrics.density
                        cardRoot.animate()
                            .alpha(1f)
                            .translationY(0f)
                            .setDuration(120)
                            .setInterpolator(AccelerateDecelerateInterpolator())
                            .start()
                    }
                }
            }

            fun hideDetailCard() {
                selectedPointIndex = null
                b.weeklyTrendChart.selectedIndex = null
                if (cardRoot.visibility == View.VISIBLE) {
                    val slidePx = 16f * resources.displayMetrics.density
                    cardRoot.animate()
                        .alpha(0f)
                        .translationY(slidePx)
                        .setDuration(180)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            cardRoot.visibility = View.GONE
                            cardRoot.translationY = 0f
                        }
                        .start()
                }
            }

            b.weeklyTrendChart.onPointSelected = { index ->
                showDetailCard(index)
            }
            b.weeklyTrendChart.onEmptyAreaTapped = { hideDetailCard() }
            cardBinding.btnCloseDetailCard.setOnClickListener { hideDetailCard() }

            // Restore or clear selection after model apply (rotation / onResume)
            val validIndex = selectedPointIndex?.takeIf { it in tc.points.indices }
            if (validIndex != null) {
                showDetailCard(validIndex)
            } else {
                selectedPointIndex = null
                b.weeklyTrendChart.selectedIndex = null
                cardRoot.visibility = View.GONE
            }
        } else {
            selectedPointIndex = null
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
            wrongReviewAccessManager.ensureDailyReset()
            val remaining = wrongReviewAccessManager.getRemaining()
            val isPremium = com.brainbuddy.app.core.PremiumStore(this).isPremium()
            b.chipWrongReviewQuota?.let { chip ->
                chip.visibility = if (isPremium) View.GONE else View.VISIBLE
                if (!isPremium) {
                    chip.text = getString(R.string.wrong_review_quota_chip, remaining)
                }
            }
            b.tvWrongReviewPremiumUpsell?.visibility = if (isPremium) View.GONE else View.VISIBLE
            b.btnReviewWrongParent?.setOnClickListener {
                openWrongAnswerReview(model.wrongReview.wrongIds, model.wrongReview.sessionJson)
            }
            if (autoOpenWrongReview && !wrongReviewOpenedOnce) {
                wrongReviewOpenedOnce = true
                openWrongAnswerReview(model.wrongReview.wrongIds, model.wrongReview.sessionJson)
            }
        } else {
            b.wrongHasData.visibility = View.GONE
            b.wrongEmpty.visibility = View.VISIBLE
            b.btnWrongEmptyCta?.setOnClickListener { startQuiz() }
        }

        b.tvWeeklyXp.text = "Toplam XP: ${model.weeklyXp}"
    }

    private fun bindSubjectChips(
        container: LinearLayout,
        subjects: List<StatsRepository.SubjectStat>,
        isWeak: Boolean
    ) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val iconRes = if (isWeak) R.drawable.ic_warning_amber else R.drawable.ic_check_circle
        for (stat in subjects) {
            val chip = inflater.inflate(R.layout.item_subject_chip, container, false) as LinearLayout
            val iv = chip.findViewById<ImageView>(R.id.ivChipIcon)
            val tv = chip.findViewById<TextView>(R.id.tvChipText)
            iv.setImageResource(iconRes)
            iv.setColorFilter(getColor(R.color.emerald_primary))
            tv.text = "${stat.name} ${stat.successPercent.roundToInt()}%"
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.marginEnd = resources.getDimensionPixelSize(R.dimen.space_8)
            chip.layoutParams = lp
            container.addView(chip)
        }
    }

    private fun showPerformanceInfoBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        bottomSheet.setContentView(R.layout.bottom_sheet_performance_info)
        bottomSheet.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnDismiss)
            ?.setOnClickListener { bottomSheet.dismiss() }
        bottomSheet.show()
    }

    private fun startQuizWithSubjectFilter(subjectTr: String) {
        startActivity(Intent(this, QuizActivity::class.java).apply {
            putExtra(QuizActivity.EXTRA_SUBJECT_FILTER, subjectTr)
        })
    }

    private fun showPdfErrorDialogIfAvailable() {
        val errorFile = File(cacheDir, PdfReportGenerator.PDF_ERROR_FILENAME)
        val content = runCatching { errorFile.readText() }.getOrNull()
        if (!content.isNullOrBlank()) {
            val lines = content.lines()
            val message = lines.firstOrNull().orEmpty()
            val stackTrace = lines.drop(1).joinToString("\n").trim()
            val tracePreview = stackTrace.lines().take(25).joinToString("\n")
            val dialogMessage = if (tracePreview.isNotBlank()) "$message\n\n$tracePreview" else message
            val authority = "${BuildConfig.APPLICATION_ID}.provider"
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.report_pdf_error_title))
                .setMessage(dialogMessage)
                .setPositiveButton(getString(R.string.report_pdf_error_copy)) { _, _ ->
                    val cm = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    cm?.setPrimaryClip(ClipData.newPlainText("PDF error", content))
                }
                .setNeutralButton(getString(R.string.report_pdf_error_share_file)) { _, _ ->
                    if (errorFile.exists()) {
                        val uri = FileProvider.getUriForFile(this, authority, errorFile)
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        startActivity(Intent.createChooser(shareIntent, getString(R.string.report_share)))
                    }
                }
                .setNegativeButton(getString(R.string.close), null)
                .show()
        } else {
            showPdfErrorSnackbar(getString(R.string.report_pdf_error))
        }
    }

    private fun showPdfErrorSnackbar(message: String) {
        val root = findViewById<View>(android.R.id.content)
        Snackbar.make(root, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(R.color.emerald_dark))
            .setTextColor(getColor(android.R.color.white))
            .setAction(getString(R.string.report_pdf_error_copy)) {
                val errorFile = File(cacheDir, PdfReportGenerator.PDF_ERROR_FILENAME)
                val content = runCatching { errorFile.readText() }.getOrNull() ?: message
                (getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                    ?.setPrimaryClip(ClipData.newPlainText("PDF error", content))
            }
            .show()
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

    private lateinit var wrongReviewAccessManager: WrongReviewAccessManager
    private var rewardAdHelper: RewardAdHelper? = null

    private fun openWrongAnswerReview(wrongIds: List<String>, sessionJson: String?) {
        if (!::wrongReviewAccessManager.isInitialized) wrongReviewAccessManager = WrongReviewAccessManager(this)
        wrongReviewAccessManager.ensureDailyReset()
        if (wrongReviewAccessManager.canOpen() && wrongReviewAccessManager.consumeOpen()) {
            startWrongAnswersListActivity(wrongIds, sessionJson)
            return
        }
        showWrongReviewPaywallDialog(wrongIds, sessionJson)
    }

    private fun startWrongAnswersListActivity(wrongIds: List<String>, sessionJson: String?) {
        startActivity(Intent(this, com.brainbuddy.app.quiz.WrongAnswersListActivity::class.java).apply {
            putStringArrayListExtra(com.brainbuddy.app.quiz.WrongAnswersListActivity.EXTRA_WRONG_IDS, ArrayList(wrongIds))
            putExtra(com.brainbuddy.app.quiz.WrongAnswersListActivity.EXTRA_SESSION_JSON, sessionJson)
        })
    }

    private fun showWrongReviewPaywallDialog(wrongIds: List<String>, sessionJson: String?) {
        val b = AlertDialog.Builder(this)
            .setTitle(getString(R.string.wrong_review_limit_title))
            .setMessage(getString(R.string.wrong_review_limit_message))
            .setNegativeButton(getString(R.string.close)) { d, _ -> d.dismiss() }
            .setNeutralButton(getString(R.string.wrong_review_btn_premium)) { _, _ ->
                startActivity(Intent(this, TestSettingsActivity::class.java))
            }
        if (rewardAdHelper == null) rewardAdHelper = RewardAdHelper(this)
        rewardAdHelper?.loadAd()
        if (rewardAdHelper?.isLoaded() == true) {
            b.setPositiveButton(getString(R.string.wrong_review_btn_watch_ad)) { d, _ ->
                d.dismiss()
                showRewardedAdForWrongReview { startWrongAnswersListActivity(wrongIds, sessionJson) }
            }
        } else {
            b.setPositiveButton(getString(R.string.wrong_review_btn_watch_ad)) { d, _ ->
                d.dismiss()
                Toast.makeText(this, getString(R.string.wrong_review_ad_failed), Toast.LENGTH_SHORT).show()
                rewardAdHelper?.loadAd()
            }
        }
        b.show()
    }

    private fun showRewardedAdForWrongReview(onRewarded: () -> Unit) {
        if (rewardAdHelper == null) rewardAdHelper = RewardAdHelper(this)
        rewardAdHelper?.showAd(
            onRewarded = {
                wrongReviewAccessManager.addOneFromReward()
                if (wrongReviewAccessManager.consumeOpen()) onRewarded()
                rewardAdHelper?.loadAd()
            },
            onFailed = {
                Toast.makeText(this, getString(R.string.wrong_review_ad_failed), Toast.LENGTH_SHORT).show()
                rewardAdHelper?.loadAd()
            }
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_OPEN_WRONG_REVIEW: String = "extra_open_wrong_review"
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
