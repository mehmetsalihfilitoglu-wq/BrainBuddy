package com.brainbuddy.app.league

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Builds the leaderboard for display. Applies daily variation to NPC scores.
 */
object LeagueHelper {

    private const val LEADERBOARD_SIZE = 15

    fun getLeaderboardEntries(context: Context): List<LeagueEntry> {
        val store = LeagueStore(context)
        val profileStore = com.brainbuddy.app.core.ProfileStore(context)
        val profileId = profileStore.getCurrentProfileId()
        val profile = profileStore.getProfile(profileId)
        val studentName = profile?.name ?: "Öğrenci"

        val weekStart = store.getCurrentWeekStartMs()
        val npcTargets = store.getNpcTargetScores()

        if (weekStart == 0L || npcTargets.isEmpty()) {
            ensureInitialLeagueState(context)
            return getLeaderboardEntries(context)
        }

        val dayIdx = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val npcScoresWithVariation = LeagueNpcGenerator.applyDailyVariation(npcTargets, dayIdx)

        val npcs = LeagueNpcGenerator.generateNpcProfiles(
            count = (LEADERBOARD_SIZE - 1).coerceAtLeast(1),
            seed = weekStart
        )

        val entries = mutableListOf<LeagueEntry>()
        entries.add(LeagueEntry(
            id = profileId,
            displayName = studentName,
            weeklyScore = store.getWeeklyScore(),
            isNpc = false
        ))
        npcs.forEach { npc ->
            entries.add(LeagueEntry(
                id = npc.id,
                displayName = npc.displayName,
                weeklyScore = npcScoresWithVariation[npc.id] ?: 50,
                isNpc = true,
                avatarCosmetics = npc.avatarCosmetics
            ))
        }

        return entries.sortedByDescending { it.weeklyScore }
    }

    fun getDaysUntilWeekEnd(): Int {
        val cal = java.util.Calendar.getInstance()
        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val daysUntilMonday = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> 7
            java.util.Calendar.TUESDAY -> 6
            java.util.Calendar.WEDNESDAY -> 5
            java.util.Calendar.THURSDAY -> 4
            java.util.Calendar.FRIDAY -> 3
            java.util.Calendar.SATURDAY -> 2
            else -> 1
        }
        return daysUntilMonday
    }

    private fun ensureInitialLeagueState(context: Context) {
        val store = LeagueStore(context)
        if (store.getCurrentWeekStartMs() == 0L) {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            if (cal.timeInMillis > System.currentTimeMillis()) {
                cal.add(java.util.Calendar.DAY_OF_MONTH, -7)
            }
            store.setWeekStartMs(cal.timeInMillis)

            val analytics = com.brainbuddy.app.core.AnalyticsStore(context)
            val perfs = analytics.getTestPerformances().takeLast(20)
            val twoWeeksAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)
            val recent = perfs.filter { it.tsMs >= twoWeeksAgo }
            val studentExpected = when {
                recent.isEmpty() -> 80
                else -> {
                    val total = recent.sumOf { it.correctCount + it.wrongCount + it.blankCount }
                    val correct = recent.sumOf { it.correctCount }
                    val acc = if (total > 0) correct.toFloat() / total else 0.7f
                    val avg = when {
                        acc >= 0.9f -> 35
                        acc >= 0.8f -> 28
                        acc >= 0.7f -> 22
                        acc >= 0.6f -> 15
                        else -> 10
                    }
                    (recent.size / 2f * avg).toInt().coerceIn(20, 300)
                }
            }
            val npcs = LeagueNpcGenerator.generateNpcProfiles(14, cal.timeInMillis)
            val targets = LeagueNpcGenerator.generateNpcTargetScores(studentExpected, npcs, cal.timeInMillis)
            store.setNpcTargetScores(targets)
        }
    }
}
