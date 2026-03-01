package com.brainbuddy.app.core

import com.brainbuddy.app.quiz.Subject

data class TestPerformance(
    val quizId: String,
    val tsMs: Long,
    val userId: String = "default",
    val accuracy: Float,
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val passed: Boolean,
    val wrongQuestionIds: List<String>,
    val byTopic: Map<String, Float> = emptyMap(),
    val byDifficulty: Map<String, Float> = emptyMap(),
    val avgAnswerTimeMs: Long? = null
)
