package com.brainbuddy.app.quiz

/**
 * In-memory quiz session state. No instant feedback until finish.
 * answers: QuestionId -> selected OptionIndex (0..n-1), -1 = blank/unanswered
 */
data class QuizSession(
    val quizId: String,
    val startedAt: Long,
    val questionIds: List<String> = emptyList(),
    val answers: Map<String, Int> = emptyMap(),
    val completedAt: Long? = null,
    val correctCount: Int = 0,
    val wrongCount: Int = 0,
    val blankCount: Int = 0,
    val passed: Boolean = true,
    val wrongQuestionIds: List<String> = emptyList(),
    val reviewCorrectedCount: Int = 0
) {
    val totalCount: Int get() = correctCount + wrongCount + blankCount
    val accuracy: Float get() = if (totalCount > 0) 100f * correctCount / totalCount else 0f
}
