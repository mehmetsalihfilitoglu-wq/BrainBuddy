package com.edumio.app.dailychallenge

import com.edumio.app.core.ExamType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the reported bug: answer a wrong question correctly, the app says it left the
 * pool, and the card is still on screen.
 *
 * The data was never wrong. A correct retry moves the row INCORRECT_* -> NEEDS_REVISION with
 * nextReviewAt a day ahead, and the hub rendered `active + scheduled` unfiltered — so the row stayed
 * visible. The existing WrongPoolInvariantsTest only asserted removal from `activeStates`, which is why
 * it stayed green while the screen was wrong: it never checked the set the user actually sees.
 *
 * These tests assert against the RENDERED set — the exact expression WrongQuestionsActivity builds —
 * so the user-visible contract is what is protected.
 */
class WrongPoolRenderedSetTest {

    private val t0 = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    // Mirrors WrongQuestionsActivity: activeStates (:48-51) and scheduledStates (:52).
    private val activeStates = listOf(
        QuestionLearnState.INCORRECT_ONCE.name,
        QuestionLearnState.INCORRECT_MULTIPLE.name,
        QuestionLearnState.FORGOTTEN.name,
    )
    private val scheduledStates = listOf(QuestionLearnState.NEEDS_REVISION.name)
    /** After the fix, "Çözüldü" counts MASTERED only (:53). */
    private val resolvedStates = listOf(QuestionLearnState.MASTERED.name)

    /** The list the screen renders: active + scheduled that are ACTUALLY DUE (WrongQuestionsActivity:171). */
    private suspend fun renderedPool(dao: DailyChallengeDao, userId: String, nowMs: Long): List<String> {
        val active = dao.getStatesByStatesAllExams(userId, activeStates)
        val scheduled = dao.getStatesByStatesAllExams(userId, scheduledStates)
        return (active + scheduled.filter { nowMs >= it.nextReviewAt }).map { it.questionId }
    }

    private suspend fun resolvedCount(dao: DailyChallengeDao, userId: String): Int =
        dao.getStatesByStatesAllExams(userId, resolvedStates).size

    private fun fixture(): Triple<InMemoryDailyChallengeDao, DailyChallengeEngine, ReviewEngine> {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val an = RecordingAnalytics()
        return Triple(dao, testEngine(dao, content, an), ReviewEngine(dao, content, an))
    }

    /** Answers today's challenge, getting exactly one question wrong. Returns that question id. */
    private suspend fun seedOneWrong(dao: InMemoryDailyChallengeDao, engine: DailyChallengeEngine, userId: String): String {
        val r = engine.getOrCreateToday(userId, ExamType.IMAT, java.util.TimeZone.getTimeZone("UTC"), t0)!!
        val qids = r.challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
        qids.forEachIndexed { i, qid ->
            engine.submitAnswer(userId, r.challenge.localDate, qid, 0, i != 0, 1000, t0)
        }
        return qids.first() // the only one answered incorrectly
    }

    @Test
    fun aWrongAnswerAppearsInTheRenderedPool() = runBlocking {
        val (dao, engine, _) = fixture()
        val qid = seedOneWrong(dao, engine, "u1")
        assertTrue("a wrong question must be visible to the student", renderedPool(dao, "u1", t0).contains(qid))
    }

    @Test
    fun theFirstCorrectRetryMovesItToNeedsRevision() = runBlocking {
        val (dao, engine, review) = fixture()
        val qid = seedOneWrong(dao, engine, "u1")
        review.submitReview("u1", qid, isCorrect = true, nowMs = t0)
        val st = dao.getState("u1", qid)!!
        assertEquals(QuestionLearnState.NEEDS_REVISION.name, st.state)
        assertTrue("it must be scheduled into the future", st.nextReviewAt > t0)
    }

    @Test
    fun itDisappearsFromTheRenderedPoolUntilDue() = runBlocking {
        val (dao, engine, review) = fixture()
        val qid = seedOneWrong(dao, engine, "u1")
        assertTrue(renderedPool(dao, "u1", t0).contains(qid))

        review.submitReview("u1", qid, isCorrect = true, nowMs = t0)

        // THE BUG: this used to still contain qid, contradicting "aktif yanlış havuzundan çıktı".
        assertFalse(
            "a correctly-retried question must leave the visible pool",
            renderedPool(dao, "u1", t0).contains(qid),
        )
    }

    @Test
    fun aFreshReadBeforeNextReviewAtStillOmitsIt() = runBlocking {
        val (dao, engine, review) = fixture()
        val qid = seedOneWrong(dao, engine, "u1")
        review.submitReview("u1", qid, isCorrect = true, nowMs = t0)

        // Re-reading the store (screen reopened / app restarted) must give the same answer — this is
        // what distinguishes a real fix from merely hiding a row in an in-memory list.
        val st = dao.getState("u1", qid)
        assertNotNull("the row is retained, not deleted — history is preserved", st)
        for (elapsed in listOf(0L, 1_000L, dayMs - 1)) {
            assertFalse(
                "still not due after ${elapsed}ms",
                renderedPool(dao, "u1", t0 + elapsed).contains(qid),
            )
        }
    }

    @Test
    fun itReappearsOnceNextReviewAtIsReached() = runBlocking {
        val (dao, engine, review) = fixture()
        val qid = seedOneWrong(dao, engine, "u1")
        review.submitReview("u1", qid, isCorrect = true, nowMs = t0)
        val due = dao.getState("u1", qid)!!.nextReviewAt

        assertFalse(renderedPool(dao, "u1", due - 1).contains(qid))
        assertTrue(
            "spaced repetition must still surface it when due — the fix hides it, never drops it",
            renderedPool(dao, "u1", due).contains(qid),
        )
    }

    @Test
    fun needsRevisionDoesNotIncrementTheResolvedCounter() = runBlocking {
        val (dao, engine, review) = fixture()
        val qid = seedOneWrong(dao, engine, "u1")
        assertEquals("nothing resolved yet", 0, resolvedCount(dao, "u1"))

        review.submitReview("u1", qid, isCorrect = true, nowMs = t0)
        assertEquals(
            "one correct retry is scheduled, NOT resolved — it must not inflate \"Çözüldü\"",
            0, resolvedCount(dao, "u1"),
        )
    }

    @Test
    fun onlyMasteryResolvesTheQuestionAndRemovesItForGood() = runBlocking {
        val (dao, engine, review) = fixture()
        val qid = seedOneWrong(dao, engine, "u1")

        // MASTERY_STREAK consecutive correct reviews, each performed when the item is due.
        var now = t0
        repeat(ReviewScheduler.MASTERY_STREAK) {
            review.submitReview("u1", qid, isCorrect = true, nowMs = now)
            now = dao.getState("u1", qid)!!.nextReviewAt
        }

        assertEquals(QuestionLearnState.MASTERED.name, dao.getState("u1", qid)!!.state)
        assertEquals("now it counts as resolved", 1, resolvedCount(dao, "u1"))
        assertFalse(
            "a mastered question never returns to the pool",
            renderedPool(dao, "u1", Long.MAX_VALUE / 2).contains(qid),
        )
    }

    @Test
    fun aWrongRetryPutsItStraightBackIntoTheVisiblePool() = runBlocking {
        val (dao, engine, review) = fixture()
        val qid = seedOneWrong(dao, engine, "u1")
        review.submitReview("u1", qid, isCorrect = true, nowMs = t0)
        assertFalse(renderedPool(dao, "u1", t0).contains(qid))

        // Missing it again must make it immediately visible, not silently scheduled away.
        review.submitReview("u1", qid, isCorrect = false, nowMs = t0 + 100)
        assertEquals(QuestionLearnState.INCORRECT_MULTIPLE.name, dao.getState("u1", qid)!!.state)
        assertTrue(renderedPool(dao, "u1", t0 + 100).contains(qid))
    }

    /**
     * The success message is gated on the RESULT of the write. submitReview returns null when there is
     * no state row to update — the same signal the screen now uses to withhold
     * "aktif yanlış havuzundan çıktı" and show the save-failed message instead.
     */
    @Test
    fun submitReviewReportsFailureForAnUnknownQuestion() = runBlocking {
        val (_, _, review) = fixture()
        val outcome = review.submitReview("u1", "no-such-question", isCorrect = true, nowMs = t0)
        assertEquals("no row -> no transition -> no success message", null, outcome)
    }
}
