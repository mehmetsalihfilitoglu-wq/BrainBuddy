package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.coach.WiseCoachGreeting
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.KillSwitchPrefs
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.league.LeagueStore
import com.brainbuddy.app.quiz.BossTestActivity
import com.brainbuddy.app.quiz.BossTestStore
import androidx.activity.OnBackPressedCallback
import com.brainbuddy.app.security.PinManager
import com.brainbuddy.app.ui.BlockedAppsActivity
import com.brainbuddy.app.ui.ParentActivity
import com.brainbuddy.app.ui.PinLockActivity
import com.brainbuddy.app.ui.SettingsActivity
import com.brainbuddy.app.ui.TimeLimitsActivity
import android.widget.ProgressBar
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
        val weeklyReward = com.brainbuddy.app.core.WeeklyRewardStore(this)

        val wiseCoach = WiseCoachGreeting(this, gam, analytics, leagueStore)
        findViewById<android.widget.TextView>(R.id.greeting).text = wiseCoach.getGreeting()

        // Reward contract notification
        val contractStore = com.brainbuddy.app.reward.RewardContractStore(this)
        val pending = contractStore.getPendingReached()
        if (pending.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Ödül!")
                .setMessage(pending.joinToString("\n") { "${it.targetXP} XP: ${it.description}" })
                .setPositiveButton("Tamam", null)
                .show()
        }

        // Header badges - streak flame + count + freeze tokens
        val freezeTxt = if (gam.freezeTokens() > 0) " (${gam.freezeTokens()} 🧊)" else ""
        findViewById<android.widget.TextView>(R.id.streakBadge).text = "🔥 ${gam.streakDays()} gün seri$freezeTxt"
        findViewById<android.widget.TextView>(R.id.pointsBadge).text = "⭐ ${gam.xp()} XP"
        findViewById<android.widget.TextView>(R.id.levelBadge).text = "Seviye ${gam.level()}"

        // Daily goal: 1 quiz per day
        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        // Simple: if we have sessions today, goal met. Use analytics.
        val sessionsToday = analytics.getSessions().count { s ->
            TimeUnit.MILLISECONDS.toDays(s.tsMs) == today
        }
        val progress = findViewById<ProgressBar>(R.id.dailyProgress)
        progress.max = 1
        progress.progress = if (sessionsToday >= 1) 1 else 0

        // CTA clicks - Student can only access Quiz, Stats, Parent Area (PIN required)
        val juniorPrefs = com.brainbuddy.app.junior.JuniorPrefs(this)
        val cardJunior = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardJunior)
        if (juniorPrefs.isJuniorEnabled()) {
            cardJunior.visibility = android.view.View.VISIBLE
            cardJunior.setOnClickListener {
                startActivity(Intent(this, com.brainbuddy.app.junior.JuniorHubActivity::class.java))
            }
        } else {
            cardJunior.visibility = android.view.View.GONE
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardTest).setOnClickListener {
            val bossStore = BossTestStore(this)
            val lvl = gam.level()
            val bossLevel = (lvl / 10) * 10
            if (bossLevel > 0 && lvl > bossLevel && !bossStore.isBossPassed(bossLevel)) {
                startActivity(Intent(this, BossTestActivity::class.java).putExtra(BossTestActivity.EXTRA_BOSS_LEVEL, bossLevel))
            } else {
                startActivity(Intent(this, com.brainbuddy.app.quiz.QuizActivity::class.java))
            }
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardCoach).setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.coach.CoachScreen::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardLeague).setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.social.LeagueScreen::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardClassroom).setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.classroom.ClassroomActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardAvatarShop).setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.avatar.AvatarShopScreen::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        val cardWeeklyChest = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWeeklyChest)
        cardWeeklyChest?.setOnClickListener {
            val tokens = weeklyReward.claimWeeklyChest()
            if (tokens > 0) {
                android.widget.Toast.makeText(this, "+$tokens donma jetonu!", android.widget.Toast.LENGTH_SHORT).show()
                val freezeTxt = if (gam.freezeTokens() > 0) " (${gam.freezeTokens()} 🧊)" else ""
                findViewById<android.widget.TextView>(R.id.streakBadge).text = "🔥 ${gam.streakDays()} gün seri$freezeTxt"
            } else if (weeklyReward.canClaimWeeklyChest()) {
                android.widget.Toast.makeText(this, "Daha fazla XP kazanın (Silver: 80, Gold: 150)", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardVeli).setOnClickListener {
            val pinManager = PinManager(this)
            val intent = Intent(this, PinLockActivity::class.java).apply {
                putExtra(PinLockActivity.EXTRA_TARGET, "ParentActivity")
                putExtra(PinLockActivity.EXTRA_MODE, if (pinManager.isPinSet()) "verify" else "set")
            }
            startActivity(intent)
        }
        val cardSettings = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSettings)
        val cardBlockedApps = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBlockedApps)
        val cardTimeLimits = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardTimeLimits)
        if (AppModeManager.isParentMode()) {
            cardSettings.visibility = android.view.View.VISIBLE
            cardBlockedApps.visibility = android.view.View.VISIBLE
            cardTimeLimits.visibility = android.view.View.VISIBLE
            cardSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
            cardBlockedApps.setOnClickListener { startActivity(Intent(this, BlockedAppsActivity::class.java)) }
            cardTimeLimits.setOnClickListener { startActivity(Intent(this, TimeLimitsActivity::class.java)) }
        } else {
            cardSettings.visibility = android.view.View.GONE
            cardBlockedApps.visibility = android.view.View.GONE
            cardTimeLimits.visibility = android.view.View.GONE
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
        // Refresh badges when returning
        val gam = GamificationStore(this)
        val freezeTxtResume = if (gam.freezeTokens() > 0) " (${gam.freezeTokens()} 🧊)" else ""
        findViewById<android.widget.TextView>(R.id.streakBadge).text = "🔥 ${gam.streakDays()} gün seri$freezeTxtResume"
        findViewById<android.widget.TextView>(R.id.pointsBadge).text = "⭐ ${gam.xp()} XP"
        findViewById<android.widget.TextView>(R.id.levelBadge).text = "Seviye ${gam.level()}"
        // Refresh parent-only cards visibility (session may have expired)
        val cardSettings = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSettings)
        val cardBlockedApps = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardBlockedApps)
        val cardTimeLimits = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardTimeLimits)
        val visible = AppModeManager.isParentMode()
        cardSettings.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        cardBlockedApps.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        cardTimeLimits.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
        val cardJuniorResume = findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardJunior)
        if (com.brainbuddy.app.junior.JuniorPrefs(this).isJuniorEnabled()) {
            cardJuniorResume.visibility = android.view.View.VISIBLE
            cardJuniorResume.setOnClickListener { startActivity(Intent(this, com.brainbuddy.app.junior.JuniorHubActivity::class.java)) }
        } else {
            cardJuniorResume.visibility = android.view.View.GONE
        }
        if (visible) {
            cardSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
            cardBlockedApps.setOnClickListener { startActivity(Intent(this, BlockedAppsActivity::class.java)) }
            cardTimeLimits.setOnClickListener { startActivity(Intent(this, TimeLimitsActivity::class.java)) }
        }
        val killBanner = findViewById<android.widget.TextView>(R.id.tvKillSwitchBanner)
        killBanner?.visibility = if (KillSwitchPrefs(this).isKillSwitchActive()) android.view.View.VISIBLE else android.view.View.GONE

        val wiseCoach = WiseCoachGreeting(this, GamificationStore(this), AnalyticsStore(this), LeagueStore(this))
        findViewById<android.widget.TextView>(R.id.greeting)?.text = wiseCoach.getGreeting()
    }
}
