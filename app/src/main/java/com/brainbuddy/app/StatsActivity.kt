package com.brainbuddy.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityStatsBinding
import com.brainbuddy.app.ui.BarChartView
import com.brainbuddy.app.ui.LineChartView
import com.brainbuddy.app.ui.ProgressRingView

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
        val accuracyPct = if (counts.total > 0) 100.0 * counts.correct / counts.total else 0.0
        b.tvOverallAccuracy.text = if (counts.total > 0) {
            "${counts.correct}/${counts.total} doğru (${"%.1f".format(accuracyPct)}%)"
        } else "Henüz veri yok"

        findViewById<ProgressRingView>(R.id.progressRing).progress = accuracyPct.toFloat()

        val topicCounts = analytics.getTopicMasteryWithCounts()
        val barData = topicCounts.map { (topic, tc) ->
            BarChartView.BarData(topic, tc.correct, tc.total)
        }.take(6)
        findViewById<BarChartView>(R.id.barChart).data = barData

        val recent = analytics.getLastTests(10)
        val trendValues = recent.map { it.accuracy }
        findViewById<LineChartView>(R.id.lineChart).values = trendValues

        val strongest = analytics.getStrongestTopicsWithCounts(3)
        b.tvStrongTopics.text = if (strongest.isEmpty()) "-" else strongest.joinToString("\n") { (topic, tc) ->
            "$topic: ${tc.correct}/${tc.total} doğru (${tc.wrong} yanlış)"
        }

        val weakest = analytics.getWeakestTopicsWithCounts(3)
        b.tvWeakTopics.text = if (weakest.isEmpty()) "-" else weakest.joinToString("\n") { (topic, tc) ->
            "$topic: ${tc.correct}/${tc.total} doğru (${tc.wrong} yanlış)"
        }

        b.tvPerTestDetail.text = if (recent.isEmpty()) "-" else recent.takeLast(5).mapIndexed { i, p ->
            val t = p.effectiveTotal
            "Test ${i + 1}: Doğru ${p.correctCount}/$t, Yanlış ${p.wrongCount}/$t, Boş ${p.blankCount}/$t"
        }.joinToString("\n")

        b.btnBack.setOnClickListener { finish() }
    }
}
