package com.edumio.app.dailychallenge

import android.content.Context
import com.edumio.app.billing.EntitlementProvider

/**
 * Resolves Premium status for the Daily Challenge from the app's entitlement seam
 * ([EntitlementProvider]) — the same source the paywall and the rest of the app trust, so a future
 * Play/Server-backed repository plugs in with no change here.
 *
 * Used ONLY to widen review depth. It is never consulted when generating the daily challenge, so it
 * cannot affect the 5-new-per-day count. Fails safe to non-premium if entitlement state is
 * unavailable — the user still gets the full Free experience, just the capped review.
 */
object DailyChallengeEntitlement {

    fun isPremiumForReview(context: Context): Boolean {
        // v1.0 ships to Google Play entirely FREE: no product is purchasable and the paywall
        // self-dismisses, so gating review/solutions on entitlement would leave every user staring at a
        // locked explanation whose only CTA does nothing. While the Premium UI is off, review depth is
        // open to everyone. This CANNOT affect the 5-new-questions-per-day invariant:
        // [DailyChallengeEntitlementPolicy.newQuestionLimit] ignores the premium flag by design.
        if (!com.edumio.app.release.ReleaseProfile.premiumEnabled) return true
        return try {
            EntitlementProvider.repository(context).current().isPremium
        } catch (_: Throwable) {
            false // fail safe: unknown entitlement → treat as Free
        }
    }

    /**
     * Premium gate for full solution content (reader screen, result-screen solution actions, hub).
     * Same seam and the same fail-closed rule: unknown/failed entitlement behaves as Free.
     */
    fun isPremiumForSolutions(context: Context): Boolean = isPremiumForReview(context)
}
