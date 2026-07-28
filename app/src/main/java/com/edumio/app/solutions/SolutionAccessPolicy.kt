package com.edumio.app.solutions

/**
 * Pure access decisions for Premium solution content (no Android deps → unit-testable).
 *
 * The single rule: solution BODIES render only for a confirmed Premium entitlement. Anything else —
 * Free, unknown, stale, or errored entitlement state — behaves as Free (fail-closed). This policy is
 * enforced at screen entry (Activity.onCreate), not by hiding buttons, so deep links or crafted
 * intents cannot reach solution content either.
 */
object SolutionAccessPolicy {

    enum class Access { FULL_SOLUTION, LOCKED }

    /** [isPremium] must be the freshly-resolved entitlement; null means unknown/failed → LOCKED. */
    fun access(isPremium: Boolean?): Access =
        if (isPremium == true) Access.FULL_SOLUTION else Access.LOCKED

    /** Free users always see whether they were correct and which option is correct — never gated. */
    fun canSeeCorrectness(): Boolean = true

    /** Solution text may be bound into the view tree ONLY under FULL_SOLUTION. */
    fun mayBindSolutionText(access: Access): Boolean = access == Access.FULL_SOLUTION
}
