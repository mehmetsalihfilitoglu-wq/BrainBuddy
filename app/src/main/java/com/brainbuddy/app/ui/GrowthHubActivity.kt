package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.StatsActivity
import com.brainbuddy.app.core.WeeklyRewardStore
import com.brainbuddy.app.social.LeagueScreen

class GrowthHubActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_growth_hub)

        findViewById<android.widget.Button>(R.id.btnBack).setOnClickListener { finish() }

        val weeklyReward = WeeklyRewardStore(this)

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardLeague).setOnClickListener {
            startActivity(Intent(this, LeagueScreen::class.java))
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardStats).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))
        }

        findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardWeeklyChest).setOnClickListener {
            val tokens = weeklyReward.claimWeeklyChest()
            if (tokens > 0) {
                android.widget.Toast.makeText(this, "+$tokens donma jetonu!", android.widget.Toast.LENGTH_SHORT).show()
            } else if (weeklyReward.canClaimWeeklyChest()) {
                android.widget.Toast.makeText(this, "Daha fazla XP kazanın (Silver: 80, Gold: 150)", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
