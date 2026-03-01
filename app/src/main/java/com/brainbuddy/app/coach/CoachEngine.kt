package com.brainbuddy.app.coach

import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.UserStats
import com.brainbuddy.app.core.TopicCounts

/**
 * Rule-based learning coach. No AI API needed.
 */
class CoachEngine(private val analytics: AnalyticsStore) {

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
        val stats = analytics.getUserStats()
        val lastTests = analytics.getLastTests(5)
        val weakest = analytics.getWeakestTopicsWithCounts(5)
        val mastery = stats.topicMasteryCounts

        // 2 failures in same topic → remedial mini-test
        val failByTopic = mutableMapOf<String, Int>()
        lastTests.forEach { p ->
            p.byTopicCounts.forEach { (topic, tc) ->
                if (tc.wrong >= 2) failByTopic[topic] = (failByTopic[topic] ?: 0) + 1
            }
        }
        val remedialTopic = failByTopic.entries.firstOrNull { it.value >= 2 }?.key
        if (remedialTopic != null) {
            return DailyRecommendation(
                text = "Tekrarlayan hatalarınız var: $remedialTopic. Mini tekrar testi çözün.",
                topic = remedialTopic,
                suggestedCount = 10,
                isRemedialSuggestion = true
            )
        }

        // mastery < 50 → priority topic
        val weakTopic = mastery.entries
            .filter { it.value.total >= 3 && it.value.accuracy < 50f }
            .minByOrNull { it.value.accuracy }
        if (weakTopic != null) {
            return DailyRecommendation(
                text = "${weakTopic.key} konusunda zorlanıyorsunuz. Bugün ${weakTopic.key} sorularından 10 tane çözün.",
                topic = weakTopic.key,
                suggestedCount = 10
            )
        }

        // accuracy improving → encouragement
        if (lastTests.size >= 3) {
            val recent = lastTests.takeLast(3).map { it.accuracy }
            if (recent.last() > recent.first() && recent.last() >= 70f) {
                return DailyRecommendation(
                    text = "Harika gidiyorsunuz! Doğruluk oranınız yükseliyor. Devam edin!",
                    topic = null,
                    suggestedCount = 5,
                    isEncouragement = true
                )
            }
        }

        // default: weakest topic
        val topic = weakest.firstOrNull()?.first ?: "Matematik"
        return DailyRecommendation(
            text = "$topic konusunda pratik yapmanız faydalı olacak. Bugün 10 soru çözün.",
            topic = topic,
            suggestedCount = 10
        )
    }

    fun getWeeklyPlan(): WeeklyPlan {
        val stats = analytics.getUserStats()
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
        val summary = plan.joinToString("; ") { "${it.first}: ${it.second} soru (${it.third})" }
            .ifEmpty { "Tüm konularda dengeli pratik yapın." }
        return WeeklyPlan(plan, summary)
    }
}
