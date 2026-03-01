package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.AppModeManager
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.security.PinManager
import com.brainbuddy.app.ui.BlockedAppsActivity
import com.brainbuddy.app.ui.ParentActivity
import com.brainbuddy.app.ui.PinLockActivity
import com.brainbuddy.app.ui.SettingsActivity
import com.brainbuddy.app.ui.TimeLimitsActivity
import android.widget.ProgressBar
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        val gam = GamificationStore(this)
        val analytics = AnalyticsStore(this)

        // Header badges
        findViewById<android.widget.TextView>(R.id.streakBadge).text = "🔥 ${gam.streakDays()} gün seri"
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
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardTest).setOnClickListener {
            startActivity(Intent(this, com.brainbuddy.app.quiz.QuizActivity::class.java))
        }
        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
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
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        // Refresh badges when returning
        val gam = GamificationStore(this)
        findViewById<android.widget.TextView>(R.id.streakBadge).text = "🔥 ${gam.streakDays()} gün seri"
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
        if (visible) {
            cardSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
            cardBlockedApps.setOnClickListener { startActivity(Intent(this, BlockedAppsActivity::class.java)) }
            cardTimeLimits.setOnClickListener { startActivity(Intent(this, TimeLimitsActivity::class.java)) }
        }
    }
}
