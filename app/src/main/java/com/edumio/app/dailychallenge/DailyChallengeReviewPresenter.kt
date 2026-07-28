package com.edumio.app.dailychallenge

/**
 * Pure presentation logic for the review screens (no Android deps → testable). The heavy lifting
 * (which questions are due, the spaced-repetition transitions, the Free cap) lives in
 * [ReviewEngine]/[ReviewScheduler]; this only formats and decides the upsell.
 */
object DailyChallengeReviewPresenter {

    fun progressText(position: Int, total: Int): String = "${(position + 1).coerceAtMost(total)}/$total"

    /** True when Free users have more eligible review questions than were surfaced (cap hit). */
    fun isCapped(totalEligible: Int, shown: Int, isPremium: Boolean): Boolean =
        !isPremium && totalEligible > shown

    /** How many eligible review questions are locked behind Premium right now. */
    fun lockedCount(totalEligible: Int, shown: Int, isPremium: Boolean): Int =
        if (isPremium) 0 else (totalEligible - shown).coerceAtLeast(0)

    /** Label for the primary button: "check" before revealing, "next"/"finish" after. */
    enum class Step { CHECK, NEXT, FINISH }

    fun step(revealed: Boolean, position: Int, total: Int): Step = when {
        !revealed -> Step.CHECK
        position >= total - 1 -> Step.FINISH
        else -> Step.NEXT
    }
}
