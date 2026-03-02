package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.ads.RewardAdHelper
import com.brainbuddy.app.core.QuizRetryPolicy

class QuizRetryAdActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_QUESTION_IDS = "question_ids"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_retry_ad)

        val quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: ""
        val questionIds = intent.getStringArrayListExtra(EXTRA_QUESTION_IDS) ?: arrayListOf()
        val policy = QuizRetryPolicy(this)

        findViewById<android.widget.TextView>(R.id.tvRetryInfo).text =
            getString(R.string.retry_ad_info, policy.getRemainingTickets())

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }

        val adHelper = RewardAdHelper(this)
        adHelper.loadAd(onFailed = { findViewById<android.widget.Button>(R.id.btnWatchAd).isEnabled = false })

        findViewById<android.widget.Button>(R.id.btnWatchAd).setOnClickListener {
            if (adHelper.isLoaded() && questionIds.size >= QuestionRepository.MIN_QUESTIONS_PER_TEST) {
                adHelper.showAd(
                    onRewarded = {
                        policy.consumeAdTicket()
                        startActivity(Intent(this, QuizActivity::class.java).apply {
                            putExtra(QuizActivity.EXTRA_GATE_MODE, true)
                            putExtra(QuizActivity.EXTRA_IS_RETRY, true)
                            putExtra(QuizActivity.EXTRA_RETRY_AFTER_AD, true)
                            putExtra(QuizActivity.EXTRA_QUIZ_ID, quizId)
                            putStringArrayListExtra(QuizActivity.EXTRA_QUESTION_IDS_FOR_REPLAY, ArrayList(questionIds))
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        })
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
