package com.edumio.app.dailychallenge

import android.content.Context
import com.edumio.app.core.ExamType
import com.edumio.app.db.QuestionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Serves the per-user review queues (INCORRECT / NEEDS_REVISION / FORGOTTEN) and advances the
 * spaced-repetition state via [ReviewScheduler].
 *
 * Hard invariant: review re-surfaces ONLY questions the user has already been served — it never
 * introduces a new (NEVER_SEEN) question and never creates a Daily Challenge row, so it cannot
 * increase the "5 new per local day" count. Premium unlocks unlimited review depth; Free is capped
 * at [ReviewScheduler.FREE_REVIEW_DAILY_CAP].
 *
 * Dependencies are injected (Room stores + content) so this invariant is provable in a pure-JVM test.
 */
class ReviewEngine internal constructor(
    private val dao: DailyChallengeDao,
    private val content: DcContentSource,
    private val analytics: DailyChallengeAnalytics,
) {

    /** Production wiring: real Room stores + analytics. */
    constructor(appContext: Context) : this(
        dao = DailyChallengeDatabase.get(appContext).dailyChallengeDao(),
        content = object : DcContentSource {
            override suspend fun getDailyCandidatePool(examType: String, section: String) =
                com.edumio.app.db.DatabaseProvider.get(appContext).questionDao().getDailyCandidatePool(examType, section)
            override suspend fun getQuestionsByIds(ids: List<String>) =
                com.edumio.app.db.DatabaseProvider.get(appContext).questionDao().getQuestionsByIds(ids)
        },
        analytics = DailyChallengeAnalyticsProvider.get(appContext),
    )

    data class ReviewItem(
        val question: QuestionEntity,
        val state: QuestionLearnState,
        val queue: ReviewQueue,
        val nextReviewAtMs: Long,
    )

    private val reviewStateNames = listOf(
        QuestionLearnState.INCORRECT_ONCE.name,
        QuestionLearnState.INCORRECT_MULTIPLE.name,
        QuestionLearnState.NEEDS_REVISION.name,
        QuestionLearnState.FORGOTTEN.name,
    )

    /**
     * The review queue the user should work through now. Overdue NEEDS_REVISION items decay to
     * FORGOTTEN (persisted) before ordering. Ordered by priority (wrong → forgotten → scheduled).
     * Free tier is capped; Premium is unlimited.
     */
    suspend fun getReviewQueue(
        userId: String,
        exam: ExamType,
        isPremium: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
    ): List<ReviewItem> = withContext(Dispatchers.IO) {
        val states = dao.getStatesByStates(userId, exam.name, reviewStateNames)

        // decay overdue revision items to FORGOTTEN, then keep only those actually due now
        val due = ArrayList<UserQuestionStateEntity>()
        for (s in states) {
            val cur = QuestionLearnState.valueOf(s.state)
            val decayed = ReviewScheduler.decayIfOverdue(cur, s.nextReviewAt, nowMs)
            val row = if (decayed != cur) {
                val updated = s.copy(state = decayed.name)
                dao.upsertState(updated)
                updated
            } else s
            if (ReviewScheduler.isDue(QuestionLearnState.valueOf(row.state), row.nextReviewAt, nowMs)) due += row
        }

        val ordered = due.sortedWith(
            compareBy(
                { ReviewScheduler.priorityOf(QuestionLearnState.valueOf(it.state)) },
                { it.nextReviewAt },
                { it.lastSeenAt },
            )
        )
        val capped = ReviewScheduler.applyPremiumCap(ordered, isPremium)
        val byId = content.getQuestionsByIds(capped.map { it.questionId }).associateBy { it.id }
        capped.mapNotNull { st ->
            val q = byId[st.questionId] ?: return@mapNotNull null
            val state = QuestionLearnState.valueOf(st.state)
            val queue = ReviewScheduler.queueOf(state) ?: return@mapNotNull null
            ReviewItem(q, state, queue, st.nextReviewAt)
        }
    }

    /**
     * One specific question as a retry-able review item (the hub's "Retry Question" path), regardless
     * of its due time — Premium allows unlimited retries of previously-served wrong questions. Returns
     * null when the question was never served to this user or is not review-eligible (e.g. MASTERED),
     * so this can NEVER surface a new question or touch the 5-new-per-day count.
     */
    suspend fun getReviewItem(userId: String, questionId: String): ReviewItem? = withContext(Dispatchers.IO) {
        val st = dao.getState(userId, questionId) ?: return@withContext null // never served → not retryable
        val state = runCatching { QuestionLearnState.valueOf(st.state) }.getOrNull() ?: return@withContext null
        val queue = ReviewScheduler.queueOf(state) ?: return@withContext null // not review-eligible
        val q = content.getQuestionsByIds(listOf(questionId)).firstOrNull() ?: return@withContext null
        ReviewItem(q, state, queue, st.nextReviewAt)
    }

    /** Advance a question's spaced-repetition state after a review attempt. Returns the new state. */
    suspend fun submitReview(
        userId: String,
        questionId: String,
        isCorrect: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): QuestionLearnState? = withContext(Dispatchers.IO) {
        val prev = dao.getState(userId, questionId) ?: return@withContext null
        val outcome = ReviewScheduler.onReview(
            QuestionLearnState.valueOf(prev.state), prev.consecutiveCorrect, isCorrect, nowMs,
        )
        dao.upsertState(
            prev.copy(
                state = outcome.state.name,
                consecutiveCorrect = outcome.consecutiveCorrect,
                timesSeen = prev.timesSeen + 1,
                timesCorrect = prev.timesCorrect + if (isCorrect) 1 else 0,
                timesIncorrect = prev.timesIncorrect + if (isCorrect) 0 else 1,
                lastSeenAt = nowMs,
                nextReviewAt = outcome.nextReviewAtMs,
                masteredAt = if (outcome.masteredAtMs != 0L) outcome.masteredAtMs else prev.masteredAt,
            )
        )
        analytics.track(
            DcEvents.REVIEW_ANSWERED,
            mapOf(
                DcEvents.P_EXAM to prev.examType,
                DcEvents.P_IS_CORRECT to isCorrect,
                DcEvents.P_NEW_STATE to outcome.state.name,
            ),
        )
        // Wrong-pool lifecycle events (IDs only, never content). A correct attempt that moves the
        // question out of the always-due INCORRECT/FORGOTTEN states resolved it; a wrong attempt
        // keeps it active and reschedules it.
        val wasActive = prev.state == QuestionLearnState.INCORRECT_ONCE.name ||
            prev.state == QuestionLearnState.INCORRECT_MULTIPLE.name ||
            prev.state == QuestionLearnState.FORGOTTEN.name
        analytics.track(
            if (isCorrect) DcEvents.RETRY_CORRECT else DcEvents.RETRY_WRONG,
            mapOf(DcEvents.P_QUESTION_ID to questionId, DcEvents.P_EXAM to prev.examType),
        )
        if (isCorrect && wasActive) {
            analytics.track(DcEvents.WRONG_POOL_RESOLVED, mapOf(DcEvents.P_QUESTION_ID to questionId))
        } else if (!isCorrect) {
            analytics.track(DcEvents.WRONG_POOL_RESCHEDULED, mapOf(DcEvents.P_QUESTION_ID to questionId))
        }
        outcome.state
    }

    /** Count of questions mastered through review (for progress UI). */
    suspend fun masteredCount(userId: String, exam: ExamType): Int = withContext(Dispatchers.IO) {
        dao.countStatesByStates(userId, exam.name, listOf(QuestionLearnState.MASTERED.name))
    }
}
