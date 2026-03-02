package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.coach.WiseCoachGreeting
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.KillSwitchPrefs
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.league.LeagueStore
import com.brainbuddy.app.ui.GrowthHubActivity
import com.brainbuddy.app.ui.StudentProfileActivity
import com.brainbuddy.app.ui.StudyHubActivity
import com.brainbuddy.app.security.PinManager
import com.brainbuddy.app.ui.PinLockActivity
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    private var contentSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.getBooleanExtra("open_gate", false)) {
            val blockedPkg = intent.getStringExtra(com.brainbuddy.app.gate.GateActivity.EXTRA_BLOCKED_PACKAGE) ?: ""
            startActivity(Intent(this, com.brainbuddy.app.gate.GateActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(com.brainbuddy.app.gate.GateActivity.EXTRA_BLOCKED_PACKAGE, blockedPkg)
            })
            finish()
            return
        }
        val protectionPrefs = ProtectionPrefs(this)
        if (protectionPrefs.userLocked() || protectionPrefs.isPermissionLocked()) {
            startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_home)
        contentSet = true

        val killSwitchBanner = findViewById<android.widget.TextView>(R.id.tvKillSwitchBanner)
        killSwitchBanner?.visibility = if (KillSwitchPrefs(this).isKillSwitchActive()) android.view.View.VISIBLE else android.view.View.GONE

        var lastBackPressMs = 0L
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = System.currentTimeMillis()
                if (now - lastBackPressMs < 3000) {
                    finishAffinity()
                } else {
                    lastBackPressMs = now
                    android.widget.Toast.makeText(this@HomeActivity, getString(R.string.back_exit_hint), android.widget.Toast.LENGTH_SHORT).show()
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
                .setTitle("Ödül!")
                .setMessage(pending.joinToString("\n") { "${it.targetXP} XP: ${it.description}" })
                .setPositiveButton("Tamam", null)
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

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardCalis).setOnClickListener {
            startActivity(Intent(this, StudyHubActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardGelisim).setOnClickListener {
            startActivity(Intent(this, GrowthHubActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardProfil).setOnClickListener {
            startActivity(Intent(this, StudentProfileActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardVeli).setOnClickListener {
            val pinManager = PinManager(this)
            startActivity(Intent(this, PinLockActivity::class.java).apply {
                putExtra(PinLockActivity.EXTRA_TARGET, "ParentActivity")
                putExtra(PinLockActivity.EXTRA_MODE, if (pinManager.isPinSet()) "verify" else "set")
            })
        }
    }

    override fun onResume() {
        super.onResume()
        val protectionPrefs = ProtectionPrefs(this)
        if (!contentSet || protectionPrefs.userLocked() || protectionPrefs.isPermissionLocked()) {
            if (protectionPrefs.userLocked() || protectionPrefs.isPermissionLocked()) {
                startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
            return
        }
        val gam = GamificationStore(this)
        val freezeTxtResume = if (gam.freezeTokens() > 0) " (${gam.freezeTokens()} 🧊)" else ""
        findViewById<android.widget.TextView>(R.id.streakBadge).text = "🔥 ${gam.streakDays()} gün seri$freezeTxtResume"
        findViewById<android.widget.TextView>(R.id.pointsBadge).text = "⭐ ${gam.xp()} XP"
        findViewById<android.widget.TextView>(R.id.levelBadge).text = "Seviye ${gam.level()}"

        val killBanner = findViewById<android.widget.TextView>(R.id.tvKillSwitchBanner)
        killBanner?.visibility = if (KillSwitchPrefs(this).isKillSwitchActive()) android.view.View.VISIBLE else android.view.View.GONE

        val wiseCoach = WiseCoachGreeting(this, GamificationStore(this), AnalyticsStore(this), LeagueStore(this))
        findViewById<android.widget.TextView>(R.id.greeting)?.text = wiseCoach.getGreeting()
    }
}
