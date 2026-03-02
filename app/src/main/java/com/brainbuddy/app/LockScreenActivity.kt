package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.ads.RewardAdHelper
import com.brainbuddy.app.core.AdsPrefs
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.RewardedRetryStore
import com.brainbuddy.app.databinding.ActivityLockScreenBinding
import com.brainbuddy.app.quiz.GateRetrySingleActivity
import com.brainbuddy.app.quiz.QuizResultActivity
import com.brainbuddy.app.security.EmergencyCodeManager
import com.brainbuddy.app.security.PinManager

/**
 * Shown when user failed a test (wrongCount >= 4).
 * Blocks main app until user passes. New quiz generated each attempt.
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

        val wrongIds = protectionPrefs.lastFailedWrongIds()
        val quizId = protectionPrefs.lastFailedQuizId()
        val sessionJson = protectionPrefs.lastFailedSessionJson()
        val session = QuizResultActivity.decodeSession(sessionJson)
        val questionsJson = protectionPrefs.lastFailedQuestionsJson()

        b.btnRetryTest.visibility = if (permLocked) View.GONE else View.VISIBLE
        b.btnPractice.visibility = if (permLocked) View.GONE else View.VISIBLE

        b.btnRetryTest.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.quiz.QuizActivity::class.java).apply {
                putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_GATE_MODE, true)
                putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_IS_RETRY, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            })
            finish()
        }

        b.btnPractice.setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.quiz.QuizActivity::class.java).apply {
                putExtra(com.brainbuddy.app.quiz.QuizActivity.EXTRA_REMEDIAL, true)
            })
            finish()
        }

        val adSection = b.adRetrySection
        val btnWatchAd = b.btnWatchAd
        val tvAdRetryInfo = b.tvAdRetryInfo
        val btnPremiumRetry = b.btnPremiumRetry

        if (!permLocked && wrongIds.isNotEmpty() && session != null) {
            val retryStore = RewardedRetryStore(this)
            val premiumStore = PremiumStore(this)
            val adsPrefs = AdsPrefs(this)
            val profileId = ProfileStore(this).getCurrentProfileId()
            val eligibleQuestion = wrongIds.shuffled().firstOrNull { qId ->
                retryStore.canRetryWithAd(profileId, quizId, qId)
            }
            val isPremium = premiumStore.isPremium()
            val canShowAd = !isPremium && adsPrefs.isAdsEnabled() && eligibleQuestion != null
            val canShowPremium = isPremium && eligibleQuestion != null

            if (canShowAd) {
                adSection.visibility = View.VISIBLE
                tvAdRetryInfo.text = "Reklam izleyerek bir yanlış soruyu tekrar cevaplayabilirsin. Bugün kalan: ${retryStore.getRemainingRetriesToday(profileId)}"
                btnWatchAd.text = "Reklam İzle → Tekrar Dene"
                val adHelper = RewardAdHelper(this)
                adHelper.loadAd(onFailed = { btnWatchAd.isEnabled = false })
                btnWatchAd.setOnClickListener {
                    if (adHelper.isLoaded() && eligibleQuestion != null) {
                        adHelper.showAd(
                            onRewarded = {
                                retryStore.recordRetryUsed(profileId, quizId, eligibleQuestion)
                                startActivity(Intent(this, GateRetrySingleActivity::class.java).apply {
                                    putExtra(GateRetrySingleActivity.EXTRA_QUIZ_ID, quizId)
                                    putExtra(GateRetrySingleActivity.EXTRA_QUESTION_ID, eligibleQuestion)
                                    putExtra(GateRetrySingleActivity.EXTRA_SESSION_JSON, sessionJson)
                                    putExtra(GateRetrySingleActivity.EXTRA_QUESTIONS_JSON, questionsJson)
                                })
                                finish()
                            },
                            onFailed = { adHelper.loadAd() }
                        )
                    } else adHelper.loadAd()
                }
            } else {
                adSection.visibility = View.GONE
            }

            if (canShowPremium) {
                btnPremiumRetry.visibility = View.VISIBLE
                btnPremiumRetry.text = "Tekrar Dene (Premium)"
                btnPremiumRetry.setOnClickListener {
                    if (eligibleQuestion != null) {
                        retryStore.recordRetryUsed(profileId, quizId, eligibleQuestion)
                        startActivity(Intent(this, GateRetrySingleActivity::class.java).apply {
                            putExtra(GateRetrySingleActivity.EXTRA_QUIZ_ID, quizId)
                            putExtra(GateRetrySingleActivity.EXTRA_QUESTION_ID, eligibleQuestion)
                            putExtra(GateRetrySingleActivity.EXTRA_SESSION_JSON, sessionJson)
                            putExtra(GateRetrySingleActivity.EXTRA_QUESTIONS_JSON, questionsJson)
                        })
                        finish()
                    }
                }
            } else {
                btnPremiumRetry.visibility = View.GONE
            }
        } else {
            adSection.visibility = View.GONE
            btnPremiumRetry.visibility = View.GONE
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
