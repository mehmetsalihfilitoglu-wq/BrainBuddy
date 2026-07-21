package com.edumio.app.core

import android.content.Context
import com.edumio.app.release.ReleaseProfile

/**
 * Single seam for "may this user use a paid-tier feature?".
 *
 * v1.0 ships to Google Play entirely FREE: [ReleaseProfile.premiumEnabled] is false, no product can be
 * purchased, and [com.edumio.app.quiz.PremiumPaywallSheet] self-dismisses. Any screen that still asked
 * [PremiumStore] directly therefore rendered a locked state with a CTA that did nothing — broken
 * functionality on core learning flows. Routing every user-visible gate through here makes the whole app
 * unlocked while the Premium UI is off, without deleting the billing/entitlement code that returns when
 * the backend is live.
 */
object FeatureAccess {

    /** True when the user may use every learning feature (always true while the Premium UI is off). */
    fun hasFullAccess(context: Context): Boolean =
        !ReleaseProfile.premiumEnabled || PremiumStore(context).isPremium()

    /** True when a paid-tier upsell/CTA may be shown at all. */
    fun mayShowPremiumUi(): Boolean = ReleaseProfile.premiumEnabled
}
