package com.brainbuddy.app.core

import android.content.Context
import android.util.Log
import com.brainbuddy.app.db.RoomQuizDataStore
import com.brainbuddy.app.db.TestSnapshotEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
        val weeklyXp: Int
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
        val isEmpty: Boolean
    )

    data class TrendPoint(
        val index: Int,
        val testName: String,
        val dateMs: Long,
        val correct: Int,
        val total: Int,
        val percent: Float
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
        refreshTrigger.receiveAsFlow()
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

        // Aggregated counts from same filtered perfs
        val totalCorrect = perfs.sumOf { it.correctCount }
        val totalWrong = perfs.sumOf { it.wrongCount }
        val totalEmpty = perfs.sumOf { it.blankCount }
        val totalTests = perfs.size
        val totalBlocked = weeklyAttempts.values.sum()

        // SUCCESS FORMULA (strict): correct / (correct + wrong) * 100 — empty MUST NOT be in denominator
        val gradedTotal = totalCorrect + totalWrong
        val noGradedAnswers = gradedTotal == 0
        val weeklyAccuracy = if (gradedTotal > 0) 100f * totalCorrect / gradedTotal else 0f
        val passRate = if (perfs.isNotEmpty()) {
            perfs.count { it.passed }.toFloat() / perfs.size * 100f
        } else 0f

        // DEBUG: log aggregated stats for selected range
        Log.d(TAG_DEBUG, "Stats range=${range.label} totalCorrect=$totalCorrect totalWrong=$totalWrong totalEmpty=$totalEmpty testCount=$totalTests weeklyAccuracy=${weeklyAccuracy}%")

        val weeklySuccess = WeeklySuccessSection(
            correct = totalCorrect,
            wrong = totalWrong,
            blank = totalEmpty,
            total = totalCorrect + totalWrong + totalEmpty,
            accuracyPercent = weeklyAccuracy,
            testCount = totalTests,
            blockedCount = totalBlocked,
            passRatePercent = passRate,
            isEmpty = totalCorrect + totalWrong + totalEmpty == 0,
            noGradedAnswers = noGradedAnswers
        )

        // Topics: build from SAME filtered perfs (not all-time)
        val topicCounts = buildTopicCountsFromPerfs(perfs).filter { it.value.total > 0 }
        val topics = TopicsSection(
            topicCounts = topicCounts,
            isEmpty = topicCounts.isEmpty()
        )

        // Trend chart: same filtered perfs, take last 10, EXCLUDE tests with 0 correct + 0 wrong
        // SUCCESS FORMULA: percent = correct / (correct + wrong) * 100 — empty excluded
        val last10Perfs = perfs.takeLast(10).filter { it.correctCount + it.wrongCount > 0 }
        val trendPoints = last10Perfs.mapIndexed { i, p ->
            val graded = p.correctCount + p.wrongCount
            val percent = if (graded > 0) 100f * p.correctCount / graded else 0f
            TrendPoint(
                index = i + 1,
                testName = "Test ${last10Perfs.size - i}",
                dateMs = p.tsMs,
                correct = p.correctCount,
                total = graded,
                percent = percent
            )
        }
        val avgPct = if (trendPoints.isNotEmpty()) trendPoints.map { it.percent }.average().toFloat() else 0f
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
            isEmpty = trendPoints.size < 3
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

        return ReportsUiModel(
            range = range,
            weeklySuccess = weeklySuccess,
            topics = topics,
            trendChart = trendChart,
            recentTests = recentTests,
            topApps = topApps,
            wrongReview = wrongReview,
            weeklyXp = weeklyXp
        )
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
