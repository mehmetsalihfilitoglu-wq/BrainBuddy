package com.mioacademy.app.ads

import android.app.Activity

/**
 * Ad-free unlock shim.
 *
 * Mioitalia has no advertisements. This retains the previous call surface so the
 * review/report screens compile unchanged, but there is no ad: a free unlock is
 * granted instantly (still bounded by each screen's own daily quota; Premium is
 * unlimited). No Google Mobile Ads SDK, no network, no "watch an ad".
 */
object RewardedAdManager {

    /** Nothing to load without ads. */
    val lastLoadError: String? = null

    /** Always "ready" — there is nothing to preload. */
    fun isLoaded(): Boolean = true

    /** No-op: there is no ad to preload. */
    fun preload(activity: Activity) { /* ad-free */ }

    /** Grants the unlock immediately (the caller's quota still applies). */
    fun show(activity: Activity, onReward: () -> Unit, onFail: (String) -> Unit) {
        onReward()
    }
}
