package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityStatsBinding

class StatsActivity : AppCompatActivity() {

    private lateinit var b: ActivityStatsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, LockScreenActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        b = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(b.root)

        val gam = GamificationStore(this)
        val analytics = AnalyticsStore(this)

        b.tvLevel.text = "Seviye ${gam.level()}"
        b.tvXp.text = "${gam.xp()} XP"
        b.tvStreak.text = "🔥 ${gam.streakDays()} gün seri"
        b.tvOverallAccuracy.text = "%.1f%%".format(analytics.getOverallAccuracy())

        val strongest = analytics.getStrongestTopics(3)
        b.tvStrongTopics.text = if (strongest.isEmpty()) "-" else strongest.joinToString(", ") { "${it.first} (${"%.0f".format(it.second)}%)" }

        val weakest = analytics.getWeakestTopics(3)
        b.tvWeakTopics.text = if (weakest.isEmpty()) "-" else weakest.joinToString(", ") { "${it.first} (${"%.0f".format(it.second)}%)" }

        val recent = analytics.getLastAccuracies(10)
        b.tvRecentTrend.text = if (recent.isEmpty()) "Henüz veri yok" else recent.joinToString(" → ") { "%.0f".format(it) }

        b.btnBack.setOnClickListener { finish() }
    }
}
