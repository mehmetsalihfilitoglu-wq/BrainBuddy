package com.brainbuddy.app.social

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityLeagueBinding
import com.brainbuddy.app.league.LeagueEntry
import com.brainbuddy.app.league.LeagueHelper
import com.brainbuddy.app.league.LeagueScoring
import com.brainbuddy.app.league.LeagueStore
import com.brainbuddy.app.league.LeagueTier

class LeagueScreen : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(
                android.content.Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            finish()
            return
        }

        val b = ActivityLeagueBinding.inflate(layoutInflater)
        setContentView(b.root)

        val store = LeagueStore(this)
        val profileStore = ProfileStore(this)
        val profileId = profileStore.getCurrentProfileId()
        val gam = GamificationStore(this)

        // Tier info
        val tier = store.getCurrentTier()
        b.tvTierName.text = tier.displayName
        b.tvTierEmoji.text = tier.emoji
        b.tvWeeklyScore.text = getString(R.string.league_weekly_score, store.getWeeklyScore())

        val daysLeft = LeagueHelper.getDaysUntilWeekEnd()
        b.tvCountdown.text = getString(R.string.league_days_left, daysLeft)

        // Tier progress
        val nextTier = tier.nextTier()
        if (nextTier != null) {
            b.tvTierProgress.text = getString(R.string.league_tier_progress, store.getWeeklyScore(), nextTier.minScoreToPromote)
            b.tvTierProgress.visibility = View.VISIBLE
        }

        // Streak info
        val streakDays = gam.streakDays()
        val streakBonus = LeagueScoring.computeStreakBonus(streakDays)
        if (streakBonus > 0) {
            b.tvStreakInfo.text = getString(R.string.league_streak_bonus, streakDays, streakBonus)
            b.tvStreakInfo.visibility = View.VISIBLE
        }

        // Build leaderboard
        val rawEntries = try {
            LeagueHelper.getLeaderboardEntries(this)
        } catch (_: Throwable) {
            emptyList()
        }

        val entries: List<LeagueEntry> = rawEntries.filterNotNull().sortedByDescending { it.weeklyScore }

        if (entries.isEmpty()) {
            b.recyclerLeaderboard.visibility = View.GONE
            b.cardLeagueEmpty.visibility = View.VISIBLE
            b.layoutPodium.root.visibility = View.GONE
            b.cardMotivation.visibility = View.GONE
        } else if (entries.size < 3) {
            b.layoutPodium.root.visibility = View.GONE
            b.cardLeagueEmpty.visibility = View.GONE
            b.recyclerLeaderboard.visibility = View.VISIBLE
            b.recyclerLeaderboard.layoutManager = LinearLayoutManager(this)
            b.recyclerLeaderboard.adapter = LeagueAdapter(entries, entries, profileId, 1)
            setupMotivation(b, entries, profileId)
        } else {
            b.cardLeagueEmpty.visibility = View.GONE

            // Bind podium (top 3)
            bindPodium(b, entries, profileId)

            // Rest goes to RecyclerView (ranks 4+)
            val restEntries = entries.drop(3)
            if (restEntries.isNotEmpty()) {
                b.recyclerLeaderboard.visibility = View.VISIBLE
                b.recyclerLeaderboard.layoutManager = LinearLayoutManager(this)
                b.recyclerLeaderboard.adapter = LeagueAdapter(restEntries, entries, profileId, 4)
            } else {
                b.recyclerLeaderboard.visibility = View.GONE
            }

            setupMotivation(b, entries, profileId)
        }

        b.btnLeagueBack.setOnClickListener { finish() }
    }

    private fun bindPodium(b: ActivityLeagueBinding, entries: List<LeagueEntry>, profileId: String) {
        val podium = b.layoutPodium
        podium.root.visibility = View.VISIBLE

        val first = entries[0]
        val second = entries[1]
        val third = entries[2]

        // 1st place (center)
        podium.tvPodiumName1.text = first.displayName
        podium.tvPodiumScore1.text = "${first.weeklyScore} puan"
        bindPodiumAvatar(podium.imgPodiumAvatar1, first)
        if (first.id == profileId) {
            podium.tvPodiumYou1.text = "Sen"
            podium.tvPodiumYou1.visibility = View.VISIBLE
            podium.podium1stCard.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            podium.podium1stCard.strokeColor = getColor(R.color.bb_primary)
        }

        // 2nd place (left)
        podium.tvPodiumName2.text = second.displayName
        podium.tvPodiumScore2.text = "${second.weeklyScore} puan"
        bindPodiumAvatar(podium.imgPodiumAvatar2, second)
        if (second.id == profileId) {
            podium.tvPodiumYou2.text = "Sen"
            podium.tvPodiumYou2.visibility = View.VISIBLE
            podium.podium2ndCard.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            podium.podium2ndCard.strokeColor = getColor(R.color.bb_primary)
        }

        // 3rd place (right)
        podium.tvPodiumName3.text = third.displayName
        podium.tvPodiumScore3.text = "${third.weeklyScore} puan"
        bindPodiumAvatar(podium.imgPodiumAvatar3, third)
        if (third.id == profileId) {
            podium.tvPodiumYou3.text = "Sen"
            podium.tvPodiumYou3.visibility = View.VISIBLE
            podium.podium3rdCard.strokeWidth = (2 * resources.displayMetrics.density).toInt()
            podium.podium3rdCard.strokeColor = getColor(R.color.bb_primary)
        }
    }

    /**
     * Loads the mascot drawable for a league entry into a podium ImageView.
     * Uses "MASCOT" key from avatarCosmetics; falls back to the default mascot.
     */
    private fun bindPodiumAvatar(imgView: android.widget.ImageView, entry: LeagueEntry) {
        val mascotId = entry.avatarCosmetics["MASCOT"]
        val mascotRes = if (!mascotId.isNullOrEmpty()) {
            com.brainbuddy.app.avatar.AvatarCatalog.items()
                .find { it.id == mascotId }
                ?.previewDrawableRes
                ?.takeIf { it != 0 }
        } else null
        imgView.setImageResource(mascotRes ?: R.drawable.avatar_mascot_brainy)
    }

    private fun setupMotivation(b: ActivityLeagueBinding, entries: List<LeagueEntry>, profileId: String) {
        val userIndex = entries.indexOfFirst { it.id == profileId }
        if (userIndex < 0) return

        val userRank = userIndex + 1
        val userScore = entries[userIndex].weeklyScore

        when {
            userRank == 1 -> {
                b.cardMotivation.visibility = View.VISIBLE
                b.tvMotivation.text = "👑 Lidersin! Devam et!"
            }
            userRank <= 3 -> {
                val above = entries[userIndex - 1]
                val gap = above.weeklyScore - userScore
                if (gap > 0) {
                    b.cardMotivation.visibility = View.VISIBLE
                    b.tvMotivation.text = "🎯 +$gap puan daha → ${userRank - 1}. sıra!"
                }
            }
            else -> {
                val above = entries[userIndex - 1]
                val gap = above.weeklyScore - userScore
                if (gap > 0) {
                    b.cardMotivation.visibility = View.VISIBLE
                    b.tvMotivation.text = "💪 +$gap puan daha → ${userRank - 1}. sıra!"
                }
                // Also check demotion zone warning
                val demotionThreshold = (entries.size - 3).coerceAtLeast(4)
                if (userRank > demotionThreshold) {
                    val safeEntry = entries[demotionThreshold - 1]
                    val escapeGap = safeEntry.weeklyScore - userScore
                    if (escapeGap > 0) {
                        b.cardMotivation.visibility = View.VISIBLE
                        b.tvMotivation.text = "⚠️ Düşme tehlikesi! +$escapeGap puan → güvenli bölge"
                    }
                }
            }
        }
    }
}

class LeagueAdapter(
    private val entries: List<LeagueEntry>,
    private val allEntries: List<LeagueEntry>,
    private val currentUserId: String,
    private val startRank: Int
) : androidx.recyclerview.widget.RecyclerView.Adapter<LeagueAdapter.VH>() {

    class VH(val view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val v = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_league_entry, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val entry = entries[position]
        val rank = startRank + position
        val card = holder.view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardEntry)
        val density = holder.view.context.resources.displayMetrics.density

        holder.view.findViewById<android.widget.TextView>(R.id.tvRank).text = rank.toString()
        val imgAvatar = holder.view.findViewById<android.widget.ImageView>(R.id.imgAvatar)
        val mascotId = entry.avatarCosmetics["MASCOT"]
        val mascotItem = if (!mascotId.isNullOrEmpty())
            com.brainbuddy.app.avatar.AvatarCatalog.items().find { it.id == mascotId }
        else null
        val mascotRes = if (mascotItem != null && mascotItem.previewDrawableRes != 0)
            mascotItem.previewDrawableRes
        else R.drawable.avatar_mascot_brainy
        imgAvatar.setImageResource(mascotRes)
        holder.view.findViewById<android.widget.TextView>(R.id.tvName).text = entry.displayName
        holder.view.findViewById<android.widget.TextView>(R.id.tvPoints).text = "${entry.weeklyScore}"

        // NPC badge
        val npcBadge = holder.view.findViewById<android.widget.TextView>(R.id.tvNpcBadge)
        npcBadge.visibility = if (entry.isNpc) android.view.View.VISIBLE else android.view.View.GONE

        // User highlight
        val tvScore = holder.view.findViewById<android.widget.TextView>(R.id.tvScore)
        val tvGapText = holder.view.findViewById<android.widget.TextView>(R.id.tvGapText)

        if (entry.id == currentUserId) {
            card.strokeWidth = (2 * density).toInt()
            card.strokeColor = holder.view.context.getColor(R.color.bb_primary)
            tvScore.text = holder.view.context.getString(R.string.league_current_user_label)
            tvScore.visibility = android.view.View.VISIBLE

            // Gap to rank above
            val allIndex = allEntries.indexOfFirst { it.id == currentUserId }
            if (allIndex > 0) {
                val above = allEntries[allIndex - 1]
                val gap = above.weeklyScore - entry.weeklyScore
                if (gap > 0) {
                    tvGapText.text = "+$gap puan → ${rank - 1}. sıra"
                    tvGapText.visibility = android.view.View.VISIBLE
                } else {
                    tvGapText.visibility = android.view.View.GONE
                }
            } else {
                tvGapText.visibility = android.view.View.GONE
            }
        } else {
            card.strokeWidth = 0
            tvScore.text = ""
            tvScore.visibility = android.view.View.GONE
            tvGapText.visibility = android.view.View.GONE
        }

        // Demotion zone coloring (bottom 3 of full leaderboard)
        val total = allEntries.size
        val demotionThreshold = (total - 3).coerceAtLeast(4)
        if (total >= 4 && rank > demotionThreshold) {
            card.setCardBackgroundColor(holder.view.context.getColor(R.color.bb_demotion_zone))
        } else {
            card.setCardBackgroundColor(holder.view.context.getColor(R.color.white))
        }
    }

    override fun getItemCount() = entries.size
}
