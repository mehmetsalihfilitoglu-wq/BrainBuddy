package com.mioacademy.app.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mioacademy.app.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Singleton manager for Rewarded ads.
 * Preloads ads in advance and retries on failure.
 */
object RewardedAdManager {

    private const val TAG = "RewardedAdManager"
    private val AD_UNIT_ID: String get() = BuildConfig.ADMOB_REWARDED_ID
    private const val RETRY_DELAY_MS = 7000L

    @Volatile
    private var rewardedAd: RewardedAd? = null

    @Volatile
    private var isLoading: Boolean = false

    @Volatile
    var lastLoadError: String? = null
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isLoaded(): Boolean = rewardedAd != null

    /**
     * Preload a rewarded ad. If already loading, returns immediately.
     * On failure, schedules a retry after RETRY_DELAY_MS.
     */
    fun preload(activity: Activity) {
        if (isLoading) {
            Log.d(TAG, "preload: already loading, skip")
            return
        }
        Log.d(TAG, "preload: started")
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        RewardedAd.load(
            activity,
            AD_UNIT_ID,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                    lastLoadError = null
                    Log.d(TAG, "preload: loaded successfully")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                    lastLoadError = "code=${error.code} domain=${error.domain} msg=${error.message}"
                    Log.w(TAG, "preload: failed - $lastLoadError")
                    mainHandler.postDelayed({
                        Log.d(TAG, "preload: retrying after failure")
                        preload(activity)
                    }, RETRY_DELAY_MS)
                }
            }
        )
    }

    /**
     * Show the rewarded ad. Must be called with an Activity.
     * - If ad is null: calls onFail and triggers preload
     * - On success: calls onReward, clears ad, triggers preload
     * - On show failure: calls onFail, clears ad, triggers preload
     */
    fun show(
        activity: Activity,
        onReward: () -> Unit,
        onFail: (String) -> Unit
    ) {
        val ad = rewardedAd
        if (ad == null) {
            Log.w(TAG, "show: called but ad not ready")
            onFail("Reklam hazır değil")
            preload(activity)
            return
        }
        Log.d(TAG, "show: displaying ad")
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "show: ad dismissed")
                rewardedAd = null
                preload(activity)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "show: failed to display - ${error.message}")
                rewardedAd = null
                onFail("Reklam gösterilemedi: ${error.message}")
                preload(activity)
            }
        }
        ad.show(activity) {
            Log.d(TAG, "show: rewarded earned")
            onReward()
        }
    }
}
