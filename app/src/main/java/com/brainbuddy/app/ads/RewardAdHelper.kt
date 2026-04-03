package com.brainbuddy.app.ads

import android.app.Activity
import android.util.Log
import com.brainbuddy.app.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

class RewardAdHelper(private val activity: Activity) {

    companion object {
        private const val TAG = "RewardAdHelper"
        private val AD_UNIT_ID: String get() = BuildConfig.ADMOB_REWARDED_ID
    }

    private var rewardedAd: RewardedAd? = null

    fun loadAd(onLoaded: () -> Unit = {}, onFailed: () -> Unit = {}) {
        try {
            Log.d(TAG, "loadAd: requesting ad")
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(activity, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    Log.d(TAG, "loadAd: ad loaded successfully")
                    try { onLoaded() } catch (e: Exception) { Log.e(TAG, "onLoaded callback error", e) }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    Log.w(TAG, "loadAd: failed code=${error.code} domain=${error.domain} msg=${error.message}")
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
            Log.w(TAG, "showAd: called but ad not ready")
            try { onFailed() } catch (e: Exception) { Log.e(TAG, "onFailed callback error", e) }
            return
        }
        try {
            Log.d(TAG, "showAd: displaying ad")
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "showAd: ad dismissed")
                    rewardedAd = null
                    try { onDismissed() } catch (e: Exception) { Log.e(TAG, "onDismissed callback error", e) }
                }
                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    rewardedAd = null
                    Log.w(TAG, "showAd: failed to display - ${error.message}")
                    try { onFailed() } catch (e: Exception) { Log.e(TAG, "onFailed callback error", e) }
                }
            }
            ad.show(activity) {
                Log.d(TAG, "showAd: reward earned")
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
