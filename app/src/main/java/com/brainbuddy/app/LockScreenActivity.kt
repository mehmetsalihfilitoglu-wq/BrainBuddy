package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.security.EmergencyCodeManager
import com.brainbuddy.app.security.PinManager
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

        val protectionPrefs = ProtectionPrefs(this)
        val permLocked = protectionPrefs.isPermissionLocked()
        if (permLocked) {
            b.tvFailedTitle.text = getString(R.string.lock_permission_title)
            b.tvFailedMessage.text = getString(R.string.lock_permission_message)
            b.btnParentPin.visibility = android.view.View.VISIBLE
        } else {
            b.tvFailedTitle.text = getString(R.string.lock_failed_title)
            b.tvFailedMessage.text = getString(R.string.lock_failed_message)
            b.btnParentPin.visibility = android.view.View.GONE
        }

        val wrongIds = protectionPrefs.lastFailedWrongIds()
        val quizId = protectionPrefs.lastFailedQuizId()
        val questionIds = protectionPrefs.lastFailedQuestionIds()

        b.btnReviewWrong.visibility = if (permLocked) android.view.View.GONE else android.view.View.VISIBLE
        b.btnRetryTest.visibility = if (permLocked) android.view.View.GONE else android.view.View.VISIBLE
        b.btnPractice.visibility = if (permLocked) android.view.View.GONE else android.view.View.VISIBLE

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
                putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_REMEDIAL, true)
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
        b.btnEmergencyUnlock.visibility = if (permLocked && emergencyManager.isEmergencyCodeSet()) android.view.View.VISIBLE else android.view.View.GONE
        b.btnEmergencyUnlock.setText(R.string.btn_emergency_unlock)
        b.btnEmergencyUnlock.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.ui.EmergencyUnlockActivity::class.java))
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { }
        })
    }
}
