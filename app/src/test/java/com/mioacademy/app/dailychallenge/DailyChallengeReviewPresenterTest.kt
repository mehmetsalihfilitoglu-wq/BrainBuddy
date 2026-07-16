package com.mioacademy.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the review presenter + Premium cap semantics. */
class DailyChallengeReviewPresenterTest {

    private val P = DailyChallengeReviewPresenter

    @Test
    fun premiumSeesEverythingFreeIsCapped() {
        // 25 eligible, Free surfaced only the cap (10)
        assertTrue("free with overflow is capped", P.isCapped(totalEligible = 25, shown = 10, isPremium = false))
        assertEquals(15, P.lockedCount(25, 10, false))
        // premium: never capped, nothing locked
        assertFalse("premium never capped", P.isCapped(totalEligible = 25, shown = 25, isPremium = true))
        assertEquals(0, P.lockedCount(25, 25, true))
    }

    @Test
    fun freeUnderCapIsNotCapped() {
        assertFalse(P.isCapped(totalEligible = 4, shown = 4, isPremium = false))
        assertEquals(0, P.lockedCount(4, 4, false))
    }

    @Test
    fun capMatchesReviewSchedulerConstant() {
        // The Free surfaced count can never exceed the scheduler cap.
        val eligible = 100
        val shown = minOf(eligible, ReviewScheduler.FREE_REVIEW_DAILY_CAP)
        assertEquals(ReviewScheduler.FREE_REVIEW_DAILY_CAP, shown)
        assertTrue(P.isCapped(eligible, shown, isPremium = false))
    }

    @Test
    fun stepProgressesCheckThenNextThenFinish() {
        assertEquals(DailyChallengeReviewPresenter.Step.CHECK, P.step(revealed = false, position = 0, total = 3))
        assertEquals(DailyChallengeReviewPresenter.Step.NEXT, P.step(revealed = true, position = 0, total = 3))
        assertEquals(DailyChallengeReviewPresenter.Step.FINISH, P.step(revealed = true, position = 2, total = 3))
    }

    @Test
    fun progressTextIsOneBased() {
        assertEquals("1/3", P.progressText(0, 3))
        assertEquals("3/3", P.progressText(2, 3))
    }
}
