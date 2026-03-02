package com.brainbuddy.app.core

import android.content.Context
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
        val isEmpty: Boolean
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

        // Scoped by account via ProfileScopedPrefs (analytics, reportStore, protectionPrefs)
        val perfs = analytics.getTestPerformances().filter { it.tsMs >= sinceMs }
        val sessions = analytics.getSessions().filter { it.tsMs >= sinceMs }
        val weeklyAttempts = reportStore.getBlockedAttemptsSince(sinceMs)

        val weeklyCorrect = perfs.sumOf { it.correctCount }
        val weeklyWrong = perfs.sumOf { it.wrongCount }
        val weeklyBlank = perfs.sumOf { it.blankCount }
        val weeklyTotal = weeklyCorrect + weeklyWrong + weeklyBlank
        val totalBlocked = weeklyAttempts.values.sum()

        val weeklyAccuracy = if (weeklyTotal > 0) 100f * weeklyCorrect / weeklyTotal else 0f
        val passRate = if (perfs.isNotEmpty()) {
            perfs.count { it.passed }.toFloat() / perfs.size * 100f
        } else 0f

        val weeklySuccess = WeeklySuccessSection(
            correct = weeklyCorrect,
            wrong = weeklyWrong,
            blank = weeklyBlank,
            total = weeklyTotal,
            accuracyPercent = weeklyAccuracy,
            testCount = perfs.size,
            blockedCount = totalBlocked,
            passRatePercent = passRate,
            isEmpty = weeklyTotal == 0
        )

        val topicCounts = analytics.getTopicMasteryWithCounts().filter { it.value.total > 0 }
        val topics = TopicsSection(
            topicCounts = topicCounts,
            isEmpty = topicCounts.isEmpty()
        )

        val last10Perfs = perfs.takeLast(10)
        val trendPoints = last10Perfs.mapIndexed { i, p ->
            val total = (p.correctCount + p.wrongCount + p.blankCount).coerceAtLeast(1)
            TrendPoint(
                index = i + 1,
                testName = "Test ${last10Perfs.size - i}",
                dateMs = p.tsMs,
                correct = p.correctCount,
                total = total,
                percent = if (total > 0) 100f * p.correctCount / total else 0f
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

    companion object {
        /** Meaning contracts for report widgets (metric, unit, date range, how to read) */
        object MeaningContracts {
            const val WEEKLY_SUCCESS = "Metrik: Doğru/Yanlış/Boş sayıları + Doğruluk % + Test sayısı. Aralık: Bugün/7g/30g. Her değer seçili aralıktaki toplamı gösterir."
            const val TOPICS = "Metrik: Konu bazlı doğru/toplam. Aralık: Tüm geçmiş. Her bar = bir konu, doğru/toplam oranı."
            const val TREND_CHART = "Metrik: Test başarı oranı (%). Aralık: Son 10 test (seçili filtreye göre). Y: 0–100%. Her nokta = 1 test. Başarı = doğru/(doğru+yanlış)."
            const val RECENT_TESTS = "Metrik: Son testler listesi. Aralık: Bugün/7g/30g. Her satır = bir tamamlanmış test."
            const val TOP_APPS = "Metrik: Deneme sayısı. Aralık: Bugün/7g/30g. Her satır = engellenen uygulama, kaç kez açılmaya çalışıldığı."
            const val EMPTY_MESSAGE = "Bu aralıkta veri yok. Test çözünce burada görünecek."
        }
    }
}
