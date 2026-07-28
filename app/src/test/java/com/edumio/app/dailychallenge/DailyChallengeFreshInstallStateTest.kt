package com.edumio.app.dailychallenge

import com.edumio.app.core.ExamType
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter.CardState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/**
 * Fresh-install regression guard for the "clean install shows Tamamlandı / empty" bug.
 *
 * The device symptom was: on a clean install, before answering anything, Home showed
 * "Bugünlük yeni soru kalmadı" + "Tamamlandı" — for TIL-I / CEnT-S especially. Root cause: today()
 * returned null (the exam bank had not finished seeding yet), and Home mis-rendered that null as
 * "completed". These tests pin the correct behaviour at both layers:
 *  - a fresh user on a SEEDED pool gets an AVAILABLE challenge (0/5, not completed) for every exam,
 *  - a null result (empty/unbuilt bank) maps to ERROR (supported) / UNAVAILABLE (unsupported) — NEVER
 *    to COMPLETED,
 *  - the daily limit is a fixed 5 (never a user goal).
 */
class DailyChallengeFreshInstallStateTest {

    private val utc: TimeZone = TimeZone.getTimeZone("UTC")
    private val t0 = 1_700_000_000_000L
    private fun ids(csv: String) = csv.split(",").filter { it.isNotBlank() }

    private fun assertFreshExamIsAvailable(exam: ExamType) = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val content = FakeDcContent.forExam(exam, perSection = 12)
        val engine = testEngine(dao, content)

        val r = engine.getOrCreateToday("fresh_${exam.name}", exam, utc, t0)!!

        assertEquals("exactly 5 new questions", 5, r.questions.size)
        assertEquals("a fresh challenge has 0 answered", 0, r.answered)
        assertFalse("a fresh challenge is NOT completed", r.completed)
        // Pool isolation: every served question id belongs to THIS exam's bank.
        assertTrue(
            "fresh $exam challenge must draw only from the $exam pool",
            ids(r.challenge.questionIdsCsv).all { it.startsWith("${exam.name}-") },
        )
        // The home card for a fresh challenge is AVAILABLE — never COMPLETED.
        val state = DailyChallengeHomePresenter.cardState(
            supported = true, answered = r.answered, total = r.total, completed = r.completed,
        )
        assertEquals(DailyChallengeHomePresenter.CardState.AVAILABLE, state)
        assertFalse("fresh must never be COMPLETED", state == DailyChallengeHomePresenter.CardState.COMPLETED)
    }

    @Test fun freshImatUser_challengeIsAvailable_notCompleted() = assertFreshExamIsAvailable(ExamType.IMAT)

    @Test fun freshTilIUser_challengeIsAvailable_notCompleted() = assertFreshExamIsAvailable(ExamType.TIL_I)

    @Test fun freshCentSUser_challengeIsAvailable_notCompleted() = assertFreshExamIsAvailable(ExamType.CENT_S)

    // ── empty / unbuilt bank → null → ERROR, never "completed" ─────────────────────────────────────
    @Test
    fun emptyBank_supportedExam_returnsNull_andMapsToError_notCompleted() = runBlocking {
        val dao = InMemoryDailyChallengeDao()
        val emptyContent = FakeDcContent(emptyMap(), emptyMap()) // bank not seeded yet
        val engine = testEngine(dao, emptyContent)

        val r = engine.getOrCreateToday("u_empty", ExamType.TIL_I, utc, t0)
        assertNull("an empty pool must not fabricate a challenge", r)
        assertEquals("no challenge row is created for an empty pool", 0, dao.countChallengesForUser("u_empty"))

        // A supported-but-null result is a controlled ERROR (retry), NOT completed.
        val state = DailyChallengeHomePresenter.emptyState(supported = true)
        assertEquals(DailyChallengeHomePresenter.CardState.ERROR, state)
        assertFalse("null must never mean COMPLETED", state == DailyChallengeHomePresenter.CardState.COMPLETED)
    }

    @Test
    fun unsupportedExam_emptyState_isUnavailable_notError() {
        assertEquals(
            DailyChallengeHomePresenter.CardState.UNAVAILABLE,
            DailyChallengeHomePresenter.emptyState(supported = false),
        )
    }

    // ── the home state machine: only 5/5 is COMPLETED ──────────────────────────────────────────────
    @Test
    fun stateMachine_completedOnlyAtFiveOfFive() {
        assertEquals(CardState.AVAILABLE, DailyChallengeHomePresenter.cardState(true, 0, 5, false))   // fresh
        assertEquals(CardState.IN_PROGRESS, DailyChallengeHomePresenter.cardState(true, 4, 5, false)) // 4/5 is NOT completed
        assertEquals(CardState.COMPLETED, DailyChallengeHomePresenter.cardState(true, 5, 5, false))   // 5/5 answered
        assertEquals(CardState.COMPLETED, DailyChallengeHomePresenter.cardState(true, 5, 5, true))    // explicit completion flag
        assertEquals(CardState.UNAVAILABLE, DailyChallengeHomePresenter.cardState(false, 0, 5, false))// unsupported exam
    }

    // ── the daily limit is FIXED at 5 (not a user-chosen goal) ─────────────────────────────────────
    @Test
    fun dailyLimitIsFixedFive() {
        assertEquals(5, DailyChallengeBlueprint.CHALLENGE_SIZE)
    }
}
