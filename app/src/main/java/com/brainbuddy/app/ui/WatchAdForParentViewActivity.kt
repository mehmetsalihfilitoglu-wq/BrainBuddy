package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.ads.RewardAdHelper
import com.brainbuddy.app.core.DailyParentViewQuotaStore

/**
 * Reklam izleyerek veli "doğru cevap görme" kotasına +1 ekler.
 */
class WatchAdForParentViewActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_ad_parent_view)

        val quotaStore = DailyParentViewQuotaStore(this)
        val remaining = quotaStore.getRemainingToday()

        findViewById<android.widget.TextView>(R.id.tvAdInfo).text =
            if (remaining == Int.MAX_VALUE) "Premium: Sınırsız"
            else "Doğru cevapları görmek için reklam izle. Bugün kalan hak: $remaining"

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        val adHelper = RewardAdHelper(this)
        adHelper.loadAd(onFailed = { findViewById<android.widget.Button>(R.id.btnWatchAd).isEnabled = false })

        findViewById<android.widget.Button>(R.id.btnWatchAd).setOnClickListener {
            if (adHelper.isLoaded()) {
                adHelper.showAd(
                    onRewarded = {
                        quotaStore.addFromAd()
                        setResult(RESULT_OK)
                        finish()
                    },
                    onFailed = { adHelper.loadAd() }
                )
            } else {
                adHelper.loadAd()
            }
        }
    }
}
