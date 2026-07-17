package com.edumio.app.core

import android.content.Context

/**
 * Retry policy after a gate/test FAIL (stored same-test token).
 * Ad-free business model:
 * - Premium: unlimited immediate retry.
 * - Free: one retry after a short cooldown (no ads, no "watch to unlock").
 */
class QuizRetryPolicy(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val premiumStore = PremiumStore(context)

    enum class StartMode {
        /** Can start retry immediately (premium, or cooldown elapsed). */
        ALLOW_FREE,
        /** Must wait out the cooldown. */
        WAIT_COOLDOWN
    }

    fun getStartMode(): StartMode {
        if (premiumStore.isPremium()) return StartMode.ALLOW_FREE
        if (!hasStoredFail()) return StartMode.ALLOW_FREE
        val cooldownEnd = prefs.getLong(KEY_COOLDOWN_END_MS, 0L)
        return if (System.currentTimeMillis() >= cooldownEnd) StartMode.ALLOW_FREE else StartMode.WAIT_COOLDOWN
    }

    fun getCooldownEndMs(): Long = prefs.getLong(KEY_COOLDOWN_END_MS, 0L)

    /** Call when user fails (wrongCount >= 4). Stores the same-test token + starts cooldown. */
    fun onFail(ctx: Context, sameTestToken: SameTestToken) {
        prefs.edit()
            .putString(KEY_SAME_TEST_TOKEN, sameTestToken.toJson())
            .putLong(KEY_COOLDOWN_END_MS, System.currentTimeMillis() + COOLDOWN_MS)
            .apply()
    }

    fun getSameTestToken(): SameTestToken? {
        val json = prefs.getString(KEY_SAME_TEST_TOKEN, null) ?: return null
        return SameTestToken.fromJson(json)
    }

    /** Call when user passes. Reset all. */
    fun onPass() {
        prefs.edit()
            .remove(KEY_SAME_TEST_TOKEN)
            .putLong(KEY_COOLDOWN_END_MS, 0L)
            .apply()
    }

    private fun hasStoredFail(): Boolean = prefs.contains(KEY_SAME_TEST_TOKEN)

    data class SameTestToken(
        val quizId: String,
        val questionIds: List<String>
    ) {
        fun toJson(): String = org.json.JSONObject().apply {
            put("quizId", quizId)
            put("questionIds", org.json.JSONArray(questionIds))
        }.toString()

        companion object {
            fun fromJson(json: String?): SameTestToken? {
                if (json.isNullOrBlank()) return null
                return try {
                    val o = org.json.JSONObject(json)
                    val arr = o.getJSONArray("questionIds")
                    val ids = (0 until arr.length()).map { arr.getString(it) }
                    SameTestToken(o.getString("quizId"), ids)
                } catch (_: Exception) { null }
            }
        }
    }

    companion object {
        private const val PREFS = "edu_quiz_retry_policy"
        private const val KEY_SAME_TEST_TOKEN = "same_test_token"
        private const val KEY_COOLDOWN_END_MS = "cooldown_end_ms"
        const val COOLDOWN_MS = 30 * 60 * 1000L
    }
}
