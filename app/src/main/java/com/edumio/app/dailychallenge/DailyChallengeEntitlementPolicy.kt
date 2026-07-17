package com.edumio.app.dailychallenge

/**
 * Pure entitlement policy for the Daily Challenge (no Android deps → testable).
 *
 * Load-bearing invariant: Premium changes the DEPTH of review only. The number of NEW questions per
 * local day is a hard product constant, identical for Free and Premium — [newQuestionLimit] ignores
 * the premium flag by design, so there is no code path where entitlement can raise it.
 */
object DailyChallengeEntitlementPolicy {

    /** Premium unlocks unlimited review; Free is capped. */
    fun reviewIsUnlimited(isPremium: Boolean): Boolean = isPremium

    /** Free-tier review cap (mirrors [ReviewScheduler]). */
    fun freeReviewCap(): Int = ReviewScheduler.FREE_REVIEW_DAILY_CAP

    /** The daily NEW-question limit — constant for everyone. Premium can NEVER increase it. */
    fun newQuestionLimit(@Suppress("UNUSED_PARAMETER") isPremium: Boolean): Int =
        DailyChallengeBlueprint.CHALLENGE_SIZE
}
