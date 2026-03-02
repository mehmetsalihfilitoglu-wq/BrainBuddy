package com.brainbuddy.app.league

/**
 * League tiers (Duolingo-style cute names).
 * Order: Başlangıç < Bronz < Gümüş < Altın < Elmas < Efsane
 */
enum class LeagueTier(val displayName: String, val emoji: String, val minScoreToPromote: Int) {
    BASLANGIC("Başlangıç", "🌱", 30),
    BRONZ("Bronz", "🥉", 50),
    GUMUS("Gümüş", "🥈", 80),
    ALTIN("Altın", "🥇", 120),
    ELMAS("Elmas", "💎", 180),
    EFSANE("Efsane", "👑", 250);

    val index: Int get() = entries.indexOf(this)
    fun nextTier(): LeagueTier? = entries.getOrNull(index + 1)
    fun prevTier(): LeagueTier? = entries.getOrNull(index - 1)
}

/** Single entry in the leaderboard (student or NPC). */
data class LeagueEntry(
    val id: String,
    val displayName: String,
    val weeklyScore: Int,
    val isNpc: Boolean,
    val avatarCosmetics: Map<String, String> = emptyMap()
)

/** NPC profile for simulated opponents. */
data class NpcProfile(
    val id: String,
    val displayName: String,
    val avatarCosmetics: Map<String, String> = emptyMap(),
    val isNpc: Boolean = true
)
