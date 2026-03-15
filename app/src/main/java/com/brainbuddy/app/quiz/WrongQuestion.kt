package com.brainbuddy.app.quiz

/**
 * Represents a question that was answered wrong and is scheduled to reappear
 * after a number of completed tests (spacing by tests, not time).
 *
 * @param questionId Question ID
 * @param wrongCount How many times this question was answered wrong (1st, 2nd, 3rd...)
 * @param dueAfterTest Global test index after which this question becomes due for reappearance
 */
data class WrongQuestion(
    val questionId: String,
    var wrongCount: Int,
    var dueAfterTest: Int
)
