package com.mioacademy.app.quiz

/**
 * A question in the spaced-repetition review ladder (Leitner-style).
 *
 * A wrong answer does not vanish after one correct answer: it climbs a mastery
 * ladder ([box]) with growing intervals, and is only considered mastered — and
 * removed — after several correct answers spread across tests. Answering it
 * wrong again drops it back to box 0.
 *
 * @param questionId Question ID
 * @param wrongCount Total times answered wrong (used for review priority)
 * @param dueAfterTest Global test index after which this question becomes due
 * @param lastShownAtCompletedTest Last completedTests when injected (-1 if never)
 * @param box Mastery level: 0 = just wrong; each spaced correct answer → +1;
 *            at [WrongQuestionScheduler.MASTERED_BOX] the question is mastered.
 */
data class WrongQuestion(
    val questionId: String,
    var wrongCount: Int,
    var dueAfterTest: Int,
    var lastShownAtCompletedTest: Int = -1,
    var box: Int = 0,
    /** Total correct answers on this question (across reviews) — history. */
    var correctCount: Int = 0,
    /** When the question first entered the review ladder (epoch ms). */
    var firstSeenMs: Long = 0L
) {
    /** Where the question is on its learning path. */
    val state: LearningState
        get() = when {
            box >= WrongQuestionScheduler.MASTERED_BOX -> LearningState.MASTERED
            box >= 1 -> LearningState.REVIEWING
            else -> LearningState.WRONG
        }
}

enum class LearningState { WRONG, REVIEWING, MASTERED }

