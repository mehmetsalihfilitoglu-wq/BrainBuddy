package com.brainbuddy.app.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardAdHelper(private val activity: Activity) {

    companion object {
        private const val TAG = "RewardAdHelper"
        // Test rewarded ad unit - replace with production ID for release
        private const val AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
    }

    private var rewardedAd: RewardedAd? = null

    fun loadAd(onLoaded: () -> Unit = {}, onFailed: () -> Unit = {}) {
        try {
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(activity, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    try { onLoaded() } catch (e: Exception) { Log.e(TAG, "onLoaded callback error", e) }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                    try { onFailed() } catch (e: Exception) { Log.e(TAG, "onFailed callback error", e) }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "loadAd error", e)
            onFailed()
        }
    }

    fun showAd(onRewarded: () -> Unit, onDismissed: () -> Unit = {}, onFailed: () -> Unit = {}) {
        val ad = rewardedAd
        if (ad == null) {
            try { onFailed() } catch (e: Exception) { Log.e(TAG, "onFailed callback error", e) }
            return
        }
        try {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    rewardedAd = null
                    try { onDismissed() } catch (e: Exception) { Log.e(TAG, "onDismissed callback error", e) }
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    Log.w(TAG, "Ad failed to show: ${error.message}")
                    try { onFailed() } catch (e: Exception) { Log.e(TAG, "onFailed callback error", e) }
                }
            }
            ad.show(activity) {
                try { onRewarded() } catch (e: Exception) { Log.e(TAG, "onRewarded callback error", e) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "showAd error", e)
            rewardedAd = null
            onFailed()
        }
    }

    fun isLoaded(): Boolean = rewardedAd != null
}
