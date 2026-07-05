package com.mioacademy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.R
import com.mioacademy.app.ads.RewardAdHelper
import com.mioacademy.app.core.DailyAdQuotaStore
import com.mioacademy.app.core.LastTestUnlockStore
import com.mioacademy.app.quiz.QuizActivity

class WatchAdToUnlockLastTestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_QUESTION_IDS = "question_ids"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_ad_unlock)

        val quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: ""
        val questionIds = intent.getStringArrayListExtra(EXTRA_QUESTION_IDS) ?: arrayListOf()

        if (quizId.isBlank() || questionIds.isEmpty()) {
            finish()
            return
        }

        val quotaStore = DailyAdQuotaStore(this)
        val remaining = quotaStore.getRemainingToday()

        findViewById<android.widget.TextView>(R.id.tvAdInfo).text =
            getString(R.string.ad_unlock_last_test_info, remaining)

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }

        val adHelper = RewardAdHelper(this)
        adHelper.loadAd(onFailed = { findViewById<android.widget.Button>(R.id.btnWatchAd).isEnabled = false })

        findViewById<android.widget.Button>(R.id.btnWatchAd).setOnClickListener {
            if (adHelper.isLoaded()) {
                adHelper.showAd(
                    onRewarded = {
                        if (quotaStore.consumeOne()) {
                            val unlockStore = LastTestUnlockStore(this)
                            val token = unlockStore.createUnlock(quizId, questionIds)
                            startActivity(Intent(this, QuizActivity::class.java).apply {
                                putExtra(QuizActivity.EXTRA_REPLAY_FROM_LAST_TEST, true)
                                putExtra(QuizActivity.EXTRA_QUIZ_ID, quizId)
                                putExtra(QuizActivity.EXTRA_UNLOCK_TOKEN, token)
                                putStringArrayListExtra(QuizActivity.EXTRA_QUESTION_IDS_FOR_REPLAY, ArrayList(questionIds))
                            })
                        }
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
