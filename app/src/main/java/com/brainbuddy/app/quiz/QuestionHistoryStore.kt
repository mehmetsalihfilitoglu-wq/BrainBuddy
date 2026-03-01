package com.brainbuddy.app.quiz

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Persists question history for smart rotation:
 * - lastSeenAt, timesCorrect, timesWrong, lastResult
 */
class QuestionHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class HistoryEntry(
        val questionId: String,
        val lastSeenAt: Long,
        val timesCorrect: Int,
        val timesWrong: Int,
        val lastResult: String // "correct" | "wrong"
    )

    fun getHistory(questionId: String): HistoryEntry? {
        val json = prefs.getString("q_$questionId", null) ?: return null
        return try {
            val o = JSONObject(json)
            HistoryEntry(
                questionId = questionId,
                lastSeenAt = o.optLong("lastSeenAt", 0L),
                timesCorrect = o.optInt("timesCorrect", 0),
                timesWrong = o.optInt("timesWrong", 0),
                lastResult = o.optString("lastResult", "")
            )
        } catch (_: Exception) { null }
    }

    fun recordAnswer(questionId: String, correct: Boolean) {
        val h = getHistory(questionId) ?: HistoryEntry(questionId, 0L, 0, 0, "")
        val now = System.currentTimeMillis()
        val newEntry = HistoryEntry(
            questionId = questionId,
            lastSeenAt = now,
            timesCorrect = (h.timesCorrect + if (correct) 1 else 0).coerceIn(0, MAX_COUNT_CAP),
            timesWrong = (h.timesWrong + if (correct) 0 else 1).coerceIn(0, MAX_COUNT_CAP),
            lastResult = if (correct) "correct" else "wrong"
        )
        val o = JSONObject().apply {
            put("lastSeenAt", newEntry.lastSeenAt)
            put("timesCorrect", newEntry.timesCorrect)
            put("timesWrong", newEntry.timesWrong)
            put("lastResult", newEntry.lastResult)
        }
        prefs.edit().putString("q_$questionId", o.toString()).apply()
    }

    fun getWrongQuestionIds(withinDays: Int = 7): Set<String> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(withinDays.toLong())
        val allKeys = prefs.all.keys.filter { it.startsWith("q_") }
        return allKeys.mapNotNull { key ->
            val id = key.removePrefix("q_")
            val h = getHistory(id) ?: return@mapNotNull null
            if (h.lastResult == "wrong" && h.lastSeenAt >= cutoff) id else null
        }.toSet()
    }

    fun getRecentlySeenIds(limit: Int = 100): Set<String> =
        getRecentlySeenIdsForProfile(DEFAULT_PROFILE, limit)

    fun getAllWrongIds(): Set<String> {
        val allKeys = prefs.all.keys.filter { it.startsWith("q_") }
        return allKeys.mapNotNull { key ->
            val id = key.removePrefix("q_")
            val h = getHistory(id) ?: return@mapNotNull null
            if (h.timesWrong > 0) id else null
        }.toSet()
    }

    /** Record question IDs as recently seen (for gate/remedial variety). Call after quiz generation, not completion. */
    fun recordSeenIds(ids: List<String>) {
        recordSeenIdsForProfile(DEFAULT_PROFILE, ids)
    }

    /** Per-profile recent seen (rolling window ~100). Gate uses this to avoid repeats. */
    fun getRecentlySeenIdsForProfile(profileId: String, limit: Int = 100): Set<String> {
        val key = "recent_seen_$profileId"
        val json = prefs.getString(key, "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i -> arr.optString(i, null).takeIf { it.isNotBlank() } }.take(limit).toSet()
        } catch (_: Exception) { emptySet() }
    }

    fun recordSeenIdsForProfile(profileId: String, ids: List<String>) {
        val now = System.currentTimeMillis()
        prefs.edit().apply {
            ids.forEach { id ->
                val h = getHistory(id) ?: HistoryEntry(id, 0L, 0, 0, "")
                val o = org.json.JSONObject().apply {
                    put("lastSeenAt", now)
                    put("timesCorrect", h.timesCorrect)
                    put("timesWrong", h.timesWrong)
                    put("lastResult", h.lastResult)
                }
                putString("q_$id", o.toString())
            }
            val key = "recent_seen_$profileId"
            val existing = (prefs.getString(key, "[]") ?: "[]").let { s ->
                try { org.json.JSONArray(s) } catch (_: Exception) { org.json.JSONArray() }
            }
            val list = (0 until existing.length()).mapNotNull { i -> existing.optString(i, null).takeIf { it.isNotBlank() } }.toMutableList()
            ids.forEach { id -> if (id !in list) list.add(0, id) }
            val trimmed = list.take(100)
            putString(key, org.json.JSONArray(trimmed).toString())
            apply()
        }
    }

    companion object {
        private const val DEFAULT_PROFILE = "default"
        private const val PREFS = "bb_question_history"
        private const val MAX_COUNT_CAP = 10000
    }
}
