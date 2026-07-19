package com.edumio.app.dailychallenge

import com.edumio.app.core.ExamType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone
import kotlin.random.Random

/**
 * Wrong-question pool + mastery invariants (see wrong_question_pool_spec.md), proven on the real
 * engines over the in-memory store — plus randomized multi-thousand-attempt simulations of the
 * spaced-repetition state machine.
 */
class WrongPoolInvariantsTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val t0 = 1_700_000_000_000L
    private val activeStates = listOf(
        QuestionLearnState.INCORRECT_ONCE.name, QuestionLearnState.INCORRECT_MULTIPLE.name,
        QuestionLearnState.FORGOTTEN.name,
    )

    private fun ids(csv: String) = csv.split(",").filter { it.isNotBlank() }

    /** Serve today's challenge and answer [wrongCount] questions wrong (rest correct). */
    private suspend fun seedWrong(
        engine: DailyChallengeEngine, userId: String, wrongCount: Int,
    ): Pair<DailyChallengeEngine.Result, List<String>> {
        val gen = engine.getOrCreateToday(userId, ExamType.IMAT, utc, t0)!!
        val qids = ids(gen.challenge.questionIdsCsv)
        qids.forEachIndexed { i, qid ->
            engine.submitAnswer(userId, gen.challenge.localDate, qid, 0, isCorrect = i >= wrongCount, timeMs = 100, nowMs = t0)
        }
        return gen to qids.take(wrongCount)
    }

    // ── a wrong answer enters the pool once; answering wrong again never duplicates the row ────────
    @Test
    fun wrongAnswer_createsExactlyOnePoolRow() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val engine = testEngine(dao, content)
        val review = ReviewEngine(dao, content, RecordingAnalytics())
        val (_, wrong) = seedWrong(engine, "u1", wrongCount = 1)
        val qid = wrong.single()

        assertEquals(1, dao.getStatesByStatesAllExams("u1", activeStates).count { it.questionId == qid })
        // wrong retry → still exactly one row, attempt count incremented, rescheduled
        review.submitReview("u1", qid, isCorrect = false, nowMs = t0)
        val rows = dao.getStatesByStatesAllExams("u1", activeStates).filter { it.questionId == qid }
        assertEquals(1, rows.size)
        assertEquals(QuestionLearnState.INCORRECT_MULTIPLE.name, rows.single().state)
        assertEquals(2, rows.single().timesIncorrect)
        assertTrue("rescheduled into the future", rows.single().nextReviewAt > t0)
    }

    // ── correct retry leaves the ACTIVE pool exactly once; history is preserved ────────────────────
    @Test
    fun correctRetry_removesFromActivePoolExactlyOnce() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val an = RecordingAnalytics()
        val engine = testEngine(dao, content, an)
        val review = ReviewEngine(dao, content, an)
        val (_, wrong) = seedWrong(engine, "u1", wrongCount = 1)
        val qid = wrong.single()

        review.submitReview("u1", qid, isCorrect = true, nowMs = t0)
        assertEquals("left the active pool", 0, dao.getStatesByStatesAllExams("u1", activeStates).count { it.questionId == qid })
        val st = dao.getState("u1", qid)!!
        assertEquals(QuestionLearnState.NEEDS_REVISION.name, st.state) // spaced reappearance, per mastery rules
        assertEquals("history preserved", 1, st.timesIncorrect)
        assertEquals(1, an.count(DcEvents.WRONG_POOL_RESOLVED))

        // rapid double submission: the second correct advances the ladder but resolves nothing again
        review.submitReview("u1", qid, isCorrect = true, nowMs = t0 + 1000)
        assertEquals("resolved exactly once", 1, an.count(DcEvents.WRONG_POOL_RESOLVED))
    }

    // ── viewing a solution performs no state change (read paths are pure) ──────────────────────────
    @Test
    fun viewingSolution_neverRemovesFromPool() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val engine = testEngine(dao, content)
        val review = ReviewEngine(dao, content, RecordingAnalytics())
        val (_, wrong) = seedWrong(engine, "u1", wrongCount = 2)

        val before = dao.getStatesByStatesAllExams("u1", activeStates).map { it.questionId to it.state }.toSet()
        // The solution screen's data reads: the retry-item lookup and latest-answer lookup.
        for (qid in wrong) {
            assertNotNull(review.getReviewItem("u1", qid))
            dao.getLatestAnswerForQuestion("u1", qid)
        }
        val after = dao.getStatesByStatesAllExams("u1", activeStates).map { it.questionId to it.state }.toSet()
        assertEquals("read paths mutated nothing", before, after)
        assertEquals(2, after.size)
    }

    // ── retry lookup can never surface a new (never-served) question ───────────────────────────────
    @Test
    fun retry_neverSurfacesUnservedQuestion() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val review = ReviewEngine(dao, content, RecordingAnalytics())
        assertNull(review.getReviewItem("u1", "IMAT-biology-0")) // exists in the bank, never served
    }

    // ── review/retry never consumes new-question quota or blueprint deficits ───────────────────────
    @Test
    fun review_neverTouchesQuotaOrDeficits() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val engine = testEngine(dao, content)
        val review = ReviewEngine(dao, content, RecordingAnalytics())
        val (gen, wrong) = seedWrong(engine, "u1", wrongCount = 3)

        val challengesBefore = dao.countChallengesForUser("u1")
        val seenBefore = dao.getSeenQuestionIds("u1", ExamType.IMAT.name).toSet()
        val deficitsBefore = dao.getDeficits("u1", ExamType.IMAT.name).toSet()

        repeat(20) { i -> wrong.forEach { review.submitReview("u1", it, isCorrect = i % 2 == 0, nowMs = t0 + i * 1000L) } }

        assertEquals(challengesBefore, dao.countChallengesForUser("u1"))
        assertEquals(seenBefore, dao.getSeenQuestionIds("u1", ExamType.IMAT.name).toSet())
        assertEquals("deficit ledger untouched by review", deficitsBefore, dao.getDeficits("u1", ExamType.IMAT.name).toSet())
        assertEquals("today's challenge unchanged", gen.challenge.questionIdsCsv,
            dao.getChallengeForDay("u1", gen.challenge.localDate)!!.questionIdsCsv)
    }

    // ── process recreation restores retry state (fresh engine instances, same store) ───────────────
    @Test
    fun processRecreation_restoresRetryState() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val (_, wrong) = seedWrong(testEngine(dao, content), "u1", wrongCount = 1)
        val qid = wrong.single()

        ReviewEngine(dao, content, RecordingAnalytics()).submitReview("u1", qid, isCorrect = false, nowMs = t0)
        // "process death": brand-new engine objects over the same persistent store
        val revived = ReviewEngine(dao, content, RecordingAnalytics())
        val item = revived.getReviewItem("u1", qid)!!
        assertEquals(QuestionLearnState.INCORRECT_MULTIPLE, item.state)
        assertEquals(2, dao.getState("u1", qid)!!.timesIncorrect)
    }

    // ── account isolation: one account's wrong pool never appears under another ────────────────────
    @Test
    fun wrongPool_isAccountIsolated() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val engine = testEngine(dao, content)
        seedWrong(engine, "alice-uid", wrongCount = 2)

        assertEquals(2, dao.getStatesByStatesAllExams("alice-uid", activeStates).size)
        assertEquals(0, dao.getStatesByStatesAllExams("bob-uid", activeStates).size)
        assertNull(ReviewEngine(dao, content, RecordingAnalytics()).getReviewItem("bob-uid",
            dao.getStatesByStatesAllExams("alice-uid", activeStates).first().questionId))
    }

    // ── analytics carry ids only — never stems, options, or solution text ──────────────────────────
    @Test
    fun analytics_neverCarryContent() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val an = RecordingAnalytics()
        val engine = testEngine(dao, content, an)
        val review = ReviewEngine(dao, content, an)
        val (gen, wrong) = seedWrong(engine, "u1", wrongCount = 2)
        wrong.forEach { review.submitReview("u1", it, isCorrect = false, nowMs = t0) }

        val stems = ids(gen.challenge.questionIdsCsv).map { "Q $it" } // FakeDcContent stem text
        synchronized(an.events) {
            for ((_, params) in an.events) for (v in params.values) {
                val s = v?.toString() ?: continue
                assertTrue("analytics param leaks content: $s", stems.none { s.contains(it) })
                assertTrue("suspiciously long analytics param: $s", s.length <= 120)
            }
        }
    }

    // ── randomized mastery simulation: thousands of attempts, machine invariants always hold ───────
    @Test
    fun randomizedReviewSimulation_invariantsHold() {
        val rng = Random(42)
        repeat(200) {
            var state = if (rng.nextBoolean()) QuestionLearnState.INCORRECT_ONCE else QuestionLearnState.NEEDS_REVISION
            var streak = 0
            var now = t0
            var masteredAtAttempt = -1
            for (attempt in 0 until 40) {
                val correct = rng.nextBoolean()
                val out = ReviewScheduler.onReview(state, streak, correct, now)
                // machine invariants
                assertTrue(out.consecutiveCorrect in 0..ReviewScheduler.MASTERY_STREAK + 5)
                if (!correct) {
                    assertEquals(QuestionLearnState.INCORRECT_MULTIPLE, out.state)
                    assertEquals(0, out.consecutiveCorrect)
                    assertTrue(out.nextReviewAtMs > now)
                } else if (out.state == QuestionLearnState.MASTERED) {
                    assertTrue(out.consecutiveCorrect >= ReviewScheduler.MASTERY_STREAK)
                    if (masteredAtAttempt < 0) masteredAtAttempt = attempt
                    assertFalse("mastered is never due again", ReviewScheduler.isDue(out.state, out.nextReviewAtMs, now + 1_000_000_000L))
                } else {
                    assertEquals(QuestionLearnState.NEEDS_REVISION, out.state)
                    assertTrue("scheduled in the future", out.nextReviewAtMs > now)
                }
                state = out.state
                streak = out.consecutiveCorrect
                now += 86_400_000L
                if (state == QuestionLearnState.MASTERED) break
            }
        }
        // 5 consecutive corrects from the start always master (deterministic path)
        var st = QuestionLearnState.INCORRECT_ONCE; var streak = 0; var now = t0
        repeat(ReviewScheduler.MASTERY_STREAK) {
            val out = ReviewScheduler.onReview(st, streak, true, now)
            st = out.state; streak = out.consecutiveCorrect; now += 86_400_000L
        }
        assertEquals(QuestionLearnState.MASTERED, st)
    }
}
