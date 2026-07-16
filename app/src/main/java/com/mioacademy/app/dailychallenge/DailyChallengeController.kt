package com.mioacademy.app.dailychallenge

import android.content.Context
import com.mioacademy.app.core.ExamType

/**
 * Thin UI-facing wrapper over [DailyChallengeEngine]. Exposes the state the home screen needs:
 * progress (answered/5), completion, next-unlock time, selected exam. Never offers a second challenge.
 */
class DailyChallengeController(context: Context) {

    private val engine = DailyChallengeEngine(context.applicationContext)
    private val reviewEngine = ReviewEngine(context.applicationContext)

    data class UiState(
        val exam: ExamType,
        val available: Boolean,
        val answered: Int,
        val total: Int,
        val completed: Boolean,
        val score: Int,
        val nextUnlockAtMs: Long,
        val questionIds: List<String>,
        val shortage: String,
    )

    /** Loads (creating once if needed) today's challenge and maps it to UI state. */
    suspend fun today(userId: String, exam: ExamType, isPremium: Boolean = false): UiState? {
        val r = engine.getOrCreateToday(userId, exam, isPremium = isPremium) ?: return null
        return UiState(
            exam = exam,
            available = true,
            answered = r.answered,
            total = r.total,
            completed = r.completed,
            score = r.challenge.score,
            nextUnlockAtMs = r.challenge.expiresAt,
            questionIds = r.challenge.questionIdsCsv.split(",").filter { it.isNotBlank() },
            shortage = r.challenge.shortage,
        )
    }

    suspend fun submit(
        userId: String, exam: ExamType, localDate: String,
        questionId: String, chosenIndex: Int, isCorrect: Boolean, timeMs: Long,
    ): DailyChallengeEngine.Result? =
        engine.submitAnswer(userId, exam, localDate, questionId, chosenIndex, isCorrect, timeMs)

    fun todayLocalDate(): String = engine.localDate()

    suspend fun reviewCount(userId: String, exam: ExamType): Int =
        engine.reviewCount(userId, exam, listOf(ReviewQueue.INCORRECT, ReviewQueue.NEEDS_REVISION, ReviewQueue.FORGOTTEN))

    /** Review queue for the user. Premium unlocks unlimited depth; Free is capped. */
    suspend fun reviewQueue(userId: String, exam: ExamType, isPremium: Boolean = false): List<ReviewEngine.ReviewItem> =
        reviewEngine.getReviewQueue(userId, exam, isPremium)

    /** Record a review attempt; advances the spaced-repetition state. Returns the new state. */
    suspend fun submitReview(userId: String, questionId: String, isCorrect: Boolean): QuestionLearnState? =
        reviewEngine.submitReview(userId, questionId, isCorrect)

    suspend fun masteredCount(userId: String, exam: ExamType): Int =
        reviewEngine.masteredCount(userId, exam)
}
