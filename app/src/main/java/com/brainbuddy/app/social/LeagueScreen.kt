package com.brainbuddy.app.social

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs

/** Scaffold for League/Friends. Safe reactions only. */
class LeagueScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(android.content.Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_league)

        val profileStore = ProfileStore(this)
        val gamification = GamificationStore(this)
        val reactionStore = ReactionStore(this)
        val userId = profileStore.getCurrentProfileId()

        val container = findViewById<android.widget.LinearLayout>(R.id.containerLeague)
        val profiles = profileStore.getProfiles()
        for (p in profiles) {
            val row = layoutInflater.inflate(R.layout.item_league_row, container, false)
            val tvName = row.findViewById<android.widget.TextView>(R.id.tvName)
            val tvCounts = row.findViewById<android.widget.TextView>(R.id.tvCounts)
            val xp = if (p.id == userId) gamification.xp() else 0
            tvName.text = "${p.name} - $xp XP"
            fun updateCounts() {
                val counts = reactionStore.getReactionCounts(p.id)
                tvCounts.text = counts.entries.joinToString(" ") { "${it.key.emoji}${it.value}" }
            }
            updateCounts()
            row.findViewById<android.widget.Button>(R.id.btnLike).setOnClickListener {
                reactionStore.addReaction(userId, p.id, ReactionType.LIKE)
                updateCounts()
            }
            row.findViewById<android.widget.Button>(R.id.btnBravo).setOnClickListener {
                reactionStore.addReaction(userId, p.id, ReactionType.BRAVO)
                updateCounts()
            }
            row.findViewById<android.widget.Button>(R.id.btnCongrats).setOnClickListener {
                reactionStore.addReaction(userId, p.id, ReactionType.CONGRATS)
                updateCounts()
            }
            container.addView(row)
        }

        findViewById<android.widget.Button>(R.id.btnLeagueBack).setOnClickListener { finish() }
    }
}
