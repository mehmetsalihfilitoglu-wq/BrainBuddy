package com.mioacademy.app.league

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Weekly reset worker: runs Monday 00:10 local time.
 * - finalize week (promote/demote)
 * - reset weekly score
 * - regenerate NPC targets
 */
class LeagueWeeklyWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        val store = LeagueStore(appContext)
        val analytics = com.mioacademy.app.core.AnalyticsStore(appContext)

        val currentWeekStart = getMonday00Ms(System.currentTimeMillis())
        val storedWeekStart = store.getCurrentWeekStartMs()

        if (storedWeekStart > 0 && storedWeekStart < currentWeekStart) {
            val prevWeeklyScore = store.getWeeklyScore()
            val tier = store.getCurrentTier()

            val studentExpected = computeStudentExpectedWeeklyScore(analytics)
            val npcs = LeagueNpcGenerator.generateNpcProfiles(14, currentWeekStart)
            val targets = LeagueNpcGenerator.generateNpcTargetScores(studentExpected, npcs, currentWeekStart)

            val entries = buildLeaderboardEntries(
                studentId = com.mioacademy.app.core.ProfileStore(appContext).getCurrentProfileId(),
                studentName = com.mioacademy.app.core.ProfileStore(appContext).getProfile(com.mioacademy.app.core.ProfileStore(appContext).getCurrentProfileId())?.name ?: "Öğrenci",
                studentScore = prevWeeklyScore,
                npcs = npcs,
                npcScores = targets
            )
            val ranked = entries.filterNotNull().sortedByDescending { it.weeklyScore }

            val rawStudentRank = ranked.indexOfFirst { !it.isNpc }
            val safeStudentRank = if (rawStudentRank < 0 || ranked.isEmpty()) {
                -1
            } else {
                rawStudentRank.coerceIn(0, ranked.size - 1)
            }
            val demotionIdx = (ranked.size - 3).coerceAtLeast(0)
            val newTier = when {
                safeStudentRank < 0 -> tier
                safeStudentRank < 3 && prevWeeklyScore >= tier.minScoreToPromote -> tier.nextTier() ?: tier
                ranked.size >= 4 && safeStudentRank >= demotionIdx && tier.prevTier() != null -> tier.prevTier()!!
                else -> tier
            }

            store.setCurrentTier(newTier)
        }

        store.resetWeeklyScore()
        store.setWeekStartMs(currentWeekStart)

        val studentExpected = computeStudentExpectedWeeklyScore(analytics)
        val npcs = LeagueNpcGenerator.generateNpcProfiles(14, currentWeekStart)
        val targets = LeagueNpcGenerator.generateNpcTargetScores(studentExpected, npcs, currentWeekStart)
        store.setNpcTargetScores(targets)
        store.setNpcSeedDay(TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()))

        LeagueScheduler.scheduleNextReset(appContext)
        ListenableWorker.Result.success()
    }

    private fun computeStudentExpectedWeeklyScore(analytics: com.mioacademy.app.core.AnalyticsStore): Int {
        val perfs = analytics.getTestPerformances().takeLast(20)
        if (perfs.isEmpty()) return 80
        val twoWeeksAgo = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)
        val recent = perfs.filter { it.tsMs >= twoWeeksAgo }
        if (recent.isEmpty()) return 80
        val totalQuestions = recent.sumOf { it.correctCount + it.wrongCount + it.blankCount }
        val correct = recent.sumOf { it.correctCount }
        val accuracy = if (totalQuestions > 0) correct.toFloat() / totalQuestions else 0.7f
        val testsPerWeek = recent.size / 2f
        val avgPointsPerTest = when {
            accuracy >= 0.9f -> 35
            accuracy >= 0.8f -> 28
            accuracy >= 0.7f -> 22
            accuracy >= 0.6f -> 15
            else -> 10
        }
        return (testsPerWeek * avgPointsPerTest).toInt().coerceIn(20, 300)
    }

    private fun buildLeaderboardEntries(
        studentId: String,
        studentName: String,
        studentScore: Int,
        npcs: List<NpcProfile>,
        npcScores: Map<String, Int>
    ): List<LeagueEntry> {
        val list = mutableListOf<LeagueEntry>()
        list.add(LeagueEntry(studentId, studentName, studentScore.coerceAtLeast(0), false))
        npcs.forEach { npc ->
            list.add(LeagueEntry(
                id = npc.id,
                displayName = npc.displayName,
                weeklyScore = (npcScores[npc.id] ?: 50).coerceIn(0, 9999),
                isNpc = true,
                avatarCosmetics = npc.avatarCosmetics
            ))
        }
        return list
    }

    private fun getMonday00Ms(nowMs: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMs
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis > nowMs) {
            cal.add(Calendar.DAY_OF_MONTH, -7)
        }
        return cal.timeInMillis
    }
}
