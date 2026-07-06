package com.mioacademy.app.report

import android.content.Context
import com.mioacademy.app.core.AnalyticsStore
import com.mioacademy.app.core.ExamReadinessEngine
import com.mioacademy.app.core.ExamType
import com.mioacademy.app.core.LearningJourney
import com.mioacademy.app.core.ProgressInsights
import com.mioacademy.app.core.ReadinessSnapshotStore
import com.mioacademy.app.core.TopicCounts
import com.mioacademy.app.core.UserGoalPrefs
import com.mioacademy.app.quiz.WrongQuestionScheduler
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Builds [WeeklyReportData] / [MonthlyReportData] for the active study area from real
 * recorded activity only. No fabrication: sparse fields come back null/empty and
 * [hasEnoughData] gates the honest "not enough data yet" report.
 */
object ReportBuilder {

    fun buildWeekly(context: Context): WeeklyReportData {
        val insights = ProgressInsights(context)
        val analytics = AnalyticsStore(context)
        val w = insights.weekly()
        val trend = insights.topicTrend()
        val streak = insights.streak()
        val states = WrongQuestionScheduler(context).learningStates()

        val readiness = ExamReadinessEngine(context).compute()
        val snapshots = ReadinessSnapshotStore(context)
        val readinessScore = if (readiness.hasEnoughData) readiness.score else null
        if (readinessScore != null) snapshots.captureIfNewDay(readinessScore)
        val readinessChange = readinessScore?.let { now ->
            snapshots.scoreOnOrBefore(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(6))?.let { now - it }
        }

        return WeeklyReportData(
            areaLabel = areaLabel(context),
            hasEnoughData = insights.hasAnyData(),
            questionsSolved = w.questions,
            questionsPrevWeek = w.questionsPrev,
            accuracy = w.accuracy,
            accuracyPrevWeek = w.accuracyPrev,
            activeStudyDays = w.activeDays,
            streakDays = streak.days,
            xpEarned = w.xp,
            mostImprovedTopic = trend.mostImproved,
            mostImprovedDelta = trend.mostImprovedDelta,
            strongestTopic = trend.strongest,
            weakestTopic = trend.weakest,
            questionsMastered = states.mastered,
            questionsUnderReview = states.reviewing,
            readinessScore = readinessScore,
            readinessChange = readinessChange,
            heatmapLast7 = lastNDaysCounts(analytics, 7),
            journeyMilestone = latestMilestone(context),
            nextWeekRecommendation = recommendation(trend.weakest?.first, states.reviewing, streak.atRisk),
            studyTimeMinutes = null // per-question timing not captured yet
        )
    }

    fun buildMonthly(context: Context): MonthlyReportData {
        val analytics = AnalyticsStore(context)
        val insights = ProgressInsights(context)
        val sessions = analytics.getSessions()
        val now = System.currentTimeMillis()
        val month = TimeUnit.DAYS.toMillis(30)

        val thisMonth = sessions.filter { it.tsMs >= now - month }
        val prevMonth = sessions.filter { it.tsMs in (now - 2 * month) until (now - month) }
        val qThis = thisMonth.sumOf { it.total }
        val qPrev = prevMonth.sumOf { it.total }
        val correct = thisMonth.sumOf { it.correct }
        val accuracy = if (qThis >= ProgressInsights.MIN_QUESTIONS_FOR_ACCURACY)
            (100.0 * correct / qThis).roundToInt() else null

        val (impTopic, impDelta) = bestImprovement(analytics, now, month)
        val states = WrongQuestionScheduler(context).learningStates()
        val readiness = ExamReadinessEngine(context).compute()
        val readinessScore = if (readiness.hasEnoughData) readiness.score else null
        val readinessChange = readinessScore?.let { s ->
            ReadinessSnapshotStore(context).scoreOnOrBefore(now - month)?.let { s - it }
        }

        return MonthlyReportData(
            areaLabel = areaLabel(context),
            hasEnoughData = insights.hasAnyData(),
            questionsSolved = qThis,
            questionsPrevMonth = qPrev,
            accuracy = accuracy,
            activeStudyDays = thisMonth.map { TimeUnit.MILLISECONDS.toDays(it.tsMs) }.distinct().size,
            bestImprovementTopic = impTopic,
            bestImprovementDelta = impDelta,
            weakestAreas = analytics.getWeakestTopicsWithCounts(3)
                .filter { it.second.total >= ProgressInsights.MIN_TOPIC_QUESTIONS }
                .map { it.first },
            questionsMastered = states.mastered,
            reviewCorrections = analytics.getTotalReviewCorrections(),
            readinessScore = readinessScore,
            readinessChange = readinessChange,
            journeyMilestones = LearningJourney.milestones(context)
                .filter { it.reached && it.label != "Bugün" }.map { it.label },
            nextMonthRecommendation = recommendation(
                analytics.getWeakestTopicsWithCounts(1).firstOrNull()?.first, states.reviewing, false),
            studyTimeMinutes = null
        )
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun areaLabel(context: Context): String {
        val exam = UserGoalPrefs(context).getExamType()
        return if (exam == ExamType.UNKNOWN) "Çalışma Alanın" else exam.name
    }

    private fun lastNDaysCounts(analytics: AnalyticsStore, n: Int): List<Int> {
        val sessions = analytics.getSessions()
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        return (n - 1 downTo 0).map { back ->
            val day = today - back
            sessions.filter { TimeUnit.MILLISECONDS.toDays(it.tsMs) == day }.sumOf { it.total }
        }
    }

    private fun latestMilestone(context: Context): String? =
        LearningJourney.milestones(context).lastOrNull { it.reached && it.label != "Bugün" }?.label

    private fun bestImprovement(analytics: AnalyticsStore, now: Long, windowMs: Long): Pair<String?, Int> {
        val perfs = analytics.getTestPerformances()
        val recent = aggregate(perfs.filter { it.tsMs >= now - windowMs })
        val older = aggregate(perfs.filter { it.tsMs in (now - 2 * windowMs) until (now - windowMs) })
        var topic: String? = null
        var bestDelta = 0
        recent.forEach { (t, tc) ->
            val o = older[t]
            if (o != null && tc.total >= ProgressInsights.MIN_TOPIC_QUESTIONS && o.total >= ProgressInsights.MIN_TOPIC_QUESTIONS) {
                val d = (tc.accuracy - o.accuracy).roundToInt()
                if (d > bestDelta) { bestDelta = d; topic = t }
            }
        }
        return topic to bestDelta
    }

    private fun aggregate(perfs: List<com.mioacademy.app.core.TestPerformance>): Map<String, TopicCounts> {
        val agg = HashMap<String, TopicCounts>()
        perfs.forEach { p ->
            p.byTopicCounts.forEach { (topic, tc) ->
                val cur = agg[topic] ?: TopicCounts(0, 0, 0, 0)
                agg[topic] = TopicCounts(cur.correct + tc.correct, cur.wrong + tc.wrong, cur.blank + tc.blank, cur.total + tc.total)
            }
        }
        return agg
    }

    private fun recommendation(weakest: String?, underReview: Int, streakAtRisk: Boolean): String = when {
        underReview > 0 -> "Tekrar kuyruğundaki soruları temizlemeye odaklan — kalıcı öğrenme buradan gelir."
        weakest != null -> "$weakest konusuna biraz daha zaman ayır; en çok gelişim fırsatın burada."
        streakAtRisk -> "Serini korumak için her gün kısa bir görev yap."
        else -> "Bu istikrarı koru — haftada en az 4 gün çalışman seni sınava hazırlıyor."
    }
}
