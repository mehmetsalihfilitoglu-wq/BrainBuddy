package com.edumio.app.dailychallenge

import com.edumio.app.core.ExamType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Proves the statistics write is tied to the DOMAIN completion transition, not to any screen.
 *
 * The result Activity may never launch — process death after the fifth answer, failed navigation, the
 * user backgrounding the app. If statistics depended on it, a completed challenge could leave the
 * statistics card empty forever. These tests drive the engine only; no Activity exists here at all.
 */
class DailyChallengeStatsWritePointTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val t0 = 1_700_000_000_000L

    /** Records what the engine asked to persist, and models the store's idempotence. */
    private class SpySink : DailyChallengeStatsSink {
        val stored = LinkedHashSet<String>()
        var attempts = 0
        var failNext = false
        override fun recordIfAbsent(completion: DailyChallengeEngine.Completion, nowMs: Long) {
            attempts++
            if (failNext) { failNext = false; throw RuntimeException("simulated analytics failure") }
            if (DailyChallengeStatsRecorder.shouldRecord(completion, stored)) {
                stored += DailyChallengeStatsRecorder.quizId(completion.examProfile, completion.localDate)
            }
        }
    }

    private fun engineWith(sink: DailyChallengeStatsSink, dao: InMemoryDailyChallengeDao, content: DcContentSource) =
        DailyChallengeEngine(dao, content, RecordingAnalytics(), {}, sink)

    private fun answerAll(engine: DailyChallengeEngine, userId: String, r: DailyChallengeEngine.Result, correct: Int) {
        val qids = r.challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
        runBlocking {
            qids.forEachIndexed { i, qid ->
                engine.submitAnswer(userId, r.challenge.localDate, qid, 0, i < correct, 1000, t0)
            }
        }
    }

    @Test
    fun completingTheChallengeWritesStatisticsWithNoActivityInvolved() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(ExamType.IMAT)
        val sink = SpySink()
        val engine = engineWith(sink, dao, content)

        val r = engine.getOrCreateToday("u", ExamType.IMAT, utc, t0)!!
        assertEquals("nothing recorded before completion", 0, sink.stored.size)

        answerAll(engine, "u", r, correct = 3)
        assertEquals("exactly one record on completion", 1, sink.stored.size)
        assertTrue(sink.stored.single().startsWith("dc_IMAT_"))
    }

    @Test
    fun aPartialChallengeWritesNothing() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val sink = SpySink()
        val engine = engineWith(sink, dao, FakeDcContent.forExam(ExamType.IMAT))
        val r = engine.getOrCreateToday("u", ExamType.IMAT, utc, t0)!!
        val qids = r.challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }
        // Answer only four of five — the session is not complete.
        qids.take(DailyChallengeBlueprint.CHALLENGE_SIZE - 1)
            .forEach { engine.submitAnswer("u", r.challenge.localDate, it, 0, true, 1000, t0) }
        assertEquals("a partial challenge is not a completed session", 0, sink.stored.size)
    }

    @Test
    fun reloadingACompletedChallengeRepairsAMissedWriteWithoutDuplicating() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val sink = SpySink()
        val engine = engineWith(sink, dao, FakeDcContent.forExam(ExamType.IMAT))

        val r = engine.getOrCreateToday("u", ExamType.IMAT, utc, t0)!!
        sink.failNext = true          // the authoritative write fails
        answerAll(engine, "u", r, correct = 5)
        assertEquals("write failed, so nothing stored yet", 0, sink.stored.size)

        // Next load of the completed challenge repairs it.
        engine.getOrCreateToday("u", ExamType.IMAT, utc, t0)
        assertEquals("repair path filled the gap", 1, sink.stored.size)

        // And repeated loads never duplicate.
        repeat(5) { engine.getOrCreateToday("u", ExamType.IMAT, utc, t0) }
        assertEquals("still exactly one record", 1, sink.stored.size)
    }

    @Test
    fun aFailedStatisticsWriteDoesNotRollBackOrCrashTheCompletion() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val sink = SpySink()
        val engine = engineWith(sink, dao, FakeDcContent.forExam(ExamType.IMAT))

        val r = engine.getOrCreateToday("u", ExamType.IMAT, utc, t0)!!
        sink.failNext = true
        answerAll(engine, "u", r, correct = 4)

        // The user's real progress stands regardless of bookkeeping.
        val after = engine.getOrCreateToday("u", ExamType.IMAT, utc, t0)!!
        assertTrue("challenge stays COMPLETED", after.completed)
        assertEquals("score preserved", 4, after.challenge.score)
    }

    @Test
    fun repeatedCompletionAttemptsRecordOnlyOnce() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val sink = SpySink()
        val engine = engineWith(sink, dao, FakeDcContent.forExam(ExamType.IMAT))
        val r = engine.getOrCreateToday("u", ExamType.IMAT, utc, t0)!!
        val qids = r.challenge.questionIdsCsv.split(",").filter { it.isNotBlank() }

        answerAll(engine, "u", r, correct = 3)
        // Re-submitting answers for an already-completed challenge must not add a second session.
        qids.forEach { engine.submitAnswer("u", r.challenge.localDate, it, 0, true, 1000, t0) }
        assertEquals(1, sink.stored.size)
    }
}
