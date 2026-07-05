package com.mioacademy.app.social

import android.content.Context

enum class ReactionType(val emoji: String) {
    LIKE("👍"),
    BRAVO("👏"),
    CONGRATS("🎉")
}

/** Stores reaction counts per targetUserId per week. No messaging. */
class ReactionStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun weekKey(): String = (java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis()) / 7).toString()

    fun getReactionCounts(targetUserId: String): Map<ReactionType, Int> {
        val key = "reactions_${weekKey()}_$targetUserId"
        val json = prefs.getString(key, "{}") ?: "{}"
        return try {
            val o = org.json.JSONObject(json)
            ReactionType.entries.associateWith { t ->
                o.optInt(t.name, 0)
            }
        } catch (_: Exception) {
            ReactionType.entries.associateWith { 0 }
        }
    }

    fun addReaction(fromUserId: String, targetUserId: String, type: ReactionType) {
        val key = "reactions_${weekKey()}_$targetUserId"
        val json = prefs.getString(key, "{}") ?: "{}"
        val o = try { org.json.JSONObject(json) } catch (_: Exception) { org.json.JSONObject() }
        o.put(type.name, o.optInt(type.name, 0) + 1)
        prefs.edit().putString(key, o.toString()).apply()
    }

    companion object {
        private const val PREFS = "bb_reactions"
    }
}
