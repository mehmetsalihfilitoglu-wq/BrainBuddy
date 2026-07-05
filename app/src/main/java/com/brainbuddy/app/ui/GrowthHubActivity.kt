package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.brainbuddy.app.R
import com.brainbuddy.app.StatsActivity
import com.brainbuddy.app.core.AnalyticsStore
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.WeeklyRewardStore
import com.brainbuddy.app.quiz.WrongPoolLauncher
import com.brainbuddy.app.quiz.WrongQuestionPoolStore
import com.brainbuddy.app.social.LeagueScreen
import kotlin.math.roundToInt

class GrowthHubActivity : AppCompatActivity() {

    private lateinit var gam: GamificationStore
    private lateinit var analytics: AnalyticsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_growth_hub)

        gam = GamificationStore(this)
        analytics = AnalyticsStore(this)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        setupNavigation()
        refreshAll()
    }

    override fun onResume() {
        super.onResume()
        refreshHeroStats()
        refreshWrongPool()
    }

    private fun refreshAll() {
        refreshHeroStats()
        refreshTopics()
        refreshWrongPool()
    }

    private fun refreshHeroStats() {
        val freezeSuffix = if (gam.freezeTokens() > 0) " 🧊" else ""
        findViewById<TextView>(R.id.tvHeroStreak).text = "${gam.streakDays()}$freezeSuffix"
        findViewById<TextView>(R.id.tvHeroXp).text = "${gam.xp()}"
        findViewById<TextView>(R.id.tvHeroLevel).text = "${gam.level()}"

        val counts = analytics.getOverallCounts()
        val accuracy = analytics.getOverallAccuracy()
        findViewById<TextView>(R.id.tvHeroAccuracy).text =
            if (counts.total > 0) "%${accuracy.roundToInt()}" else "—"
    }

    private fun refreshTopics() {
        val strongest = analytics.getStrongestTopicsWithCounts(3)
        val weakest = analytics.getWeakestTopicsWithCounts(3).filter { it.second.total >= 3 }

        val chipGroupStrong = findViewById<ChipGroup>(R.id.chipGroupStrong)
        val tvStrongEmpty = findViewById<TextView>(R.id.tvStrongEmpty)
        chipGroupStrong.removeAllViews()
        if (strongest.isEmpty()) {
            tvStrongEmpty.visibility = View.VISIBLE
        } else {
            tvStrongEmpty.visibility = View.GONE
            strongest.forEach { (topic, tc) ->
                chipGroupStrong.addView(Chip(this, null, R.style.Widget_BrainBuddy_Chip_Stat).apply {
                    text = "$topic  ${tc.correct}/${tc.total}"
                    isClickable = false
                    isCheckable = false
                })
            }
        }

        val chipGroupWeak = findViewById<ChipGroup>(R.id.chipGroupWeak)
        val tvWeakEmpty = findViewById<TextView>(R.id.tvWeakEmpty)
        chipGroupWeak.removeAllViews()
        if (weakest.isEmpty()) {
            tvWeakEmpty.visibility = View.VISIBLE
        } else {
            tvWeakEmpty.visibility = View.GONE
            weakest.forEach { (topic, tc) ->
                chipGroupWeak.addView(Chip(this, null, R.style.Widget_BrainBuddy_Chip_Stat).apply {
                    text = "$topic  ${tc.correct}/${tc.total}"
                    isClickable = false
                    isCheckable = false
                })
            }
        }
    }

    private fun refreshWrongPool() {
        val card = findViewById<MaterialCardView>(R.id.cardWrongPool)
        val pool = WrongQuestionPoolStore(this)
        card.visibility = if (pool.isNotEmpty()) View.VISIBLE else View.GONE
        if (pool.isNotEmpty()) {
            card.setOnClickListener { WrongPoolLauncher.launch(this) }
        }
    }

    private fun setupNavigation() {
        val weeklyReward = WeeklyRewardStore(this)

        findViewById<MaterialCardView>(R.id.cardDetailedStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }
        findViewById<MaterialCardView>(R.id.cardLeague).setOnClickListener {
            startActivity(Intent(this, LeagueScreen::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardWeeklyChest).setOnClickListener {
            val tokens = weeklyReward.claimWeeklyChest()
            if (tokens > 0) {
                android.widget.Toast.makeText(this, "+$tokens donma jetonu!", android.widget.Toast.LENGTH_SHORT).show()
            } else if (weeklyReward.canClaimWeeklyChest()) {
                android.widget.Toast.makeText(this, getString(R.string.progress_chest_xp_hint), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
