package com.edumio.app.quiz

import android.content.Context
import com.edumio.app.core.ProfileScopedPrefs
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Stores wrong answers and tracks improvement (wrong → fixed).
 * Used for: wrong questions reappearing in future tests, parent improvement stats.
 */
class WrongQuestionStore(context: Context) {
    private val prefs = ProfileScopedPrefs.wrongQuestion(context)

    /** Record a wrong answer. */
    fun recordWrong(questionId: String, topic: String) {
        val key = "wrong_$questionId"
        val json = prefs.getString(key, null) ?: run {
            JSONObject().apply {
                put("topic", topic)
                put("wrongCount", 0)
                put("fixed", false)
                put("lastWrongAt", 0L)
            }.toString()
        }
        val o = JSONObject(json)
        val wrongCount = o.optInt("wrongCount", 0) + 1
        o.put("wrongCount", wrongCount.coerceAtMost(MAX_CAP))
        o.put("topic", topic)
        o.put("lastWrongAt", System.currentTimeMillis())
        o.put("fixed", false)
        prefs.edit().putString(key, o.toString()).apply()
    }

    /** Mark question as FIXED when answered correctly later. */
    fun markFixed(questionId: String) {
        val key = "wrong_$questionId"
        val json = prefs.getString(key, null) ?: return
        try {
            val o = JSONObject(json)
            o.put("fixed", true)
            o.put("fixedAt", System.currentTimeMillis())
            prefs.edit().putString(key, o.toString()).apply()
        } catch (_: Exception) { }
    }

    /** Wrong question IDs that are not yet fixed (for reappearance in tests). */
    fun getUnfixedWrongIds(withinDays: Int = 30): Set<String> {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(withinDays.toLong())
        return prefs.all.keys
            .filter { it.startsWith("wrong_") }
            .mapNotNull { key ->
                val id = key.removePrefix("wrong_")
                val o = prefs.getString(key, null)?.let { JSONObject(it) } ?: return@mapNotNull null
                if (o.optBoolean("fixed", false)) return@mapNotNull null
                if (o.optLong("lastWrongAt", 0) < cutoff) return@mapNotNull null
                id
            }
            .toSet()
    }

    /** All wrong IDs (fixed + unfixed) for weighting. Higher probability for unfixed. */
    fun getAllWrongIds(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith("wrong_") }
            .map { it.removePrefix("wrong_") }
            .toSet()
    }

    /** Parent stats: Wrong → Fixed by topic. e.g. "Matematik: 12 yanlış → 9 düzeltildi" */
    fun getImprovementByTopic(): Map<String, TopicImprovement> {
        val byTopic = mutableMapOf<String, MutableList<WrongEntry>>()
        prefs.all.keys
            .filter { it.startsWith("wrong_") }
            .forEach { key ->
                val json = prefs.getString(key, null) ?: return@forEach
                try {
                    val o = JSONObject(json)
                    val topic = o.optString("topic", "Diğer").takeIf { it.isNotBlank() } ?: "Diğer"
                    byTopic.getOrPut(topic) { mutableListOf() }.add(
                        WrongEntry(
                            wrongCount = o.optInt("wrongCount", 1),
                            fixed = o.optBoolean("fixed", false)
                        )
                    )
                } catch (_: Exception) { }
            }
        return byTopic.mapValues { (_, entries) ->
            val totalWrong = entries.sumOf { it.wrongCount }
            val fixedCount = entries.count { it.fixed }
            TopicImprovement(totalWrong = totalWrong, fixedCount = fixedCount)
        }
    }

    /** Total wrong→fixed count (all topics). */
    fun getTotalWrongCount(): Int = getImprovementByTopic().values.sumOf { it.totalWrong }
    fun getTotalFixedCount(): Int = getImprovementByTopic().values.sumOf { it.fixedCount }

    data class TopicImprovement(val totalWrong: Int, val fixedCount: Int)
    private data class WrongEntry(val wrongCount: Int, val fixed: Boolean)

    companion object {
        private const val MAX_CAP = 1000
    }
}
