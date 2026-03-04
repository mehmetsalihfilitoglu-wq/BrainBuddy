package com.brainbuddy.app.core

import android.content.Context
import android.util.Log
import com.brainbuddy.app.db.RoomQuizDataStore
import com.brainbuddy.app.db.TestSnapshotEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Single source of truth for reports UI.
 * Exposes Flow<ReportsUiModel> recomputed when:
 * - quiz session saved
 * - blocked app event recorded
 * - account (active profile) changed
 * - screen opened (onResume) — caller triggers refresh
 */
class StatsRepository(private val context: Context) {

    private val analytics = AnalyticsStore(context)
    private val reportStore = ReportStore(context)
    private val protectionPrefs = ProtectionPrefs(context)
    private val dataStore = RoomQuizDataStore(context)
    private val packageManager get() = context.packageManager

    /** Triggers recompute. Call after quiz save, blocked event, account switch, or onResume. */
    private val refreshTrigger = Channel<Unit>(Channel.CONFLATED)

    private val rangeDaysFlow = MutableStateFlow(ReportRange.SEVEN.days)
    private val activeProfileFlow = MutableStateFlow(ActiveProfileManager.getActiveProfileId(context))

    /** Emit when something changes that affects reports. */
    fun notifyQuizSaved() { refreshTrigger.trySend(Unit) }
    fun notifyBlockedAppEvent() { refreshTrigger.trySend(Unit) }
    fun notifyAccountChanged() {
        activeProfileFlow.value = ActiveProfileManager.getActiveProfileId(context)
        refreshTrigger.trySend(Unit)
    }
    fun notifyScreenOpened() { refreshTrigger.trySend(Unit) }

    fun setRangeDays(days: Int) { rangeDaysFlow.value = days }

    /**
     * Device local midnight (00:00:00) for the given timestamp's day.
     */
    fun getLocalMidnightMs(tsMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = tsMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getSinceMsForRange(days: Int): Long {
        val now = System.currentTimeMillis()
        val todayStart = getLocalMidnightMs(now)
        return when (days) {
            1 -> todayStart
            else -> todayStart - TimeUnit.DAYS.toMillis(days.toLong() - 1)
        }
    }

    /**
     * Reports UI model. All data scoped by active profile and selected date range.
     */
    data class ReportsUiModel(
        val range: ReportRange,
        val weeklySuccess: WeeklySuccessSection,
        val topics: TopicsSection,
        val trendChart: TrendChartSection,
        val recentTests: RecentTestsSection,
        val topApps: TopAppsSection,
        val wrongReview: WrongReviewSection,
        val weeklyXp: Int,
        val advancedStats: AdvancedStatsUiModel
    )

    data class WeeklySuccessSection(
        val correct: Int,
        val wrong: Int,
        val blank: Int,
        val total: Int,
        val accuracyPercent: Float,
        val testCount: Int,
        val blockedCount: Int,
        val passRatePercent: Float,
        val isEmpty: Boolean,
        /** True when (correct + wrong) == 0 — no graded answers to compute accuracy from. */
        val noGradedAnswers: Boolean = false
    )

    data class TopicsSection(
        val topicCounts: Map<String, TopicCounts>,
        val isEmpty: Boolean
    )

    data class TrendChartSection(
        val points: List<TrendPoint>,
        val averagePercent: Float,
        val lastTestPercent: Float,
        val trendDirection: Int, // -1 down, 0 same, 1 up
        val isEmpty: Boolean,
        /** Count of tests in range with (correct+wrong)==0 (excluded from chart). Show "En az 1 soru cevaplanmalı" when > 0. */
        val excludedEmptyTestsCount: Int = 0
    )

    data class TrendPoint(
        val index: Int,
        val testName: String,
        val dateMs: Long,
        val correct: Int,
        val wrong: Int,
        val blank: Int,
        val total: Int, // graded = correct + wrong
        val percent: Float,
        /** Quiz ID for navigation to PastTestDetailActivity. Empty if unavailable. */
        val testId: String = "",
        /** Question IDs for PastTestDetailActivity EXTRA_QUESTION_IDS. Empty if unavailable. */
        val questionIds: List<String> = emptyList()
    )

    data class RecentTestsSection(
        val snapshots: List<TestSnapshotEntity>,
        val isEmpty: Boolean
    )

    data class TopAppsSection(
        val items: List<Pair<String, Int>>,
        val maxCount: Int,
        val isEmpty: Boolean
    )

    data class WrongReviewSection(
        val hasData: Boolean,
        val wrongIds: List<String>,
        val sessionJson: String
    )

    /**
     * Advanced performance analysis: weak/strong subjects, trend, most wrong topic.
     * All scoped by active profile and selected date range (Today/7d/30d).
     */
    data class AdvancedStatsUiModel(
        val weakSubjects: List<SubjectStat>,
        val strongSubjects: List<SubjectStat>,
        val trendDelta: Float,
        val trendDirection: TrendDirection,
        val mostWrongTopic: String?,
        /** Smart mini-test recommendation based on risk score. Null when no weak subject. */
        val smartRecommendation: SmartRecommendation?
    )

    /**
     * Smart recommendation for mini test: subject, dynamic message, and whether to show.
     */
    data class SmartRecommendation(
        val subject: String,
        val message: String,
        val subjectTr: String
    )

    data class SubjectStat(val name: String, val successPercent: Float)

    enum class TrendDirection {
        IMPROVING,
        DECLINING,
        STABLE
    }

    enum class ReportRange(val days: Int, val label: String) {
        TODAY(1, "Bugün"),
        SEVEN(7, "7 Gün"),
        THIRTY(30, "30 Gün");

        companion object {
            fun fromDays(days: Int): ReportRange = when (days) {
                1 -> TODAY
                30 -> THIRTY
                else -> SEVEN
            }
        }
    }

    private val triggerFlow = merge(
        flowOf(Unit),
        refreshTrigger.receiveAsFlow(),
        globalQuizSaved
    )

    val reportsFlow: Flow<ReportsUiModel> = combine(
        rangeDaysFlow,
        activeProfileFlow,
        triggerFlow
    ) { rangeDays, _, _ -> rangeDays }
        .map { rangeDays -> computeReports(rangeDays) }

    private fun computeReports(rangeDays: Int): ReportsUiModel {
        val range = ReportRange.fromDays(rangeDays)
        val accountId = ActiveProfileManager.getActiveProfileId(context)
        val sinceMs = getSinceMsForRange(range.days)

        // Single filtered list: test.date within local time boundaries (Bugün/7 Gün/30 Gün)
        val perfs = analytics.getTestPerformances().filter { it.tsMs >= sinceMs }
        val sessions = analytics.getSessions().filter { it.tsMs >= sinceMs }
        val weeklyAttempts = reportStore.getBlockedAttemptsSince(sinceMs)

        // COMPLETE TESTS ONLY: exclude incomplete tests (correctCount + wrongCount == 0)
        // Use this SAME filtered list for both weekly summary and trend chart
        val completePerfs = perfs.filter { it.correctCount + it.wrongCount > 0 }

        // Aggregated counts from complete tests only
        val totalCorrect = completePerfs.sumOf { it.correctCount }
        val totalWrong = completePerfs.sumOf { it.wrongCount }
        val totalEmpty = completePerfs.sumOf { it.blankCount }
        val totalTests = completePerfs.size
        val totalBlocked = weeklyAttempts.values.sum()

        // SUCCESS FORMULA (strict): correct / (correct + wrong) * 100 — empty MUST NOT be in denominator
        val gradedTotal = totalCorrect + totalWrong
        val noGradedAnswers = gradedTotal == 0
        val weeklyAccuracy = if (gradedTotal > 0) 100f * totalCorrect / gradedTotal else 0f
        val passRate = if (completePerfs.isNotEmpty()) {
            completePerfs.count { it.passed }.toFloat() / completePerfs.size * 100f
        } else 0f

        // DEBUG: log aggregated stats for selected range
        Log.d(TAG_DEBUG, "Stats range=${range.label} completeTests=${completePerfs.size} totalCorrect=$totalCorrect totalWrong=$totalWrong totalEmpty=$totalEmpty weeklyAccuracy=${weeklyAccuracy}%")

        val weeklySuccess = WeeklySuccessSection(
            correct = totalCorrect,
            wrong = totalWrong,
            blank = totalEmpty,
            total = totalCorrect + totalWrong + totalEmpty,
            accuracyPercent = weeklyAccuracy,
            testCount = totalTests,
            blockedCount = totalBlocked,
            passRatePercent = passRate,
            isEmpty = gradedTotal + totalEmpty == 0,
            noGradedAnswers = noGradedAnswers
        )

        // Topics: build from complete perfs (same filtered list)
        val topicCounts = buildTopicCountsFromPerfs(completePerfs).filter { it.value.total > 0 }
        val topics = TopicsSection(
            topicCounts = topicCounts,
            isEmpty = topicCounts.isEmpty()
        )

        // Trend chart: same completePerfs, take last 10 (already excludes incomplete tests)
        // SUCCESS FORMULA: percent = correct / (correct + wrong) * 100 — empty excluded
        val excludedEmptyTestsCount = perfs.count { it.correctCount + it.wrongCount == 0 }
        val last10Perfs = completePerfs.takeLast(10)
        val trendPoints = last10Perfs.mapIndexed { i, p ->
            val graded = p.correctCount + p.wrongCount
            // Defensive: only compute percent when graded > 0 (already guaranteed by completePerfs)
            val percent = if (graded > 0) 100f * p.correctCount / graded else 0f
            TrendPoint(
                index = i + 1,
                testName = "Test ${last10Perfs.size - i}",
                dateMs = p.tsMs,
                correct = p.correctCount,
                wrong = p.wrongCount,
                blank = p.blankCount,
                total = graded,
                percent = percent,
                testId = p.quizId,
                questionIds = p.questionIds
            )
        }
        // Recalculate averagePercent: sum(correct) / sum(correct + wrong), not average of per-test %
        val sumCorrect = trendPoints.sumOf { it.correct }
        val sumGraded = trendPoints.sumOf { it.total }
        val avgPct = if (sumGraded > 0) 100f * sumCorrect / sumGraded else 0f
        val lastPct = trendPoints.lastOrNull()?.percent ?: 0f
        val prevPct = trendPoints.dropLast(1).lastOrNull()?.percent ?: lastPct
        val trendDir = when {
            trendPoints.size < 2 -> 0
            lastPct > prevPct -> 1
            lastPct < prevPct -> -1
            else -> 0
        }
        val trendChart = TrendChartSection(
            points = trendPoints.reversed(),
            averagePercent = avgPct,
            lastTestPercent = lastPct,
            trendDirection = trendDir,
            isEmpty = trendPoints.size < 3,
            excludedEmptyTestsCount = excludedEmptyTestsCount
        )

        val snapshots = try {
            dataStore.getLastSnapshots(accountId, 50)
        } catch (_: Throwable) { emptyList() }
        val filteredSnapshots = snapshots
            .filter { it.createdAt >= sinceMs }
            .sortedByDescending { it.createdAt }
            .take(10)
        val recentTests = RecentTestsSection(
            snapshots = filteredSnapshots,
            isEmpty = filteredSnapshots.isEmpty()
        )

        val installedPkgs = InstalledAppsHelper.getInstalledApps(packageManager).map { it.packageName }.toSet()
        val topItems = weeklyAttempts.entries
            .filter { it.key in installedPkgs }
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }
        val topApps = TopAppsSection(
            items = topItems,
            maxCount = topItems.maxOfOrNull { it.second } ?: 1,
            isEmpty = topItems.isEmpty()
        )

        val wrongIds = try { protectionPrefs.lastFailedWrongIds() } catch (_: Throwable) { emptyList() }
        val sessionJson = try { protectionPrefs.lastFailedSessionJson() } catch (_: Throwable) { "" }
        val wrongReview = WrongReviewSection(
            hasData = wrongIds.isNotEmpty() && sessionJson.isNotEmpty(),
            wrongIds = wrongIds,
            sessionJson = sessionJson
        )

        val weeklyXp = sessions.sumOf { it.pointsEarned }

        val advancedStats = computeAdvancedStats(completePerfs, topicCounts)

        return ReportsUiModel(
            range = range,
            weeklySuccess = weeklySuccess,
            topics = topics,
            trendChart = trendChart,
            recentTests = recentTests,
            topApps = topApps,
            wrongReview = wrongReview,
            weeklyXp = weeklyXp,
            advancedStats = advancedStats
        )
    }

    private fun computeAdvancedStats(
        completePerfs: List<TestPerformance>,
        topicCounts: Map<String, TopicCounts>
    ): AdvancedStatsUiModel {
        // 1) Weak/Strong Subjects: group by subject (topic key), totalQuestions >= 10
        //    successPercent = correct / (correct+wrong)
        val subjectAgg = mutableMapOf<String, Triple<Int, Int, Int>>() // subject -> (correct, wrong, total)
        completePerfs.forEach { p ->
            p.byTopicCounts.forEach { (subject, tc) ->
                val existing = subjectAgg.getOrPut(subject) { Triple(0, 0, 0) }
                subjectAgg[subject] = Triple(
                    existing.first + tc.correct,
                    existing.second + tc.wrong,
                    existing.third + tc.total
                )
            }
        }
        val weakSubjects = subjectAgg
            .map { (name, t) ->
                val (correct, wrong, total) = t
                val graded = correct + wrong
                val successPercent = if (graded > 0) 100f * correct / graded else 0f
                SubjectStat(name, successPercent) to total
            }
            // Spec: "Zayıf ders(ler)" (<=55% ve en az 10 cevap)
            .filter { it.second >= 10 && it.first.successPercent <= 55f }
            .map { it.first }
            .sortedBy { it.successPercent }
        val strongSubjects = subjectAgg
            .map { (name, t) ->
                val (correct, wrong, total) = t
                val graded = correct + wrong
                val successPercent = if (graded > 0) 100f * correct / graded else 0f
                SubjectStat(name, successPercent) to total
            }
            // Spec: "Güçlü ders(ler)" (>=80% ve en az 10 cevap)
            .filter { it.second >= 10 && it.first.successPercent >= 80f }
            .map { it.first }
            .sortedByDescending { it.successPercent }
        // 3) Trend Engine: last 6 completed tests
        val last6 = completePerfs.takeLast(6)
        val trendDelta: Float
        val trendDirection: TrendDirection
        if (last6.size >= 6) {
            val percents = last6.map { p ->
                val graded = p.correctCount + p.wrongCount
                if (graded > 0) 100f * p.correctCount / graded else 0f
            }
            val previous3Avg = percents.take(3).average().toFloat()
            val last3Avg = percents.takeLast(3).average().toFloat()
            trendDelta = last3Avg - previous3Avg
            trendDirection = when {
                trendDelta > 0 -> TrendDirection.IMPROVING
                trendDelta < 0 -> TrendDirection.DECLINING
                else -> TrendDirection.STABLE
            }
        } else {
            trendDelta = 0f
            trendDirection = TrendDirection.STABLE
        }
        // 4) Most Wrong Topic
        val mostWrongTopic = topicCounts.entries
            .filter { it.value.wrong > 0 }
            .maxByOrNull { it.value.wrong }
            ?.key

        // 5) Smart Recommendation Engine
        val smartRecommendation = computeSmartRecommendation(completePerfs, topicCounts)

        return AdvancedStatsUiModel(
            weakSubjects = weakSubjects,
            strongSubjects = strongSubjects,
            trendDelta = trendDelta,
            trendDirection = trendDirection,
            mostWrongTopic = mostWrongTopic,
            smartRecommendation = smartRecommendation
        )
    }

    /**
     * Smart Recommendation Engine: pick subject with highest risk score, generate dynamic message.
     * Returns null when no weak subject (hide recommendation card).
     */
    private fun computeSmartRecommendation(
        completePerfs: List<TestPerformance>,
        topicCounts: Map<String, TopicCounts>
    ): SmartRecommendation? {
        if (topicCounts.isEmpty()) return null

        val totalWrongAll = topicCounts.values.sumOf { it.wrong }.toFloat().coerceAtLeast(1f)

        // Per-subject: (tsMs, successRate) for tests that include this subject
        val subjectTestHistory = mutableMapOf<String, MutableList<Pair<Long, Float>>>()
        completePerfs.forEach { p ->
            p.byTopicCounts.forEach { (subject, tc) ->
                val graded = tc.correct + tc.wrong
                if (graded > 0) {
                    val successRate = 100f * tc.correct / graded
                    subjectTestHistory.getOrPut(subject) { mutableListOf() }
                        .add(p.tsMs to successRate)
                }
            }
        }

        data class SubjectRisk(val subject: String, val successRate: Float, val trend: Float, val riskScore: Float)

        val subjectData = topicCounts.entries.mapNotNull { (subject, tc) ->
            val correct = tc.correct
            val wrong = tc.wrong
            val graded = correct + wrong
            if (graded < 3) return@mapNotNull null

            val successRate = 100f * correct / graded
            val history = subjectTestHistory[subject]?.sortedBy { it.first } ?: return@mapNotNull null

            val percents = history.map { it.second }
            val last3Avg = percents.takeLast(3).average().toFloat()
            val previous3Avg = if (percents.size >= 6) {
                percents.dropLast(3).takeLast(3).average().toFloat()
            } else {
                last3Avg
            }
            val trend = last3Avg - previous3Avg
            val wrongCountRatio = (wrong / totalWrongAll).coerceIn(0f, 1f)
            val riskScore = (100 - successRate) * 0.6f +
                kotlin.math.abs(kotlin.math.min(trend, 0f)) * 0.3f +
                wrongCountRatio * 0.1f

            SubjectRisk(subject, successRate, trend, riskScore)
        }.maxByOrNull { it.riskScore } ?: return null

        val subject = subjectData.subject
        val successRate = subjectData.successRate
        val trend = subjectData.trend

        val isWeak = trend < 0 || successRate < 60f
        if (!isWeak) return null

        val message = when {
            trend < 0 -> "Son testlerde düşüş var. $subject için mini test önerilir."
            successRate < 60f -> "$subject başarı oranı düşük. Güçlendirme önerilir."
            else -> "$subject performansı stabil."
        }

        return SmartRecommendation(subject = subject, message = message, subjectTr = subject)
    }

    private fun buildTopicCountsFromPerfs(perfs: List<TestPerformance>): Map<String, TopicCounts> {
        val agg = mutableMapOf<String, MutableList<TopicCounts>>()
        perfs.forEach { p ->
            p.byTopicCounts.forEach { (topic, tc) ->
                agg.getOrPut(topic) { mutableListOf() }.add(tc)
            }
        }
        return agg.mapValues { (_, list) ->
            TopicCounts(
                correct = list.sumOf { it.correct },
                wrong = list.sumOf { it.wrong },
                blank = list.sumOf { it.blank },
                total = list.sumOf { it.total }
            )
        }
    }

    companion object {
        private const val TAG_DEBUG = "StatsRepository"
        /** Shared trigger so Reports refresh when quiz saved from another screen (e.g. QuizResultActivity). */
        private val globalQuizSaved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        @JvmStatic fun notifyQuizSavedGlobal() { globalQuizSaved.tryEmit(Unit) }
        /** Meaning contracts for report widgets (metric, unit, date range, how to read) */
        object MeaningContracts {
            const val WEEKLY_SUCCESS = "Metrik: Doğru/Yanlış/Boş sayıları + Doğruluk % + Test sayısı. Aralık: Bugün/7g/30g. Her değer seçili aralıktaki toplamı gösterir."
            const val TOPICS = "Metrik: Konu bazlı doğru/toplam. Aralık: Bugün/7g/30g (aynı filtre). Her bar = bir konu, doğru/toplam oranı."
            const val TREND_CHART = "Metrik: Test başarı oranı (%). Aralık: Son 10 test (seçili filtreye göre). Y: 0–100%. Her nokta = 1 test. Başarı = doğru/(doğru+yanlış)."
            const val RECENT_TESTS = "Metrik: Son testler listesi. Aralık: Bugün/7g/30g. Her satır = bir tamamlanmış test."
            const val TOP_APPS = "Metrik: Deneme sayısı. Aralık: Bugün/7g/30g. Her satır = engellenen uygulama, kaç kez açılmaya çalışıldığı."
            const val EMPTY_MESSAGE = "Bu aralıkta veri yok. Test çözünce burada görünecek."
        }
    }
}
