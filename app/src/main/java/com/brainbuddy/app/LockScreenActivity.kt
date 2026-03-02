package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizRetryPolicy
import com.brainbuddy.app.databinding.ActivityLockScreenBinding
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.quiz.QuizCooldownActivity
import com.brainbuddy.app.quiz.QuizRetryAdActivity
import com.brainbuddy.app.security.EmergencyCodeManager
import com.brainbuddy.app.security.PinManager

/**
 * Shown when user failed a test (wrongCount >= 4).
 * Blocks main app until user passes. Retry uses QuizRetryPolicy (ad tickets or cooldown).
 */
class LockScreenActivity : AppCompatActivity() {

    private lateinit var b: ActivityLockScreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLockScreenBinding.inflate(layoutInflater)
        setContentView(b.root)

        val protectionPrefs = ProtectionPrefs(this)
        val permLocked = protectionPrefs.isPermissionLocked()
        if (permLocked) {
            b.tvFailedTitle.text = getString(R.string.lock_permission_title)
            b.tvFailedMessage.text = getString(R.string.lock_permission_message)
            b.btnParentPin.visibility = View.VISIBLE
        } else {
            b.tvFailedTitle.text = getString(R.string.lock_failed_title)
            b.tvFailedMessage.text = getString(R.string.lock_failed_message)
            b.btnParentPin.visibility = View.GONE
        }

        val quizId = protectionPrefs.lastFailedQuizId()
        val questionIds = protectionPrefs.lastFailedQuestionIds()
        val policy = QuizRetryPolicy(this)

        b.btnRetryTest.visibility = if (permLocked) View.GONE else View.VISIBLE
        b.btnPractice.visibility = if (permLocked) View.GONE else View.VISIBLE

        b.adRetrySection.visibility = View.GONE
        b.btnPremiumRetry.visibility = View.GONE

        b.btnRetryTest.setOnClickListener {
            val token = policy.getSameTestToken() ?: run {
                if (quizId.isNotBlank() && questionIds.size >= com.brainbuddy.app.quiz.QuestionRepository.MIN_QUESTIONS_PER_TEST) {
                    policy.onFail(this, QuizRetryPolicy.SameTestToken(quizId, questionIds))
                }
                policy.getSameTestToken()
            }
            val ids = token?.questionIds ?: questionIds
            if (ids.size < com.brainbuddy.app.quiz.QuestionRepository.MIN_QUESTIONS_PER_TEST) {
                startActivity(Intent(this, QuizActivity::class.java).apply {
                    putExtra(QuizActivity.EXTRA_GATE_MODE, true)
                    putExtra(QuizActivity.EXTRA_IS_RETRY, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                })
                finish()
                return@setOnClickListener
            }
            when (policy.getStartMode()) {
                QuizRetryPolicy.StartMode.ALLOW_FREE -> {
                    startActivity(Intent(this, QuizActivity::class.java).apply {
                        putExtra(QuizActivity.EXTRA_GATE_MODE, true)
                        putExtra(QuizActivity.EXTRA_IS_RETRY, true)
                        putExtra(QuizActivity.EXTRA_QUIZ_ID, quizId)
                        putStringArrayListExtra(QuizActivity.EXTRA_QUESTION_IDS_FOR_REPLAY, ArrayList(ids))
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    })
                    finish()
                }
                QuizRetryPolicy.StartMode.REQUIRE_AD -> {
                    startActivity(Intent(this, QuizRetryAdActivity::class.java).apply {
                        putExtra(QuizRetryAdActivity.EXTRA_QUIZ_ID, quizId)
                        putStringArrayListExtra(QuizRetryAdActivity.EXTRA_QUESTION_IDS, ArrayList(ids))
                    })
                    finish()
                }
                QuizRetryPolicy.StartMode.WAIT_COOLDOWN -> {
                    startActivity(Intent(this, QuizCooldownActivity::class.java).apply {
                        putExtra(QuizCooldownActivity.EXTRA_QUIZ_ID, quizId)
                        putStringArrayListExtra(QuizCooldownActivity.EXTRA_QUESTION_IDS, ArrayList(ids))
                    })
                    finish()
                }
            }
        }

        b.btnPractice.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.quiz.QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_REMEDIAL, true)
            })
            finish()
        }

        b.btnParentPin.setOnClickListener {
            val pinManager = PinManager(this)
            startActivity(Intent(this, com.brainbuddy.app.ui.PinLockActivity::class.java).apply {
                putExtra(com.brainbuddy.app.ui.PinLockActivity.EXTRA_TARGET, "PermissionsChecklistActivity")
                putExtra(com.brainbuddy.app.ui.PinLockActivity.EXTRA_MODE, if (pinManager.isPinSet()) "verify" else "set")
            })
        }

        val emergencyManager = EmergencyCodeManager(this)
        b.btnEmergencyUnlock.visibility = if (permLocked && emergencyManager.isEmergencyCodeSet()) View.VISIBLE else View.GONE
        b.btnEmergencyUnlock.setText(R.string.btn_emergency_unlock)
        b.btnEmergencyUnlock.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.ui.EmergencyUnlockActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        })
    }
}
