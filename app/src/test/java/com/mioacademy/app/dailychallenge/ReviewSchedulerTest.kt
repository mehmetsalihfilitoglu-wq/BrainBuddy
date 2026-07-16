package com.mioacademy.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the spaced-repetition review state machine + Premium gating (no Android/DB deps). */
class ReviewSchedulerTest {

    private val day = ReviewScheduler.DAY_MS

    @Test
    fun fiveConsecutiveCorrectReviewsReachMastered() {
        var state = QuestionLearnState.INCORRECT_ONCE
        var streak = 0
        var now = 1_000_000L
        var last: ReviewScheduler.Outcome? = null
        repeat(ReviewScheduler.MASTERY_STREAK) {
            val o = ReviewScheduler.onReview(state, streak, isCorrect = true, nowMs = now)
            state = o.state; streak = o.consecutiveCorrect; last = o
            now += 40L * day // simulate reviewing when due
        }
        assertEquals(QuestionLearnState.MASTERED, state)
        assertEquals(ReviewScheduler.MASTERY_STREAK, last!!.consecutiveCorrect)
        assertTrue("mastered timestamp set", last!!.masteredAtMs > 0)
    }

    @Test
    fun intervalsGrowWithEachCorrectReview() {
        val now = 5_000_000L
        val i1 = ReviewScheduler.onReview(QuestionLearnState.INCORRECT_ONCE, 0, true, now)
        val i2 = ReviewScheduler.onReview(QuestionLearnState.NEEDS_REVISION, 1, true, now)
        val i3 = ReviewScheduler.onReview(QuestionLearnState.NEEDS_REVISION, 2, true, now)
        assertEquals(now + 1 * day, i1.nextReviewAtMs) // 1 day
        assertEquals(now + 3 * day, i2.nextReviewAtMs) // 3 days
        assertEquals(now + 7 * day, i3.nextReviewAtMs) // 7 days
        assertTrue(i1.nextReviewAtMs < i2.nextReviewAtMs && i2.nextReviewAtMs < i3.nextReviewAtMs)
    }

    @Test
    fun wrongReviewResetsStreakAndRequeuesTomorrow() {
        val now = 9_000_000L
        val o = ReviewScheduler.onReview(QuestionLearnState.NEEDS_REVISION, 3, isCorrect = false, nowMs = now)
        assertEquals(QuestionLearnState.INCORRECT_MULTIPLE, o.state)
        assertEquals(0, o.consecutiveCorrect)
        assertEquals(now + 1 * day, o.nextReviewAtMs)
    }

    @Test
    fun incorrectAndForgottenAreAlwaysDueButNeedsRevisionRespectsSchedule() {
        val now = 100L * day
        assertTrue(ReviewScheduler.isDue(QuestionLearnState.INCORRECT_ONCE, Long.MAX_VALUE, now))
        assertTrue(ReviewScheduler.isDue(QuestionLearnState.INCORRECT_MULTIPLE, Long.MAX_VALUE, now))
        assertTrue(ReviewScheduler.isDue(QuestionLearnState.FORGOTTEN, Long.MAX_VALUE, now))
        assertFalse("future revision not yet due", ReviewScheduler.isDue(QuestionLearnState.NEEDS_REVISION, now + day, now))
        assertTrue("past revision is due", ReviewScheduler.isDue(QuestionLearnState.NEEDS_REVISION, now - day, now))
        // never-review-eligible states are never due
        assertFalse(ReviewScheduler.isDue(QuestionLearnState.MASTERED, 0, now))
        assertFalse(ReviewScheduler.isDue(QuestionLearnState.CORRECT, 0, now))
        assertFalse(ReviewScheduler.isDue(QuestionLearnState.SEEN_ONCE, 0, now))
    }

    @Test
    fun overdueRevisionDecaysToForgotten() {
        val now = 100L * day
        val notOverdue = ReviewScheduler.decayIfOverdue(QuestionLearnState.NEEDS_REVISION, now - 5 * day, now)
        assertEquals(QuestionLearnState.NEEDS_REVISION, notOverdue)
        val overdue = ReviewScheduler.decayIfOverdue(QuestionLearnState.NEEDS_REVISION, now - 40 * day, now)
        assertEquals(QuestionLearnState.FORGOTTEN, overdue)
        // other states never decay
        assertEquals(QuestionLearnState.INCORRECT_ONCE,
            ReviewScheduler.decayIfOverdue(QuestionLearnState.INCORRECT_ONCE, 0, now))
    }

    @Test
    fun premiumUnlocksUnlimitedReviewFreeIsCapped() {
        val items = (1..50).toList()
        val free = ReviewScheduler.applyPremiumCap(items, isPremium = false)
        val premium = ReviewScheduler.applyPremiumCap(items, isPremium = true)
        assertEquals("free capped", ReviewScheduler.FREE_REVIEW_DAILY_CAP, free.size)
        assertEquals("premium unlimited", 50, premium.size)
    }

    @Test
    fun premiumNeverAddsNewQuestions_reviewOnlyResurfacesSeen() {
        // Review operates only on already-seen review states; NEVER_SEEN/SEEN_ONCE are never queue-eligible,
        // so no premium path can turn review into extra NEW questions.
        for (s in QuestionLearnState.values()) {
            val q = ReviewScheduler.queueOf(s)
            if (s == QuestionLearnState.NEVER_SEEN || s == QuestionLearnState.SEEN_ONCE ||
                s == QuestionLearnState.CORRECT || s == QuestionLearnState.MASTERED
            ) {
                assertEquals("$s must not be review-eligible", null, q)
            } else {
                assertTrue("$s must map to a review queue", q != null)
            }
        }
    }
}
