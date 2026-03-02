package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.LockScreenActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.quiz.BossTestActivity
import com.brainbuddy.app.quiz.BossTestStore
import com.brainbuddy.app.quiz.QuizActivity
import com.brainbuddy.app.coach.CoachScreen
import com.brainbuddy.app.classroom.ClassroomActivity
import com.brainbuddy.app.core.GamificationStore

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

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardCoach).setOnClickListener {
            startActivity(Intent(this, CoachScreen::class.java))
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardClassroom).setOnClickListener {
            startActivity(Intent(this, ClassroomActivity::class.java))
        }
    }
}
