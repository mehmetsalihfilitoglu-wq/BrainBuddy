package com.mioacademy.app.core

import android.content.Context

/**
 * Maps internal badge IDs to human-readable display names and icons.
 * No raw IDs should be shown to users.
 */
object BadgeDisplayHelper {

    private val badgeDisplayNames = mapOf(
        "100_puan" to "100 XP",
        "500_puan" to "500 XP",
        "seviye_5" to "Seviye 5",
        "seri_3" to "3 Gün Seri",
        "seri_7" to "7 Gün Seri",
        GamificationStore.MILESTONE_7 to "7 Gün Seri Başarısı",
        GamificationStore.MILESTONE_30 to "30 Gün Avatar Kilidi",
        GamificationStore.MILESTONE_100 to "Efsane Seri"
    )

    fun getDisplayName(badgeId: String): String = badgeDisplayNames[badgeId] ?: badgeId

    fun getDisplayNames(badgeIds: Collection<String>): List<String> =
        badgeIds.map { getDisplayName(it) }

    fun getBadgeEmoji(badgeId: String): String = when (badgeId) {
        "100_puan", "500_puan" -> "⭐"
        "seviye_5" -> "📊"
        "seri_3", "seri_7" -> "🔥"
        GamificationStore.MILESTONE_7 -> "🏆"
        GamificationStore.MILESTONE_30 -> "🎭"
        GamificationStore.MILESTONE_100 -> "👑"
        else -> "🏅"
    }
}
