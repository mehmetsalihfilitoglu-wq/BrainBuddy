package com.brainbuddy.app.social

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.brainbuddy.app.R
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityLeagueBinding
import com.brainbuddy.app.league.LeagueEntry
import com.brainbuddy.app.league.LeagueHelper
import com.brainbuddy.app.league.LeagueStore
import com.brainbuddy.app.league.LeagueTier

class LeagueScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(android.content.Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }

        val b = ActivityLeagueBinding.inflate(layoutInflater)
        setContentView(b.root)

        val store = LeagueStore(this)
        val profileStore = ProfileStore(this)
        val profileId = profileStore.getCurrentProfileId()

        val tier = store.getCurrentTier()
        b.tvTierName.text = tier.displayName
        b.tvTierEmoji.text = tier.emoji
        b.tvWeeklyScore.text = "Haftalık puan: ${store.getWeeklyScore()}"

        val daysLeft = LeagueHelper.getDaysUntilWeekEnd()
        b.tvCountdown.text = "Haftanın bitmesine: $daysLeft gün"

        val notes = listOf("Bu hafta antrenman ligi", "Bu hafta pratik arena")
        b.tvLeagueNote.text = notes[(System.currentTimeMillis() % 2).toInt()]

        val entries = LeagueHelper.getLeaderboardEntries(this)
        b.recyclerLeaderboard.layoutManager = LinearLayoutManager(this)
        b.recyclerLeaderboard.adapter = LeagueAdapter(entries, profileId)

        b.btnLeagueBack.setOnClickListener { finish() }
    }
}

class LeagueAdapter(
    private val entries: List<LeagueEntry>,
    private val currentUserId: String
) : androidx.recyclerview.widget.RecyclerView.Adapter<LeagueAdapter.VH>() {

    class VH(val view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_league_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = entries[position]
        val rank = position + 1
        val card = holder.view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardEntry)

        holder.view.findViewById<android.widget.TextView>(R.id.tvRank).text = rank.toString()
        holder.view.findViewById<android.widget.TextView>(R.id.tvAvatar).text = "👤"
        holder.view.findViewById<android.widget.TextView>(R.id.tvName).text = entry.displayName
        holder.view.findViewById<android.widget.TextView>(R.id.tvScore).text = if (entry.id == currentUserId) "Sen" else ""
        holder.view.findViewById<android.widget.TextView>(R.id.tvPoints).text = "${entry.weeklyScore}"

        val chipNpc = holder.view.findViewById<com.google.android.material.chip.Chip>(R.id.chipNpc)
        chipNpc.visibility = if (entry.isNpc) android.view.View.VISIBLE else android.view.View.GONE
        chipNpc.text = "NPC"

        val total = entries.size
        when {
            rank <= 3 -> {
                card.setCardBackgroundColor(holder.view.context.getColor(R.color.bb_promotion_zone))
            }
            rank > total - 3 -> {
                card.setCardBackgroundColor(holder.view.context.getColor(R.color.bb_demotion_zone))
            }
            else -> {
                card.setCardBackgroundColor(holder.view.context.getColor(R.color.white))
            }
        }
    }

    override fun getItemCount() = entries.size
}
