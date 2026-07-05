package com.mioacademy.app.core

import android.util.Log

/**
 * Optional analytics for Wrong Answer Review feature.
 * Events: wrong_review_open, wrong_item_reveal, wrong_review_ad_shown,
 * wrong_review_ad_rewarded, wrong_review_paywall_opened, wrong_review_premium_click.
 */
object WrongReviewAnalytics {
    private const val TAG = "WrongReviewAnalytics"

    fun logOpen() = Log.d(TAG, "wrong_review_open")
    fun logItemReveal() = Log.d(TAG, "wrong_item_reveal")
    fun logLimitHit() = Log.d(TAG, "wrong_review_limit_hit")
    fun logAdShown() = Log.d(TAG, "wrong_review_ad_shown")
    fun logAdRewarded() = Log.d(TAG, "wrong_review_ad_rewarded")
    fun logPaywallOpened() = Log.d(TAG, "wrong_review_paywall_opened")
    fun logPremiumClick() = Log.d(TAG, "wrong_review_premium_click")
}
