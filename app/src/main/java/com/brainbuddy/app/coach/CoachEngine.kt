package com.brainbuddy.app.coach

import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.CareerPath
import com.brainbuddy.app.core.TopicCounts
import com.brainbuddy.app.core.UserGoalPrefs
import com.brainbuddy.app.core.UserStats

/**
 * Rule-based learning coach personalised to the student's career goal.
 * No AI API — deterministic recommendations from local analytics.
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
        val personTitle = personTitleFor(career)
        val stats = analytics.getUserStats()
        val lastTests = analytics.getLastTests(5)
        val weakest = analytics.getWeakestTopicsWithCounts(5)

        // Repeated failures in same topic → remedial mini-test
        val failByTopic = mutableMapOf<String, Int>()
        lastTests.forEach { p ->
            p.byTopicCounts.forEach { (topic, tc) ->
                if (tc.wrong >= 2) failByTopic[topic] = (failByTopic[topic] ?: 0) + 1
            }
        }
        val remedialTopic = failByTopic.entries.firstOrNull { it.value >= 2 }?.key
        if (remedialTopic != null) {
            return DailyRecommendation(
                text = "${career.emoji} $personTitle yolculuğunda $remedialTopic konusunda " +
                    "tekrarlayan hatalar görüyorum. Bir mini tekrar testi çözelim.",
                topic = remedialTopic,
                suggestedCount = 10,
                isRemedialSuggestion = true
            )
        }

        // Mastery below 50% → priority topic
        val weakTopic = stats.topicMasteryCounts.entries
            .filter { it.value.total >= 3 && it.value.accuracy < 50f }
            .minByOrNull { it.value.accuracy }
        if (weakTopic != null) {
            return DailyRecommendation(
                text = "${career.emoji} $personTitle olmak için ${weakTopic.key} konusunu " +
                    "güçlendirmek seni ilerletir. Bugün bu konudan 10 soru çöz.",
                topic = weakTopic.key,
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

        // Default: weakest topic with career context
        val topic = weakest.firstOrNull()?.first ?: "Matematik"
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
        val personTitle = personTitleFor(career)

        val topics = stats.topicMasteryCounts.entries
            .filter { it.value.total >= 2 }
            .sortedBy { it.value.accuracy }
            .take(5)

        val plan = topics.take(3).mapIndexed { i, (topic, tc) ->
            val count = when {
                tc.accuracy < 50f -> 15
                tc.accuracy < 75f -> 10
                else -> 5
            }
            val priority = if (tc.accuracy < 50f) "Öncelik" else "Pratik"
            Triple(topic, count, priority)
        }

        val summary = if (plan.isEmpty()) {
            "${career.emoji} $personTitle yolculuğuna başlamak için bugün ilk testini çöz!"
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
        else -> "Öğrenci"
    }
}
