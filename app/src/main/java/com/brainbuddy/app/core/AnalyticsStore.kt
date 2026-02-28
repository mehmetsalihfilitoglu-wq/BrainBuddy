package com.brainbuddy.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class QuizSession(
    val tsMs: Long,
    val correct: Int,
    val total: Int,
    val pointsEarned: Int
)

class AnalyticsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun recordSession(session: QuizSession) {
        val arr = JSONArray(prefs.getString(KEY_SESSIONS, "[]"))
        val obj = JSONObject()
            .put("tsMs", session.tsMs)
            .put("correct", session.correct)
            .put("total", session.total)
            .put("pointsEarned", session.pointsEarned)

        arr.put(obj)

        val trimmed = JSONArray()
        val start = (arr.length() - 200).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))

        prefs.edit().putString(KEY_SESSIONS, trimmed.toString()).apply()
    }

    fun getSessions(): List<QuizSession> {
        val arr = JSONArray(prefs.getString(KEY_SESSIONS, "[]"))
        val out = ArrayList<QuizSession>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                QuizSession(
                    tsMs = o.getLong("tsMs"),
                    correct = o.getInt("correct"),
                    total = o.getInt("total"),
                    pointsEarned = o.getInt("pointsEarned")
                )
            )
        }
        return out
    }

    companion object {
        private const val PREFS = "bb_analytics"
        private const val KEY_SESSIONS = "sessions_json"
    }
}