package com.edumio.app.dailychallenge

import com.edumio.app.core.TestPerformance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Statistics must accumulate from the real v1 flow — the Daily Challenge — and must do so EXACTLY
 * once per completed challenge.
 *
 * The authoritative write lives on the domain completion transition in DailyChallengeEngine, not on
 * the result screen, so these tests exercise the recorder directly: that is the component whose
 * idempotence makes reopening, retrying, Activity recreation, process restore and the engine's repair
 * path all safe.
 */
class DailyChallengeStatsRecorderTest {

    private fun review(id: String, section: String, correct: Boolean) =
        DailyChallengeEngine.AnswerReview(
            questionId = id, section = section, stem = "s", chosenIndex = 0,
            correctIndex = if (correct) 0 else 1, isCorrect = correct, explanation = "",
        )

    private fun completion(
        exam: String = "IMAT",
        date: String = "2026-07-26",
        correct: Int = 3,
        size: Int = DailyChallengeBlueprint.CHALLENGE_SIZE,
    ): DailyChallengeEngine.Completion {
        val reviews = (0 until size).map { review("q$it", if (it % 2 == 0) "biology" else "chemistry", it < correct) }
        val sectionTotal = LinkedHashMap<String, Int>()
        val sectionCorrect = LinkedHashMap<String, Int>()
        reviews.forEach { r ->
            sectionTotal[r.section] = (sectionTotal[r.section] ?: 0) + 1
            if (r.isCorrect) sectionCorrect[r.section] = (sectionCorrect[r.section] ?: 0) + 1
        }
        return DailyChallengeEngine.Completion(
            localDate = date, examProfile = exam, score = correct, total = size,
            nextUnlockAtMs = 0L, streakCurrent = 1, reviews = reviews,
            sectionCorrect = sectionCorrect, sectionTotal = sectionTotal,
        )
    }

    // ── 1/2: first completion records; totals are real ──────────────────────────────────────────

    @Test
    fun aCompletedChallengeIsRecordedTheFirstTime() {
        assertTrue(DailyChallengeStatsRecorder.shouldRecord(completion(), existingQuizIds = emptySet()))
    }

    @Test
    fun fiveAnsweredQuestionsProduceTheCorrectTotals() {
        val p = DailyChallengeStatsRecorder.toPerformance(completion(correct = 3), nowMs = 1_000L)
        assertEquals("all five questions counted", 5, p.totalQuestions)
        assertEquals(3, p.correctCount)
        assertEquals(2, p.wrongCount)
        assertEquals("the Daily Challenge cannot be submitted blank", 0, p.blankCount)
        assertEquals(5, p.correctCount + p.wrongCount + p.blankCount)
        assertEquals(60f, p.accuracy)
        assertEquals(5, p.questionIds.size)
        assertEquals(2, p.wrongQuestionIds.size)
    }

    @Test
    fun correctAndIncorrectCountsTrackTheActualAnswers() {
        for (correct in 0..5) {
            val p = DailyChallengeStatsRecorder.toPerformance(completion(correct = correct), nowMs = 1L)
            assertEquals("correct=$correct", correct, p.correctCount)
            assertEquals("correct=$correct", 5 - correct, p.wrongCount)
            assertEquals("correct=$correct", correct * 20f, p.accuracy)
        }
    }

    // ── 3/4/5: idempotence — the core guarantee ─────────────────────────────────────────────────

    @Test
    fun reopeningACompletedChallengeDoesNotRecordItAgain() {
        val c = completion()
        val key = DailyChallengeStatsRecorder.quizId(c.examProfile, c.localDate)
        assertFalse(
            "an already-recorded challenge must never be written twice",
            DailyChallengeStatsRecorder.shouldRecord(c, existingQuizIds = setOf(key)),
        )
    }

    @Test
    fun activityRecreationProcessRestoreAndRetryAllCollapseToOneRecord() {
        // Simulates the store accumulating: first call records, every later call is refused. This is
        // what makes rotation, process death, the result screen reopening and the engine's repair path
        // safe — none of them can inflate the totals.
        val c = completion()
        val stored = LinkedHashSet<String>()
        var writes = 0
        repeat(25) {
            if (DailyChallengeStatsRecorder.shouldRecord(c, stored)) {
                stored += DailyChallengeStatsRecorder.quizId(c.examProfile, c.localDate)
                writes++
            }
        }
        assertEquals("exactly one record after 25 attempts", 1, writes)
        assertEquals(1, stored.size)
    }

    @Test
    fun theDedupKeyIsTheChallengeIdentity() {
        assertEquals("dc_IMAT_2026-07-26", DailyChallengeStatsRecorder.quizId("IMAT", "2026-07-26"))
    }

    // ── 6: a partial challenge is not a completed session ───────────────────────────────────────

    @Test
    fun aPartiallyAnsweredChallengeIsNeverRecorded() {
        for (answered in 0 until DailyChallengeBlueprint.CHALLENGE_SIZE) {
            val partial = completion(size = answered)
            assertFalse(
                "$answered/5 answered must not count as a completed session",
                DailyChallengeStatsRecorder.shouldRecord(partial, emptySet()),
            )
        }
    }

    // ── 7: exam isolation ───────────────────────────────────────────────────────────────────────

    @Test
    fun eachExamGetsItsOwnRecordOnTheSameDay() {
        val day = "2026-07-26"
        val keys = listOf("IMAT", "TIL_I", "CENT_S")
            .map { DailyChallengeStatsRecorder.quizId(it, day) }
        assertEquals("three exams must not collide on one day", 3, keys.toSet().size)

        // Recording IMAT must not suppress TIL-I or CEnT-S.
        val stored = setOf(DailyChallengeStatsRecorder.quizId("IMAT", day))
        assertFalse(DailyChallengeStatsRecorder.shouldRecord(completion(exam = "IMAT", date = day), stored))
        assertTrue(DailyChallengeStatsRecorder.shouldRecord(completion(exam = "TIL_I", date = day), stored))
        assertTrue(DailyChallengeStatsRecorder.shouldRecord(completion(exam = "CENT_S", date = day), stored))
    }

    @Test
    fun consecutiveDaysAccumulateSeparately() {
        val stored = LinkedHashSet<String>()
        val days = listOf("2026-07-24", "2026-07-25", "2026-07-26")
        var writes = 0
        for (d in days) {
            val c = completion(date = d)
            if (DailyChallengeStatsRecorder.shouldRecord(c, stored)) {
                stored += DailyChallengeStatsRecorder.quizId(c.examProfile, c.localDate); writes++
            }
        }
        assertEquals("one completed session per day", 3, writes)
    }

    // ── 8: section data maps onto the model the UI already consumes ─────────────────────────────

    @Test
    fun sectionCountsMapOntoTopicCountsWithoutInventingData() {
        val p = DailyChallengeStatsRecorder.toPerformance(completion(correct = 3), nowMs = 1L)
        assertEquals("only the sections actually played", setOf("biology", "chemistry"), p.byTopicCounts.keys)
        val totals = p.byTopicCounts.values.sumOf { it.total }
        assertEquals("section totals must equal the challenge size", 5, totals)
        val correct = p.byTopicCounts.values.sumOf { it.correct }
        assertEquals("section correct must equal the score", 3, correct)
        p.byTopicCounts.forEach { (section, tc) ->
            assertTrue("$section: counts must be internally consistent", tc.correct + tc.wrong + tc.blank == tc.total)
        }
    }

    // ── 9: nothing fabricated ───────────────────────────────────────────────────────────────────

    @Test
    fun aRecordedPerformanceNeverContainsPlaceholderOrZeroedStatistics() {
        val p: TestPerformance = DailyChallengeStatsRecorder.toPerformance(completion(correct = 5), nowMs = 42L)
        assertTrue("a real timestamp is stored", p.tsMs > 0)
        assertTrue("quizId identifies the challenge", p.quizId.startsWith("dc_"))
        assertEquals(100f, p.accuracy)
        assertTrue("a perfect score passes", p.passed)
        // A zero-question record would poison the averages the card shows; it must be impossible.
        assertTrue(p.totalQuestions > 0)
    }

    // ── 10: legacy / corrupt state fails safe ───────────────────────────────────────────────────

    @Test
    fun anEmptyOrUnknownStoreIsTreatedAsNothingRecorded() {
        // A corrupt store surfaces as an empty id set (see AnalyticsStore.performanceQuizIds), which
        // must mean "record it", never "crash" and never "silently skip forever".
        assertTrue(DailyChallengeStatsRecorder.shouldRecord(completion(), emptySet()))
        assertTrue(
            "unrelated ids must not block a new challenge",
            DailyChallengeStatsRecorder.shouldRecord(completion(), setOf("quiz_legacy_1", "dc_IMAT_1999-01-01")),
        )
    }

    @Test
    fun theNoOpSinkIsInertAndNeverThrows() {
        DailyChallengeStatsSink.NoOp.recordIfAbsent(completion(), 1L)
    }
}
