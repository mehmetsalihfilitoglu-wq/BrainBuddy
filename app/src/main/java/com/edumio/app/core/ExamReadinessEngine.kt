package com.edumio.app.core

import android.content.Context
import com.edumio.app.core.exam.AdmissionExamRegistry
import com.edumio.app.quiz.WrongQuestionScheduler
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Exam Readiness — a single, honest "how ready am I?" signal computed ONLY from
 * the student's real recorded activity in the active study area. Never a
 * fabricated percentage: below a real-data threshold it reports
 * [Readiness.hasEnoughData] = false and the UI must show "yeterli veri yok".
 *
 * The score blends the things that actually predict exam readiness:
 * accuracy, study consistency, breadth of coverage, how the weakest area is
 * doing (balance), how well mistakes are being cleared (review), and recency.
 * It interprets the data ("what's the biggest opportunity") rather than just
 * printing a number. Fully active-area scoped.
 */
class ExamReadinessEngine(private val context: Context) {

    data class Factor(val label: String, val percent: Int)

    data class Readiness(
        val score: Int,                 // 0..100
        val hasEnoughData: Boolean,
        val headline: String,
        val biggestOpportunity: String?, // weakest area to focus on, if any
        val factors: List<Factor>
    )

    fun compute(): Readiness {
        val analytics = AnalyticsStore(context)
        val counts = analytics.getOverallCounts()
        if (counts.total < MIN_QUESTIONS) {
            return Readiness(0, false,
                "Sınav hazırlık skorun için biraz daha veri gerekli.", null, emptyList())
        }

        val exam = AdmissionExamRegistry.get(UserGoalPrefs(context).getCareerPath().examType)
        val topics = analytics.getTopicMasteryWithCounts().filterValues { it.total >= 3 }

        // 1) Accuracy — overall correctness.
        val accuracy = (analytics.getOverallAccuracy() / 100f).coerceIn(0f, 1f)

        // 2) Consistency — active study days in the last 14.
        val now = System.currentTimeMillis()
        val activeDays = analytics.getSessions()
            .filter { it.tsMs >= now - TimeUnit.DAYS.toMillis(14) }
            .map { TimeUnit.MILLISECONDS.toDays(it.tsMs) }.distinct().size
        val consistency = (activeDays / 10f).coerceIn(0f, 1f)

        // 3) Coverage — breadth of the exam actually practised.
        val coverage = (topics.size.toFloat() / max(exam.subjects.size, 3)).coerceIn(0f, 1f)

        // 4) Balance — how the weakest practised area is doing (drags readiness, realistically).
        val balance = topics.values.minOfOrNull { it.accuracy / 100f }?.coerceIn(0f, 1f) ?: 0f

        // 5) Review — how well mistakes are being mastered.
        val scheduler = WrongQuestionScheduler(context)
        val mastered = scheduler.masteredTotal
        val pending = scheduler.reviewQueueSize()
        val review = if (mastered + pending == 0) 0.6f  // nothing to review yet → neutral
            else (mastered.toFloat() / (mastered + pending)).coerceIn(0f, 1f)

        // 6) Recency — studied recently?
        val daysSince = analytics.getSessions().maxOfOrNull { it.tsMs }
            ?.let { TimeUnit.MILLISECONDS.toDays(now - it) } ?: 99
        val recency = (1f - (daysSince / 10f)).coerceIn(0f, 1f)

        val score = (100 * (
            0.30f * accuracy +
            0.20f * consistency +
            0.15f * coverage +
            0.15f * balance +
            0.10f * review +
            0.10f * recency
        )).roundToInt().coerceIn(0, 100)

        val weakest = analytics.getWeakestTopicsWithCounts(1)
            .firstOrNull { it.second.total >= ProgressInsights.MIN_TOPIC_QUESTIONS }?.first

        val headline = when {
            score >= 75 -> "Sınav hazırlığın güçlü. Bu tempoyu koru."
            score >= 55 -> "İyi yoldasın. Düzenli çalışman skorunu yükseltiyor."
            score >= 35 -> "Temeli atıyorsun. Zayıf alanlara odaklanmak en çok fark yaratır."
            else -> "Başlangıç aşamasındasın. Her gün küçük bir adım skoru büyütür."
        }

        return Readiness(
            score = score,
            hasEnoughData = true,
            headline = headline,
            biggestOpportunity = weakest,
            factors = listOf(
                Factor("Doğruluk", (accuracy * 100).roundToInt()),
                Factor("İstikrar", (consistency * 100).roundToInt()),
                Factor("Kapsam", (coverage * 100).roundToInt()),
                Factor("En zayıf alan", (balance * 100).roundToInt()),
                Factor("Tekrar", (review * 100).roundToInt())
            )
        )
    }

    companion object {
        /** Below this many recorded questions we never invent a readiness score. */
        const val MIN_QUESTIONS = 20
    }
}
