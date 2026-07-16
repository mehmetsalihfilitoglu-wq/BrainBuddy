package com.mioacademy.app.dailychallenge

import android.content.Context
import com.mioacademy.app.billing.EntitlementProvider

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

    fun isPremiumForReview(context: Context): Boolean = try {
        EntitlementProvider.repository(context).current().isPremium
    } catch (_: Throwable) {
        false // fail safe: unknown entitlement → treat as Free
    }
}
