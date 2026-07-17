package com.edumio.app.dailychallenge

/**
 * Pure spaced-repetition state machine for the review queues (no Android/DB deps → unit-testable).
 *
 * Review NEVER introduces new questions — it only re-surfaces questions the user has already been
 * served in a Daily Challenge. Therefore Premium can gate the *depth* of review freely without ever
 * touching the hard "5 new questions per local day" invariant. Free = capped daily review; Premium =
 * unlimited review.
 *
 * Ladder: each consecutive correct review pushes the next review further out; [MASTERY_STREAK]
 * consecutive correct answers retire the question to MASTERED. A wrong review resets the streak and
 * schedules the question for tomorrow. A NEEDS_REVISION item left overdue long enough decays to
 * FORGOTTEN so it resurfaces with priority.
 */
object ReviewScheduler {

    const val DAY_MS = 86_400_000L

    /** Spaced-repetition intervals (days) applied after the Nth consecutive correct review. */
    val intervalDays = intArrayOf(1, 3, 7, 16, 35)

    /** Consecutive correct reviews required to retire a question to MASTERED. */
    const val MASTERY_STREAK = 5

    /** A NEEDS_REVISION item overdue by more than this decays to FORGOTTEN. */
    const val FORGOTTEN_AFTER_DAYS = 30

    /** Free tier: at most this many review questions surfaced per session. Premium: unlimited. */
    const val FREE_REVIEW_DAILY_CAP = 10

    data class Outcome(
        val state: QuestionLearnState,
        val consecutiveCorrect: Int,
        val nextReviewAtMs: Long,
        val masteredAtMs: Long,
    )

    /** Apply the result of one review attempt to a question's learning state. Pure. */
    fun onReview(
        prevState: QuestionLearnState,
        prevConsecutiveCorrect: Int,
        isCorrect: Boolean,
        nowMs: Long,
        dayMs: Long = DAY_MS,
    ): Outcome {
        if (!isCorrect) {
            // A miss during review sends the question back to the front of the incorrect queue.
            return Outcome(QuestionLearnState.INCORRECT_MULTIPLE, 0, nowMs + intervalDays[0] * dayMs, 0L)
        }
        val streak = prevConsecutiveCorrect + 1
        if (streak >= MASTERY_STREAK) {
            return Outcome(QuestionLearnState.MASTERED, streak, Long.MAX_VALUE, nowMs)
        }
        val idx = (streak - 1).coerceIn(0, intervalDays.size - 1)
        return Outcome(QuestionLearnState.NEEDS_REVISION, streak, nowMs + intervalDays[idx] * dayMs, 0L)
    }

    /** Is a question in this state due to surface in the review queue right now? */
    fun isDue(state: QuestionLearnState, nextReviewAtMs: Long, nowMs: Long): Boolean = when (state) {
        QuestionLearnState.INCORRECT_ONCE,
        QuestionLearnState.INCORRECT_MULTIPLE,
        QuestionLearnState.FORGOTTEN -> true // always due — the user got these wrong
        QuestionLearnState.NEEDS_REVISION -> nowMs >= nextReviewAtMs
        else -> false // NEVER_SEEN / SEEN_ONCE / CORRECT / MASTERED are not review-eligible
    }

    /** A NEEDS_REVISION item left overdue beyond [forgottenAfterDays] decays to FORGOTTEN. */
    fun decayIfOverdue(
        state: QuestionLearnState,
        nextReviewAtMs: Long,
        nowMs: Long,
        forgottenAfterDays: Int = FORGOTTEN_AFTER_DAYS,
        dayMs: Long = DAY_MS,
    ): QuestionLearnState {
        if (state == QuestionLearnState.NEEDS_REVISION && nowMs - nextReviewAtMs >= forgottenAfterDays * dayMs) {
            return QuestionLearnState.FORGOTTEN
        }
        return state
    }

    /** Which review queue a state belongs to (null if not review-eligible). */
    fun queueOf(state: QuestionLearnState): ReviewQueue? = when (state) {
        QuestionLearnState.INCORRECT_ONCE, QuestionLearnState.INCORRECT_MULTIPLE -> ReviewQueue.INCORRECT
        QuestionLearnState.NEEDS_REVISION -> ReviewQueue.NEEDS_REVISION
        QuestionLearnState.FORGOTTEN -> ReviewQueue.FORGOTTEN
        else -> null
    }

    /** Surface priority: wrong answers first, then forgotten, then scheduled revision. */
    fun priorityOf(state: QuestionLearnState): Int = when (state) {
        QuestionLearnState.INCORRECT_MULTIPLE -> 0
        QuestionLearnState.INCORRECT_ONCE -> 1
        QuestionLearnState.FORGOTTEN -> 2
        QuestionLearnState.NEEDS_REVISION -> 3
        else -> 9
    }

    /**
     * Premium gates the DEPTH of review, never the daily NEW-question count.
     * Free: capped at [FREE_REVIEW_DAILY_CAP]. Premium: the full due list.
     */
    fun <T> applyPremiumCap(items: List<T>, isPremium: Boolean): List<T> =
        if (isPremium) items else items.take(FREE_REVIEW_DAILY_CAP)
}
