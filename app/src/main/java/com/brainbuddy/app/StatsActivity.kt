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
        val counts = analytics.getOverallCounts()
        b.tvOverallAccuracy.text = if (counts.total > 0) {
            "${counts.correct}/${counts.total} doğru (${"%.1f".format(100.0 * counts.correct / counts.total)}%)"
        } else "Henüz veri yok"

        val strongest = analytics.getStrongestTopicsWithCounts(3)
        b.tvStrongTopics.text = if (strongest.isEmpty()) "-" else strongest.joinToString("\n") { (topic, tc) ->
            "$topic: ${tc.correct}/${tc.total} doğru (${tc.wrong} yanlış)"
        }

        val weakest = analytics.getWeakestTopicsWithCounts(3)
        b.tvWeakTopics.text = if (weakest.isEmpty()) "-" else weakest.joinToString("\n") { (topic, tc) ->
            "$topic: ${tc.correct}/${tc.total} doğru (${tc.wrong} yanlış)"
        }

        val recent = analytics.getLastTests(10)
        b.tvRecentTrend.text = if (recent.isEmpty()) "Henüz veri yok" else recent.joinToString(" → ") { p ->
            "${p.correctCount}/${p.effectiveTotal}"
        }

        b.tvPerTestDetail.text = if (recent.isEmpty()) "-" else recent.takeLast(5).mapIndexed { i, p ->
            val t = p.effectiveTotal
            "Test ${i + 1}: Doğru ${p.correctCount}/$t, Yanlış ${p.wrongCount}/$t, Boş ${p.blankCount}/$t"
        }.joinToString("\n")

        b.btnBack.setOnClickListener { finish() }
    }
}
