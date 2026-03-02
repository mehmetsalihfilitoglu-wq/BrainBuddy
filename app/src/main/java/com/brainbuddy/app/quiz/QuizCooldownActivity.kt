package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizRetryPolicy

class QuizCooldownActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_QUIZ_ID = "quiz_id"
        const val EXTRA_QUESTION_IDS = "question_ids"
    }

    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_quiz_cooldown)

        val quizId = intent.getStringExtra(EXTRA_QUIZ_ID) ?: ""
        val questionIds = intent.getStringArrayListExtra(EXTRA_QUESTION_IDS) ?: arrayListOf()
        val policy = QuizRetryPolicy(this)
        val cooldownEnd = policy.getCooldownEndMs()
        val tvMessage = findViewById<android.widget.TextView>(R.id.tvCooldownMessage)

        fun formatRemaining(ms: Long): String {
            val totalSec = (ms / 1000).toInt()
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%d:%02d".format(min, sec)
        }

        timer = object : CountDownTimer(
            (cooldownEnd - System.currentTimeMillis()).coerceAtLeast(0),
            1000
        ) {
            override fun onTick(millisUntilFinished: Long) {
                tvMessage.text = getString(R.string.retry_cooldown_message, formatRemaining(millisUntilFinished))
            }
            override fun onFinish() {
                tvMessage.text = "Tekrar deneyebilirsin."
                findViewById<android.widget.Button>(R.id.btnRetry).apply {
                    isEnabled = true
                    setOnClickListener {
                        if (questionIds.size >= QuestionRepository.MIN_QUESTIONS_PER_TEST && ProtectionPrefs(this@QuizCooldownActivity).userLocked()) {
                            startActivity(Intent(this@QuizCooldownActivity, QuizActivity::class.java).apply {
                                putExtra(QuizActivity.EXTRA_GATE_MODE, true)
                                putExtra(QuizActivity.EXTRA_IS_RETRY, true)
                                putExtra(QuizActivity.EXTRA_QUIZ_ID, quizId)
                                putStringArrayListExtra(QuizActivity.EXTRA_QUESTION_IDS_FOR_REPLAY, ArrayList(questionIds))
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            })
                        }
                        finish()
                    }
                }
            }
        }.start()

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}
