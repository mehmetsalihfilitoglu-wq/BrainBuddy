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
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(activity, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                rewardedAd = ad
                onLoaded()
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                rewardedAd = null
                Log.w(TAG, "Rewarded ad failed to load: ${error.message}")
                onFailed()
            }
        })
    }

    fun showAd(onRewarded: () -> Unit, onDismissed: () -> Unit = {}, onFailed: () -> Unit = {}) {
        val ad = rewardedAd
        if (ad == null) {
            onFailed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                onFailed()
            }
        }
        ad.show(activity) { rewardItem ->
            onRewarded()
        }
    }

    fun isLoaded(): Boolean = rewardedAd != null
}
