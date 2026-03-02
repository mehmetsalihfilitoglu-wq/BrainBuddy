package com.brainbuddy.app.league

import android.content.Context
import com.brainbuddy.app.core.ProfileScopedPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class LeagueStore(context: Context) {
    private val prefs = ProfileScopedPrefs.league(context)

    fun getCurrentTier(): LeagueTier {
        val name = prefs.getString(KEY_TIER, LeagueTier.BASLANGIC.name) ?: LeagueTier.BASLANGIC.name
        return try { LeagueTier.valueOf(name) } catch (_: Exception) { LeagueTier.BASLANGIC }
    }

    fun setCurrentTier(tier: LeagueTier) {
        prefs.edit().putString(KEY_TIER, tier.name).apply()
    }

    fun getWeeklyScore(): Int = prefs.getInt(KEY_WEEKLY_SCORE, 0)
    fun addWeeklyScore(delta: Int) {
        prefs.edit().putInt(KEY_WEEKLY_SCORE, (getWeeklyScore() + delta).coerceAtLeast(0)).apply()
    }
    fun resetWeeklyScore() = prefs.edit().putInt(KEY_WEEKLY_SCORE, 0).apply()

    /** Week identifier: Monday-based week start timestamp. */
    fun getCurrentWeekStartMs(): Long = prefs.getLong(KEY_WEEK_START_MS, 0L)
    fun setWeekStartMs(ms: Long) = prefs.edit().putLong(KEY_WEEK_START_MS, ms).apply()

    /** Tests completed today (for anti-farming). */
    fun getTestsCompletedToday(): Int {
        val dayIdx = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val savedDay = prefs.getLong(KEY_LAST_LEAGUE_DAY, -1L)
        val savedCount = prefs.getInt(KEY_TESTS_TODAY, 0)
        return if (savedDay == dayIdx) savedCount else 0
    }
    fun incrementTestsToday() {
        val dayIdx = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        val savedDay = prefs.getLong(KEY_LAST_LEAGUE_DAY, -1L)
        val savedCount = prefs.getInt(KEY_TESTS_TODAY, 0)
        val count = if (savedDay == dayIdx) savedCount + 1 else 1
        prefs.edit().putLong(KEY_LAST_LEAGUE_DAY, dayIdx).putInt(KEY_TESTS_TODAY, count).apply()
    }

    /** NPC target scores (id -> score) for current week. Regenerated on reset. */
    fun getNpcTargetScores(): Map<String, Int> {
        val json = prefs.getString(KEY_NPC_TARGETS, "{}") ?: "{}"
        return try {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getInt(it) }
        } catch (_: Exception) { emptyMap() }
    }
    fun setNpcTargetScores(map: Map<String, Int>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY_NPC_TARGETS, o.toString()).apply()
    }

    /** Last seed used for NPC generation (for daily variation). */
    fun getNpcSeedDay(): Long = prefs.getLong(KEY_NPC_SEED_DAY, -1L)
    fun setNpcSeedDay(day: Long) = prefs.edit().putLong(KEY_NPC_SEED_DAY, day).apply()

    /** Parent-only: points breakdown per test for analytics. */
    fun recordPointsBreakdown(breakdown: LeagueScoring.PointsBreakdown, quizId: String, tsMs: Long) {
        val arr = JSONArray(prefs.getString(KEY_POINTS_BREAKDOWN, "[]") ?: "[]")
        val o = JSONObject()
            .put("quizId", quizId)
            .put("tsMs", tsMs)
            .put("base", breakdown.base)
            .put("wrongBonus", breakdown.wrongBonus)
            .put("blankPenalty", breakdown.blankPenalty)
            .put("failPenalty", breakdown.failPenalty)
            .put("rawTotal", breakdown.rawTotal)
            .put("finalPoints", breakdown.finalPoints)
            .put("wrongCount", breakdown.wrongCount)
            .put("blankCount", breakdown.blankCount)
            .put("isGateFail", breakdown.isGateFail)
            .put("testIndexOfDay", breakdown.testIndexOfDay)
        arr.put(o)
        trimAndSaveBreakdown(arr, 100)
    }

    fun getPointsBreakdowns(): List<LeaguePointsBreakdownRecord> {
        val arr = JSONArray(prefs.getString(KEY_POINTS_BREAKDOWN, "[]") ?: "[]")
        return (0 until arr.length()).mapNotNull { i ->
            try {
                val o = arr.getJSONObject(i)
                LeaguePointsBreakdownRecord(
                    quizId = o.getString("quizId"),
                    tsMs = o.getLong("tsMs"),
                    base = o.optInt("base", 10),
                    wrongBonus = o.optInt("wrongBonus", 0),
                    blankPenalty = o.optInt("blankPenalty", 0),
                    failPenalty = o.optInt("failPenalty", 0),
                    rawTotal = o.optInt("rawTotal", 0),
                    finalPoints = o.optInt("finalPoints", 0),
                    wrongCount = o.optInt("wrongCount", 0),
                    blankCount = o.optInt("blankCount", 0),
                    isGateFail = o.optBoolean("isGateFail", false),
                    testIndexOfDay = o.optInt("testIndexOfDay", 0)
                )
            } catch (_: Exception) { null }
        }
    }

    private fun trimAndSaveBreakdown(arr: JSONArray, maxSize: Int) {
        val trimmed = JSONArray()
        val start = (arr.length() - maxSize).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString(KEY_POINTS_BREAKDOWN, trimmed.toString()).apply()
    }

    data class LeaguePointsBreakdownRecord(
        val quizId: String,
        val tsMs: Long,
        val base: Int,
        val wrongBonus: Int,
        val blankPenalty: Int,
        val failPenalty: Int,
        val rawTotal: Int,
        val finalPoints: Int,
        val wrongCount: Int,
        val blankCount: Int,
        val isGateFail: Boolean,
        val testIndexOfDay: Int
    )

    companion object {
        private const val KEY_TIER = "tier"
        private const val KEY_WEEKLY_SCORE = "weekly_score"
        private const val KEY_WEEK_START_MS = "week_start_ms"
        private const val KEY_LAST_LEAGUE_DAY = "last_league_day"
        private const val KEY_TESTS_TODAY = "tests_today"
        private const val KEY_NPC_TARGETS = "npc_targets"
        private const val KEY_NPC_SEED_DAY = "npc_seed_day"
        private const val KEY_POINTS_BREAKDOWN = "points_breakdown"
    }
}
