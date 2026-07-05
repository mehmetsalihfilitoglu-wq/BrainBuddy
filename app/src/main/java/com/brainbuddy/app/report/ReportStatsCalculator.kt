package com.brainbuddy.app.report

import android.content.Context
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.InstalledAppsHelper
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ReportStore
import com.brainbuddy.app.core.TopicCounts
import com.brainbuddy.app.db.RoomQuizDataStore
import com.brainbuddy.app.quiz.Question
import com.brainbuddy.app.quiz.QuestionRepository
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Single source of truth for report statistics.
 * Computes all report data for a given range and profile.
 * Unit-testable: pass data in for pure computation; or use context for full flow.
 */
object ReportStatsCalculator {

    enum class Range(val days: Int, val labelTr: String) {
        TODAY(1, "Bugün"),
        DAYS_7(7, "Son 7 Gün"),
        DAYS_30(30, "Son 30 Gün");

        companion object {
            fun fromDays(days: Int): Range = when (days) {
                1 -> TODAY
                30 -> DAYS_30
                else -> DAYS_7
            }
        }
    }

    data class Totals(
        val correct: Int,
        val wrong: Int,
        val blank: Int,
        val testCount: Int,
        val blockedCount: Int,
        val accuracyPercent: Float,
        val passRatePercent: Float,
        val noGradedAnswers: Boolean,
        val totalXp: Int
    )

    data class LastTestPoint(
        val index: Int,
        val testName: String,
        val dateMs: Long,
        val correct: Int,
        val wrong: Int,
        val blank: Int,
        val percent: Float,
        val testId: String,
        val questionIds: List<String>
    )

    data class WrongAnswerRecord(
        val subject: String,
        val stem: String,
        val choices: List<String>,
        val userAnswer: String,
        val correctAnswer: String,
        val category: String?,
        val dateMs: Long,
        val testId: String
    )

    data class ReportResult(
        val range: Range,
        val profileId: String,
        val profileName: String,
        val totals: Totals,
        val perSubject: Map<String, TopicCounts>,
        val lastTests: List<LastTestPoint>,
        val wrongAnswers: List<WrongAnswerRecord>,
        val attemptedApps: List<Pair<String, Int>>,
        val trendDelta: Float,
        val trendDirection: Int,
        val excludedEmptyTestsCount: Int
    ) {
        val hasCharts: Boolean get() = lastTests.size >= 2
        val isEmpty: Boolean get() = totals.testCount == 0 && wrongAnswers.isEmpty()
    }

    fun getLocalMidnightMs(tsMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = tsMs
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getSinceMsForRange(days: Int): Long {
        val now = System.currentTimeMillis()
        val todayStart = getLocalMidnightMs(now)
        return when (days) {
            1 -> todayStart
            else -> todayStart - TimeUnit.DAYS.toMillis((days - 1).toLong())
        }
    }

    /**
     * Full computation for report. Uses Context for data access.
     */
    fun computeForRange(context: Context, range: Range): ReportResult {
        val profileId = ActiveProfileManager.getActiveProfileId(context)
        val profileName = ProfileStore(context).getProfile(profileId)?.name ?: "Öğrenci"
        return computeForRange(context, range, profileId, profileName)
    }

    fun computeForRange(context: Context, range: Range, profileId: String, profileName: String): ReportResult {
        val sinceMs = getSinceMsForRange(range.days)
        val analytics = AnalyticsStore(context)
        val reportStore = ReportStore(context)
        val packageManager = context.packageManager

        val allPerfs = analytics.getTestPerformances().filter { it.tsMs >= sinceMs }
        val completePerfs = allPerfs.filter { it.correctCount + it.wrongCount > 0 }
        val excludedEmpty = allPerfs.count { it.correctCount + it.wrongCount == 0 }

        val totalCorrect = completePerfs.sumOf { it.correctCount }
        val totalWrong = completePerfs.sumOf { it.wrongCount }
        val totalBlank = completePerfs.sumOf { it.blankCount }
        val gradedTotal = totalCorrect + totalWrong
        val noGradedAnswers = gradedTotal == 0
        val accuracy = if (gradedTotal > 0) 100f * totalCorrect / gradedTotal else 0f
        val passRate = if (completePerfs.isNotEmpty()) {
            completePerfs.count { it.passed }.toFloat() / completePerfs.size * 100f
        } else 0f

        val attempts = reportStore.getBlockedAttemptsSince(sinceMs)
        val installedPkgs = InstalledAppsHelper.getInstalledApps(packageManager).map { it.packageName }.toSet()
        val attemptedApps = attempts.entries
            .filter { it.key in installedPkgs }
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key to it.value }

        val topicCounts = mutableMapOf<String, MutableList<TopicCounts>>()
        completePerfs.forEach { p ->
            p.byTopicCounts.forEach { (topic, tc) ->
                topicCounts.getOrPut(topic) { mutableListOf() }.add(tc)
            }
        }
        val perSubject = topicCounts.mapValues { (_, list) ->
            TopicCounts(
                correct = list.sumOf { it.correct },
                wrong = list.sumOf { it.wrong },
                blank = list.sumOf { it.blank },
                total = list.sumOf { it.total }
            )
        }.filter { it.value.total > 0 }

        val last10Perfs = completePerfs.takeLast(10)
        val lastTests = last10Perfs.mapIndexed { i, p ->
            val graded = p.correctCount + p.wrongCount
            val percent = if (graded > 0) 100f * p.correctCount / graded else 0f
            LastTestPoint(
                index = i + 1,
                testName = "Test ${last10Perfs.size - i}",
                dateMs = p.tsMs,
                correct = p.correctCount,
                wrong = p.wrongCount,
                blank = p.blankCount,
                percent = percent,
                testId = p.quizId,
                questionIds = p.questionIds
            )
        }.reversed()

        val lastPct = lastTests.lastOrNull()?.percent ?: 0f
        val prevPct = lastTests.dropLast(1).lastOrNull()?.percent ?: lastPct
        val trendDir = when {
            lastTests.size < 2 -> 0
            lastPct > prevPct -> 1
            lastPct < prevPct -> -1
            else -> 0
        }
        val last6 = completePerfs.takeLast(6)
        val trendDelta = if (last6.size >= 6) {
            val percents = last6.map { p ->
                val g = p.correctCount + p.wrongCount
                if (g > 0) 100f * p.correctCount / g else 0f
            }
            percents.takeLast(3).average().toFloat() - percents.take(3).average().toFloat()
        } else 0f

        val sessions = analytics.getSessions().filter { it.tsMs >= sinceMs }
        val totalXp = sessions.sumOf { it.pointsEarned }

        val wrongAnswers = buildWrongAnswers(context, profileId, sinceMs)

        return ReportResult(
            range = range,
            profileId = profileId,
            profileName = profileName,
            totals = Totals(
                correct = totalCorrect,
                wrong = totalWrong,
                blank = totalBlank,
                testCount = completePerfs.size,
                blockedCount = attempts.values.sum(),
                accuracyPercent = accuracy,
                passRatePercent = passRate,
                noGradedAnswers = noGradedAnswers,
                totalXp = totalXp
            ),
            perSubject = perSubject,
            lastTests = lastTests,
            wrongAnswers = wrongAnswers,
            attemptedApps = attemptedApps,
            trendDelta = trendDelta,
            trendDirection = trendDir,
            excludedEmptyTestsCount = excludedEmpty
        )
    }

    private fun buildWrongAnswers(context: Context, profileId: String, sinceMs: Long): List<WrongAnswerRecord> {
        val dataStore = RoomQuizDataStore(context)
        val repo = QuestionRepository(context)
        val snapshots = dataStore.getLastSnapshots(profileId, 100)
            .filter { it.createdAt >= sinceMs }
            .sortedByDescending { it.createdAt }
        val allQuestions = repo.loadAllQuestions().associateBy { it.id }
        val result = mutableListOf<WrongAnswerRecord>()
        for (snap in snapshots) {
            val wrongIds = try {
                if (snap.wrongQuestionIdsJson.isNullOrBlank()) emptyList()
                else org.json.JSONArray(snap.wrongQuestionIdsJson).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } catch (_: Exception) { emptyList() }
            val qIds = try {
                if (snap.questionIdsJson.isNullOrBlank()) emptyList()
                else org.json.JSONArray(snap.questionIdsJson).let { arr ->
                    (0 until arr.length()).map { arr.getString(it) }
                }
            } catch (_: Exception) { emptyList() }
            val userAnswersMap = mutableMapOf<String, Int>()
            try {
                val ansArr = org.json.JSONArray(snap.userAnswersJson)
                qIds.forEachIndexed { i, id ->
                    if (i < ansArr.length()) {
                        val v = ansArr.optInt(i, -1)
                        userAnswersMap[id] = v
                    }
                }
            } catch (_: Exception) { }
            for (qId in wrongIds) {
                val q = allQuestions[qId] ?: continue
                val userIdx = userAnswersMap[qId] ?: -1
                val userAnswer = if (userIdx in 0..3) q.choices.getOrNull(userIdx) ?: "—" else "—"
                val correctAnswer = q.choices.getOrNull(q.correctIndex) ?: "?"
                result.add(WrongAnswerRecord(
                    subject = q.subject.tr,
                    stem = q.stem,
                    choices = q.choices.toList(),
                    userAnswer = userAnswer,
                    correctAnswer = correctAnswer,
                    category = q.topic ?: q.subject.tr,
                    dateMs = snap.createdAt,
                    testId = snap.testId
                ))
            }
        }
        return result
    }
}
