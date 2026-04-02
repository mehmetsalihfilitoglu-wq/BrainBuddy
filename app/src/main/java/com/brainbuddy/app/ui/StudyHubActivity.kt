package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.LockScreenActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.quiz.BossTestActivity
import com.brainbuddy.app.quiz.BossTestStore
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.quiz.WrongPoolLauncher
import com.brainbuddy.app.quiz.WrongQuestionPoolStore

class StudyHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_study_hub)

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }

        val gam = GamificationStore(this)
        val bossStore = BossTestStore(this)

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardTest).setOnClickListener {
            val lvl = gam.level()
            val bossLevel = (lvl / 10) * 10
            if (bossLevel > 0 && lvl > bossLevel && !bossStore.isBossPassed(bossLevel)) {
                startActivity(Intent(this, BossTestActivity::class.java)
                    .putExtra(BossTestActivity.EXTRA_BOSS_LEVEL, bossLevel))
            } else {
                startActivity(Intent(this, QuizActivity::class.java))
            }
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWrongPool).setOnClickListener {
            WrongPoolLauncher.launch(this)
        }
    }

    override fun onResume() {
        super.onResume()
        updateWrongPoolCardState()
    }

    private fun updateWrongPoolCardState() {
        val card = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWrongPool)
        val subtitle = findViewById<android.widget.TextView>(R.id.tvWrongPoolSubtitle)
        val hasItems = WrongQuestionPoolStore(this).isNotEmpty()

        card.isEnabled = hasItems
        card.alpha = if (hasItems) 1f else 0.45f
        subtitle.text = if (hasItems) {
            getString(R.string.wrong_pool_home_sub)
        } else {
            getString(R.string.wrong_pool_card_empty_sub)
        }
    }
}
