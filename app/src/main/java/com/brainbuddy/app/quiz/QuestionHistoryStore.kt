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
            timesCorrect = h.timesCorrect + if (correct) 1 else 0,
            timesWrong = h.timesWrong + if (correct) 0 else 1,
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

    fun getRecentlySeenIds(limit: Int = 100): Set<String> {
        val allKeys = prefs.all.keys.filter { it.startsWith("q_") }
        val withTime = allKeys.mapNotNull { key ->
            val id = key.removePrefix("q_")
            val h = getHistory(id) ?: return@mapNotNull null
            id to h.lastSeenAt
        }
        return withTime.sortedByDescending { it.second }.take(limit).map { it.first }.toSet()
    }

    fun getAllWrongIds(): Set<String> {
        val allKeys = prefs.all.keys.filter { it.startsWith("q_") }
        return allKeys.mapNotNull { key ->
            val id = key.removePrefix("q_")
            val h = getHistory(id) ?: return@mapNotNull null
            if (h.timesWrong > 0) id else null
        }.toSet()
    }

    companion object {
        private const val PREFS = "bb_question_history"
    }
}
