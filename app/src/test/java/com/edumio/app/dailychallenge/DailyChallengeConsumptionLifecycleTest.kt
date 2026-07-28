package com.edumio.app.dailychallenge

import com.edumio.app.core.ExamType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Proves the consumption lifecycle: a question is spent when it is ANSWERED, never merely because the
 * app was opened and today's challenge was generated.
 *
 * Before: generation immediately wrote SEEN_ONCE for all five questions, so opening Home and closing
 * the app burned five unseen questions the student never saw an answer screen for.
 */
class DailyChallengeConsumptionLifecycleTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val t0 = 1_700_000_000_000L
    private val nextDay = t0 + 26L * 3600 * 1000

    private fun ids(csv: String) = csv.split(",").filter { it.isNotBlank() }

    private fun setup(exam: ExamType = ExamType.IMAT) =
        InMemoryDailyChallengeDao() to FakeDcContent.forExam(exam, perSection = 12)

    @Test
    fun generatingTheChallenge_consumesNothing() = runBlocking {
        val (dao, content) = setup()
        val engine = testEngine(dao, content)
        val user = "u1"

        val gen = engine.getOrCreateToday(user, ExamType.IMAT, utc, t0)!!

        assertEquals("challenge is generated", 5, gen.questions.size)
        assertEquals(
            "opening the app must NOT consume any question",
            0, dao.getSeenQuestionIds(user, ExamType.IMAT.name).size,
        )
    }

    @Test
    fun closingWithoutAnswering_losesNothing_andTheSameChallengeReturns() = runBlocking {
        val (dao, content) = setup()
        val user = "u1"
        val gen = testEngine(dao, content).getOrCreateToday(user, ExamType.IMAT, utc, t0)!!
        val originalIds = ids(gen.challenge.questionIdsCsv)

        // App closed with zero answers, then reopened (fresh engine, same persisted store).
        val reopened = testEngine(dao, content).getOrCreateToday(user, ExamType.IMAT, utc, t0)!!

        assertEquals("the same unfinished challenge comes back", originalIds, ids(reopened.challenge.questionIdsCsv))
        assertEquals("still nothing consumed", 0, dao.getSeenQuestionIds(user, ExamType.IMAT.name).size)
        assertEquals("still exactly one challenge for the day", 1, dao.countChallengesForUser(user))
        assertEquals(0, reopened.answered)
        assertFalse(reopened.completed)
    }

    @Test
    fun answering_consumesExactlyTheAnsweredQuestions() = runBlocking {
        val (dao, content) = setup()
        val engine = testEngine(dao, content)
        val user = "u1"
        val gen = engine.getOrCreateToday(user, ExamType.IMAT, utc, t0)!!
        val qids = ids(gen.challenge.questionIdsCsv)

        engine.submitAnswer(user, gen.challenge.localDate, qids[0], 0, true, 100, t0)
        engine.submitAnswer(user, gen.challenge.localDate, qids[1], 0, false, 100, t0)

        val seen = dao.getSeenQuestionIds(user, ExamType.IMAT.name).toSet()
        assertEquals("only the two answered questions are consumed", setOf(qids[0], qids[1]), seen)
    }

    @Test
    fun partialProgressSurvivesRestart_andResumesWhereItStopped() = runBlocking {
        val (dao, content) = setup()
        val user = "u1"
        val gen = testEngine(dao, content).getOrCreateToday(user, ExamType.IMAT, utc, t0)!!
        val qids = ids(gen.challenge.questionIdsCsv)
        testEngine(dao, content).submitAnswer(user, gen.challenge.localDate, qids[0], 0, true, 100, t0)
        testEngine(dao, content).submitAnswer(user, gen.challenge.localDate, qids[1], 0, true, 100, t0)

        val afterRestart = testEngine(dao, content).getOrCreateToday(user, ExamType.IMAT, utc, t0)!!

        assertEquals("same challenge", ids(gen.challenge.questionIdsCsv), ids(afterRestart.challenge.questionIdsCsv))
        assertEquals("progress preserved", 2, afterRestart.answered)
        assertEquals(
            "resumes at the next unanswered question",
            2, DailyChallengeHomePresenter.resumeIndex(afterRestart.answered, afterRestart.total, afterRestart.completed),
        )
        assertEquals("only answered questions consumed", 2, dao.getSeenQuestionIds(user, ExamType.IMAT.name).size)
    }

    @Test
    fun completing_consumesAllFiveExactlyOnce_andNoSecondChallengeSameDay() = runBlocking {
        val (dao, content) = setup()
        val engine = testEngine(dao, content)
        val user = "u1"
        val gen = engine.getOrCreateToday(user, ExamType.IMAT, utc, t0)!!
        val qids = ids(gen.challenge.questionIdsCsv)

        var last = gen
        qids.forEach { last = engine.submitAnswer(user, gen.challenge.localDate, it, 0, true, 100, t0)!! }

        assertTrue("completed after 5", last.completed)
        assertEquals("all five consumed", 5, dao.getSeenQuestionIds(user, ExamType.IMAT.name).size)

        // Re-answering must not double-consume, and reopening must not create a second challenge.
        engine.submitAnswer(user, gen.challenge.localDate, qids[0], 0, true, 100, t0)
        assertEquals("consumed exactly once", 5, dao.getSeenQuestionIds(user, ExamType.IMAT.name).size)

        val reopened = engine.getOrCreateToday(user, ExamType.IMAT, utc, t0)!!
        assertEquals(gen.challenge.questionIdsCsv, reopened.challenge.questionIdsCsv)
        assertEquals("still one challenge for the day", 1, dao.countChallengesForUser(user))
    }

    @Test
    fun unansweredQuestionsRemainEligible_onALaterDay() = runBlocking {
        val (dao, content) = setup()
        val user = "u1"
        val day1 = testEngine(dao, content).getOrCreateToday(user, ExamType.IMAT, utc, t0)!!
        val day1Ids = ids(day1.challenge.questionIdsCsv).toSet()
        // Never answered anything on day 1.

        val day2 = testEngine(dao, content).getOrCreateToday(user, ExamType.IMAT, utc, nextDay)
        assertNotNull(day2)
        assertEquals("still exactly 5", 5, day2!!.questions.size)
        assertEquals("nothing was ever consumed", 0, dao.getSeenQuestionIds(user, ExamType.IMAT.name).size)
        // The point: day 1's untouched questions were NOT burned, so they are still in the unseen pool.
        val stillUnseen = day1Ids.none { it in dao.getSeenQuestionIds(user, ExamType.IMAT.name) }
        assertTrue("day-1 questions are not lost", stillUnseen)
    }

    @Test
    fun answeredQuestionsAreNotServedAgainOnALaterDay() = runBlocking {
        val (dao, content) = setup()
        val engine = testEngine(dao, content)
        val user = "u1"
        val day1 = engine.getOrCreateToday(user, ExamType.IMAT, utc, t0)!!
        val answered = ids(day1.challenge.questionIdsCsv)
        answered.forEach { engine.submitAnswer(user, day1.challenge.localDate, it, 0, true, 100, t0) }

        val day2 = engine.getOrCreateToday(user, ExamType.IMAT, utc, nextDay)!!
        assertTrue(
            "an answered question must never come back as a NEW question",
            ids(day2.challenge.questionIdsCsv).none { it in answered },
        )
    }

    @Test
    fun stillExactlyFive_andExamIsolationHolds() = runBlocking {
        for (exam in listOf(ExamType.IMAT, ExamType.TIL_I, ExamType.CENT_S)) {
            val (dao, content) = setup(exam)
            val r = testEngine(dao, content).getOrCreateToday("u_$exam", exam, utc, t0)!!
            assertEquals("$exam: exactly 5", 5, r.questions.size)
            assertTrue(
                "$exam: only its own pool",
                ids(r.challenge.questionIdsCsv).all { it.startsWith(exam.name + "-") },
            )
        }
    }
}
