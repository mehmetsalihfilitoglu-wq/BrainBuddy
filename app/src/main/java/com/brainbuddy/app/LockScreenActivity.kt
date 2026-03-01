package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityLockScreenBinding

/**
 * Shown when user failed a test (wrongCount > 3).
 * Blocks main app until user passes a retry of the same quiz.
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var b: ActivityLockScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.tvFailedTitle.text = "Test Başarısız ❌"
        b.tvFailedMessage.text = "3'ten fazla yanlış cevap verdin. Testi geçene kadar uygulama kilitli."

        val protectionPrefs = ProtectionPrefs(this)
        val wrongIds = protectionPrefs.lastFailedWrongIds()
        val quizId = protectionPrefs.lastFailedQuizId()
        val questionIds = protectionPrefs.lastFailedQuestionIds()

        b.btnReviewWrong.setOnClickListener {
            if (wrongIds.isNotEmpty()) {
                val sessionJson = protectionPrefs.lastFailedSessionJson()
                startActivity(Intent(this, com.brainbuddy.app.quiz.WrongAnswerReviewActivity::class.java).apply {
                    putStringArrayListExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_WRONG_IDS, ArrayList(wrongIds))
                    if (sessionJson.isNotEmpty()) putExtra(com.brainbuddy.app.quiz.WrongAnswerReviewActivity.EXTRA_SESSION_JSON, sessionJson)
                })
            }
        }

        b.btnRetryTest.setOnClickListener {
            val retryIds = if (questionIds.isNotEmpty()) questionIds else wrongIds
            startActivity(Intent(this, com.brainbuddy.app.quiz.QuizActivity::class.java).apply {
                putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_IS_RETRY, true)
                putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_QUIZ_ID, quizId)
                putStringArrayListExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_WRONG_IDS, ArrayList(retryIds))
            })
            finish()
        }

        b.btnPractice.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.quiz.QuizActivity::class.java).apply {
                putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_RETRY_WRONG, true)
            })
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        })
    }
}
