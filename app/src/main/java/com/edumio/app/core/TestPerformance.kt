package com.edumio.app.core

import com.edumio.app.quiz.Subject

data class TestPerformance(
    val quizId: String,
    val tsMs: Long,
    val userId: String = "default",
    val accuracy: Float,
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val totalQuestions: Int = 0,
    val passed: Boolean,
    val wrongQuestionIds: List<String>,
    val questionIds: List<String> = emptyList(),
    val byTopic: Map<String, Float> = emptyMap(),
    val byDifficulty: Map<String, Float> = emptyMap(),
    val byTopicCounts: Map<String, TopicCounts> = emptyMap(),
    val byDifficultyCounts: Map<String, TopicCounts> = emptyMap(),
    val avgAnswerTimeMs: Long? = null
) {
    val effectiveTotal: Int get() = if (totalQuestions > 0) totalQuestions else (correctCount + wrongCount + blankCount)
}
