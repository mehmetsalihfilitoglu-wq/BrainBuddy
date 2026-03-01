package com.brainbuddy.app.quiz

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProtectionPrefs

class BossTestActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BOSS_LEVEL = "boss_level"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_boss_test)

        val gamification = GamificationStore(this)
        val bossStore = BossTestStore(this)
        val bossLevel = intent.getIntExtra(EXTRA_BOSS_LEVEL, (gamification.level() / 10) * 10)
            .takeIf { it > 0 } ?: 10

        findViewById<android.widget.TextView>(R.id.tvBossTitle).text = "BOSS TEST Seviye $bossLevel"
        findViewById<android.widget.TextView>(R.id.tvBossDesc).text = "Bu seviyeyi geçmek için zor sorulardan oluşan Boss testini geçmelisiniz."

        findViewById<android.widget.Button>(R.id.btnStartBoss).setOnClickListener {
            startActivity(Intent(this, QuizActivity::class.java).apply {
                putExtra(QuizActivity.EXTRA_QUIZ_ID, "boss_$bossLevel")
                putExtra(EXTRA_BOSS_LEVEL, bossLevel)
            })
            finish()
        }
    }
}
