package com.edumio.app.core

import android.content.Context
import com.edumio.app.core.exam.AdmissionExam
import com.edumio.app.core.exam.AdmissionExamRegistry
import com.edumio.app.quiz.Subject
import com.edumio.app.quiz.SubjectFilter
import java.util.concurrent.TimeUnit

/**
 * Builds the Daily Mission as a mini version of the real entrance exam.
 *
 * Philosophy: the free mission always mirrors the official exam blueprint — a
 * balanced spread of the exam's sections (from [AdmissionExamRegistry]) scaled
 * to a short daily set — so it feels like "today's mini exam", never
 * "solve 10 Biology questions". Premium keeps that exam feel but quietly shifts
 * practice toward the sections the student is actually weaker in.
 *
 * Everything is real-data and active-area scoped:
 * - the blueprint comes from the exam definition (extensible: add an exam to the
 *   registry and its mission works automatically);
 * - premium personalization uses real per-subject accuracy from [AnalyticsStore];
 * - when there isn't enough data to personalize honestly, it falls back to the
 *   balanced mission and [Plan.isPersonalized] stays false. No invented
 *   strengths or weaknesses.
 *
 * The blueprint [sections] are the exam's real structure (presentation + ready
 * to drive per-section selection once real exam content exists). Today's actual
 * questions are drawn from [executionCategories] (DB subjects): null = balanced,
 * or a weak-biased set for personalized premium missions.
 */
class DailyMissionEngine(private val context: Context) {

    data class Section(val displayName: String, val count: Int)

    data class Plan(
        val examCode: String,
        val sections: List<Section>,
        val total: Int,
        val isPersonalized: Boolean,
        val executionCategories: Set<String>?
    ) {
        val sectionsSummary: String get() = sections.joinToString(" · ") { it.displayName }
    }

    fun plan(): Plan {
        val exam = AdmissionExamRegistry.get(UserGoalPrefs(context).getCareerPath().examType)
        val day = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()).toInt()

        val sections = distribute(exam, MISSION_SIZE, day)
        val premium = PremiumStore(context).isPremium()
        val execCats = if (premium) weakBiasedCategories() else null

        return Plan(
            examCode = exam.examType.code,
            sections = sections,
            total = MISSION_SIZE,
            isPersonalized = premium && execCats != null,
            executionCategories = execCats
        )
    }

    /**
     * Scales the exam blueprint to [total] questions using largest-remainder
     * allocation, rotating which sections receive the leftover by [day] so the
     * exact composition shifts naturally day to day while staying proportional
     * to the official distribution.
     */
    private fun distribute(exam: AdmissionExam, total: Int, day: Int): List<Section> {
        val subs = exam.subjects
        if (subs.isEmpty()) return listOf(Section("Karışık", total))

        val raw = subs.map { it.weight * total }
        val counts = raw.map { it.toInt() }.toMutableList()
        var remaining = total - counts.sum()
        val byRemainder = subs.indices.sortedByDescending { raw[it] - counts[it] }
        var i = 0
        while (remaining > 0 && byRemainder.isNotEmpty()) {
            counts[byRemainder[(i + day) % byRemainder.size]]++
            remaining--; i++
        }
        return subs.indices
            .map { Section(subs[it].displayNameTr, counts[it]) }
            .filter { it.count > 0 }
    }

    /**
     * Premium: keep breadth but drop the clearly-mastered subject so the mission
     * concentrates on weaker areas. Returns null (→ balanced) unless there is
     * enough real, multi-subject data to personalize honestly.
     */
    private fun weakBiasedCategories(): Set<String>? {
        val analytics = AnalyticsStore(context)
        val perSubject = HashMap<Subject, TopicCounts>()
        analytics.getTopicMasteryWithCounts().forEach { (topic, tc) ->
            val subj = topicToSubject(topic) ?: return@forEach
            val cur = perSubject[subj] ?: TopicCounts(0, 0, 0, 0)
            perSubject[subj] = TopicCounts(
                cur.correct + tc.correct, cur.wrong + tc.wrong, cur.blank + tc.blank, cur.total + tc.total
            )
        }
        val withData = perSubject.filterValues { it.total >= ProgressInsights.MIN_TOPIC_QUESTIONS }
        if (withData.size < 3) return null

        val sorted = withData.entries.sortedByDescending { it.value.accuracy }
        val strongest = sorted.first()
        val median = sorted[sorted.size / 2].value.accuracy
        // Only drop the strongest when it is clearly ahead — otherwise stay balanced.
        if (strongest.value.accuracy - median < 15f) return null
        val set = withData.keys.filter { it != strongest.key }.map { it.name }.toSet()
        return set.takeIf { it.size >= 2 }
    }

    private fun topicToSubject(topic: String): Subject? =
        Subject.entries.firstOrNull { it.tr.equals(topic, ignoreCase = true) }
            ?: SubjectFilter.forName(topic)?.let { tr -> Subject.entries.firstOrNull { it.tr == tr } }

    companion object {
        /** Short daily mini-exam size — habit-friendly, not exhausting. */
        const val MISSION_SIZE = 10
    }
}
