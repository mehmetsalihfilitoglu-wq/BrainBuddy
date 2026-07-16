package com.mioacademy.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests: entitlement widens review depth only, never the new-question count. */
class DailyChallengeEntitlementPolicyTest {

    private val E = DailyChallengeEntitlementPolicy

    @Test
    fun premiumUnlocksUnlimitedReviewFreeDoesNot() {
        assertTrue(E.reviewIsUnlimited(isPremium = true))
        assertFalse(E.reviewIsUnlimited(isPremium = false))
        assertEquals(ReviewScheduler.FREE_REVIEW_DAILY_CAP, E.freeReviewCap())
    }

    @Test
    fun newQuestionLimitIsIdenticalForFreeAndPremium() {
        assertEquals(DailyChallengeBlueprint.CHALLENGE_SIZE, E.newQuestionLimit(isPremium = false))
        assertEquals(DailyChallengeBlueprint.CHALLENGE_SIZE, E.newQuestionLimit(isPremium = true))
        assertEquals("premium must NOT change the new-question limit",
            E.newQuestionLimit(false), E.newQuestionLimit(true))
        assertEquals(5, E.newQuestionLimit(true))
    }
}
