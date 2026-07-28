package com.edumio.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the home-card / flow presenter (no Android deps). */
class DailyChallengeHomePresenterTest {

    private val S = DailyChallengeHomePresenter

    @Test
    fun cardStateCoversEveryHomeState() {
        assertEquals(DailyChallengeHomePresenter.CardState.UNAVAILABLE,
            S.cardState(supported = false, answered = 0, total = 5, completed = false))
        assertEquals(DailyChallengeHomePresenter.CardState.AVAILABLE,
            S.cardState(supported = true, answered = 0, total = 5, completed = false))
        assertEquals(DailyChallengeHomePresenter.CardState.IN_PROGRESS,
            S.cardState(supported = true, answered = 2, total = 5, completed = false))
        assertEquals(DailyChallengeHomePresenter.CardState.COMPLETED,
            S.cardState(supported = true, answered = 5, total = 5, completed = false))
        assertEquals("explicit completed flag wins",
            DailyChallengeHomePresenter.CardState.COMPLETED,
            S.cardState(supported = true, answered = 5, total = 5, completed = true))
    }

    @Test
    fun progressTextWalksZeroToFive() {
        assertEquals("0/5", S.progressText(0, 5))
        assertEquals("1/5", S.progressText(1, 5))
        assertEquals("3/5", S.progressText(3, 5))
        assertEquals("5/5", S.progressText(5, 5))
    }

    @Test
    fun resumeIndexRestoresToAnsweredCount() {
        assertEquals("fresh restart resumes at 0", 0, S.resumeIndex(0, 5, false))
        assertEquals("mid restart resumes at answered", 3, S.resumeIndex(3, 5, false))
        assertEquals("completed resumes at total (result screen), never a 6th", 5, S.resumeIndex(5, 5, true))
    }

    @Test
    fun resumeIndexNeverExceedsTotal_noSixthQuestion() {
        // even if a stale answered count is passed, the flow can never point past question 5
        assertEquals(5, S.resumeIndex(6, 5, false))
        assertEquals(5, S.resumeIndex(99, 5, true))
        assertTrue(S.resumeIndex(7, 5, false) <= 5)
    }

    @Test
    fun estimatedDurationScalesWithCount() {
        assertEquals("~5 dk", S.estimatedDurationText(5))
        assertEquals("~1 dk", S.estimatedDurationText(1))
    }

    @Test
    fun countdownFormatsRemainingTime() {
        val now = 1_000_000_000L
        assertEquals("", S.countdownText(now - 1, now)) // already unlocked
        assertEquals("", S.countdownText(now, now))
        assertEquals("2s 30dk", S.countdownText(now + (2 * 60 + 30) * 60_000L, now))
        assertEquals("45dk", S.countdownText(now + 45 * 60_000L, now))
        assertEquals("1dk", S.countdownText(now + 20_000L, now)) // <1min rounds up to 1
    }
}
