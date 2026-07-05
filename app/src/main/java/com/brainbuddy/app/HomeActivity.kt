package com.brainbuddy.app

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.coach.WiseCoachGreeting
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.league.LeagueStore
import com.brainbuddy.app.quiz.WrongPoolLauncher
import com.brainbuddy.app.quiz.WrongQuestionPoolStore
import com.brainbuddy.app.ui.GrowthHubActivity
import com.brainbuddy.app.ui.StudentProfileActivity
import com.brainbuddy.app.ui.StudyHubActivity
import android.content.Intent
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        var lastBackPressMs = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 3000) {
                    finishAffinity()
                } else {
                    lastBackPressMs = now
                    android.widget.Toast.makeText(
                        this@HomeActivity,
                        getString(R.string.back_exit_hint),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })

        val gam = GamificationStore(this)
        val analytics = AnalyticsStore(this)
        val leagueStore = LeagueStore(this)

        val wiseCoach = WiseCoachGreeting(this, gam, analytics, leagueStore)
        findViewById<android.widget.TextView>(R.id.greeting).text = wiseCoach.getGreeting()

        val contractStore = com.brainbuddy.app.reward.RewardContractStore(this)
        val pending = contractStore.getPendingReached()
        if (pending.isNotEmpty()) {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.home_reward_title))
                .setMessage(pending.joinToString("\n") { "${it.targetXP} XP: ${it.description}" })
                .setPositiveButton(getString(R.string.home_reward_ok), null)
                .show()
        }

        val freezeTxt = if (gam.freezeTokens() > 0) " (${gam.freezeTokens()} 🧊)" else ""
        findViewById<android.widget.TextView>(R.id.streakBadge).text = "🔥 ${gam.streakDays()} gün seri$freezeTxt"
        findViewById<android.widget.TextView>(R.id.pointsBadge).text = "⭐ ${gam.xp()} XP"
        findViewById<android.widget.TextView>(R.id.levelBadge).text = "Seviye ${gam.level()}"

        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val sessionsToday = analytics.getSessions().count { s ->
            TimeUnit.MILLISECONDS.toDays(s.tsMs) == today
        }
        val progress = findViewById<ProgressBar>(R.id.dailyProgress)
        progress.max = 1
        progress.progress = if (sessionsToday >= 1) 1 else 0

        updateWrongPoolCardVisibility()
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWrongPool).setOnClickListener {
            WrongPoolLauncher.launch(this)
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardCalis).setOnClickListener {
            startActivity(Intent(this, StudyHubActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardGelisim).setOnClickListener {
            startActivity(Intent(this, GrowthHubActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardProfil).setOnClickListener {
            startActivity(Intent(this, StudentProfileActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val gam = GamificationStore(this)
        val freezeTxtResume = if (gam.freezeTokens() > 0) " (${gam.freezeTokens()} 🧊)" else ""
        findViewById<android.widget.TextView>(R.id.streakBadge).text = "🔥 ${gam.streakDays()} gün seri$freezeTxtResume"
        findViewById<android.widget.TextView>(R.id.pointsBadge).text = "⭐ ${gam.xp()} XP"
        findViewById<android.widget.TextView>(R.id.levelBadge).text = "Seviye ${gam.level()}"
        val wiseCoach = WiseCoachGreeting(this, GamificationStore(this), AnalyticsStore(this), LeagueStore(this))
        findViewById<android.widget.TextView>(R.id.greeting)?.text = wiseCoach.getGreeting()
        updateWrongPoolCardVisibility()
    }

    private fun updateWrongPoolCardVisibility() {
        val pool = WrongQuestionPoolStore(this)
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWrongPool).visibility =
            if (pool.isNotEmpty()) View.VISIBLE else View.GONE
    }
}
