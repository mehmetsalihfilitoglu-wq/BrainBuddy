package com.mioacademy.app.core.exam

import com.mioacademy.app.core.ExamType

data class ExamScoring(
    val correctPoints: Float,
    val wrongPenalty: Float,
    val blankPoints: Float = 0f
)

data class AdmissionExam(
    val examType: ExamType,
    val durationMinutes: Int,
    val totalQuestions: Int,
    val scoring: ExamScoring,
    val subjects: List<ExamSubject>
) {
    val subjectDisplaySummary: String
        get() = subjects.joinToString(" · ") { it.displayNameTr }

    /**
     * Returns the exam weight [0..1] for a given DB topic name.
     * Fuzzy-matches Turkish topic strings against subject display names.
     */
    fun weightForTopicName(topicName: String): Float {
        if (subjects.isEmpty()) return 0f
        val name = topicName.trim().lowercase()
        return subjects.maxByOrNull { sub -> fuzzyScore(name, sub.displayNameTr.lowercase()) }
            ?.weight ?: 0f
    }

    private fun fuzzyScore(topic: String, subjectName: String): Int {
        if (topic == subjectName) return 4
        if (topic.contains(subjectName) || subjectName.contains(topic)) return 3
        val subWords = subjectName.split(" ", "&", "·", "-").filter { it.length > 2 }
        if (subWords.any { topic.contains(it) }) return 2
        val topicWords = topic.split(" ", "&", "·", "-").filter { it.length > 2 }
        if (topicWords.any { subjectName.contains(it) }) return 1
        return 0
    }
}
