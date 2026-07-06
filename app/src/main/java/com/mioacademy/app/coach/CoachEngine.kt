package com.mioacademy.app.coach

import com.mioacademy.app.core.AnalyticsStore
import com.mioacademy.app.core.CareerPath
import com.mioacademy.app.core.TopicCounts
import com.mioacademy.app.core.UserGoalPrefs
import com.mioacademy.app.core.UserStats
import com.mioacademy.app.core.exam.AdmissionExamRegistry

/**
 * Rule-based learning coach personalised to the student's career goal.
 * No AI API — deterministic recommendations from local analytics.
 * Topic priority = (1 − accuracy) × examSubjectWeight so the coach
 * focuses on both weak areas AND high-stakes exam sections.
 */
class CoachEngine(
    private val analytics: AnalyticsStore,
    private val goalPrefs: UserGoalPrefs
) {

    data class DailyRecommendation(
        val text: String,
        val topic: String?,
        val suggestedCount: Int,
        val isEncouragement: Boolean = false,
        val isRemedialSuggestion: Boolean = false
    )

    data class WeeklyPlan(
        val topics: List<Triple<String, Int, String>>, // topic, question count, priority
        val summary: String
    )

    fun getDailyRecommendation(): DailyRecommendation {
        val career = goalPrefs.getCareerPath()
        val exam = AdmissionExamRegistry.get(career.examType)
        val personTitle = personTitleFor(career)
        val stats = analytics.getUserStats()
        val lastTests = analytics.getLastTests(5)
        val weakest = analytics.getWeakestTopicsWithCounts(5)

        // Repeated failures in same topic → pick highest-weight remedial candidate
        val failByTopic = mutableMapOf<String, Int>()
        lastTests.forEach { p ->
            p.byTopicCounts.forEach { (topic, tc) ->
                if (tc.wrong >= 2) failByTopic[topic] = (failByTopic[topic] ?: 0) + 1
            }
        }
        val remedialTopic = failByTopic.entries
            .filter { it.value >= 2 }
            .maxByOrNull { (topic, count) ->
                count.toFloat() * exam.weightForTopicName(topic).coerceAtLeast(0.1f)
            }?.key
        if (remedialTopic != null) {
            return DailyRecommendation(
                text = "${career.emoji} $personTitle yolculuğunda $remedialTopic konusunda " +
                    "tekrarlayan hatalar görüyorum. Bir mini tekrar testi çözelim.",
                topic = remedialTopic,
                suggestedCount = 10,
                isRemedialSuggestion = true
            )
        }

        // Score topics: weakness × exam weight — surface the most exam-critical weak areas
        val scoredTopics = stats.topicMasteryCounts.entries
            .filter { it.value.total >= 3 }
            .map { (topic, tc) ->
                val weakness = (1f - tc.accuracy / 100f).coerceIn(0f, 1f)
                val examWeight = exam.weightForTopicName(topic).coerceAtLeast(0.1f)
                Triple(topic, tc, weakness * examWeight)
            }
            .sortedByDescending { it.third }

        val priorityTopic = scoredTopics.firstOrNull()
        if (priorityTopic != null && priorityTopic.second.accuracy < 60f) {
            return DailyRecommendation(
                text = "${career.emoji} ${priorityTopic.first} pratiği seni $personTitle olma " +
                    "yolunda ilerletir. Bugün bu konudan 10 soru çöz.",
                topic = priorityTopic.first,
                suggestedCount = 10
            )
        }

        // Accuracy improving → encouragement
        if (lastTests.size >= 3) {
            val recent = lastTests.takeLast(3).map { it.accuracy }
            if (recent.last() > recent.first() && recent.last() >= 70f) {
                return DailyRecommendation(
                    text = "Harika! ${career.emoji} $personTitle olma yolunda doğruluk " +
                        "oranın yükseliyor. Bu ivmeyi koru, devam et!",
                    topic = null,
                    suggestedCount = 5,
                    isEncouragement = true
                )
            }
        }

        // Default: highest-priority scored topic, or highest-weight exam subject
        val topic = scoredTopics.firstOrNull()?.first
            ?: weakest.firstOrNull()?.first
            ?: exam.subjects.firstOrNull()?.displayNameTr
            ?: "Matematik"
        return DailyRecommendation(
            text = "Bugün $topic konusuna odaklanmak, ${career.emoji} $personTitle " +
                "hedefine seni bir adım yaklaştırır. 10 soru çözmek için hazır mısın?",
            topic = topic,
            suggestedCount = 10
        )
    }

    fun getWeeklyPlan(): WeeklyPlan {
        val stats = analytics.getUserStats()
        val career = goalPrefs.getCareerPath()
        val exam = AdmissionExamRegistry.get(career.examType)
        val personTitle = personTitleFor(career)

        // Score topics by weakness × exam weight, take top 5
        val scored = stats.topicMasteryCounts.entries
            .filter { it.value.total >= 2 }
            .map { (topic, tc) ->
                val weakness = (1f - tc.accuracy / 100f).coerceIn(0f, 1f)
                val examWeight = exam.weightForTopicName(topic).coerceAtLeast(0.1f)
                Triple(topic, tc, weakness * examWeight)
            }
            .sortedByDescending { it.third }
            .take(5)

        val plan = scored.take(3).map { (topic, tc, _) ->
            val count = when {
                tc.accuracy < 50f -> 15
                tc.accuracy < 75f -> 10
                else -> 5
            }
            val priority = if (tc.accuracy < 50f) "Öncelik" else "Pratik"
            Triple(topic, count, priority)
        }

        val summary = if (plan.isEmpty()) {
            val subjectList = exam.subjects.take(3).joinToString("\n") { "• ${it.displayNameTr}" }
            "${career.emoji} $personTitle yolculuğunda bu hafta odaklan:\n$subjectList"
        } else {
            plan.joinToString("\n") { "• ${it.first}: ${it.second} soru  (${it.third})" }
        }

        return WeeklyPlan(plan, summary)
    }

    private fun personTitleFor(career: CareerPath): String = when (career) {
        CareerPath.MEDICINE -> "Doktor"
        CareerPath.DENTISTRY -> "Diş Hekimi"
        CareerPath.ENGINEERING -> "Mühendis"
        CareerPath.COMPUTER_SCIENCE -> "Yazılımcı"
        CareerPath.ARCHITECTURE -> "Mimar"
        CareerPath.ECONOMICS -> "Ekonomist"
        CareerPath.LAW -> "Avukat"
        CareerPath.PHARMACY -> "Eczacı"
        CareerPath.BIOLOGY -> "Biyolog"
        CareerPath.PSYCHOLOGY -> "Psikolog"
        CareerPath.VETERINARY -> "Veteriner"
        CareerPath.MATHEMATICS -> "Matematikçi"
        CareerPath.DESIGN -> "Tasarımcı"
        CareerPath.OTHER -> "Öğrenci"
    }
}
