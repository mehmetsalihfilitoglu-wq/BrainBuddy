package com.brainbuddy.app.core

import android.content.Context

/**
 * Retry policy after gate/test FAIL (stored same-test token).
 * - Premium: unlimited retry, no ad
 * - Non-premium: 3 ad tickets, then 30min cooldown; after cooldown 1 free retry
 */
class QuizRetryPolicy(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val premiumStore = PremiumStore(context)
    private val protectionPrefs = ProtectionPrefs(context)

    enum class StartMode {
        /** Can start retry immediately (no ad, no cooldown) */
        ALLOW_FREE,
        /** Must watch ad first */
        REQUIRE_AD,
        /** Must wait cooldown */
        WAIT_COOLDOWN
    }

    fun getStartMode(): StartMode {
        if (premiumStore.isPremium()) return StartMode.ALLOW_FREE
        if (!hasStoredFail() && !protectionPrefs.userLocked()) return StartMode.ALLOW_FREE

        val tickets = prefs.getInt(KEY_AD_TICKETS, MAX_AD_TICKETS)
        if (tickets > 0) return StartMode.REQUIRE_AD

        val cooldownEnd = prefs.getLong(KEY_COOLDOWN_END_MS, 0L)
        if (System.currentTimeMillis() >= cooldownEnd) return StartMode.ALLOW_FREE
        return StartMode.WAIT_COOLDOWN
    }

    fun getRemainingTickets(): Int = prefs.getInt(KEY_AD_TICKETS, MAX_AD_TICKETS).coerceIn(0, MAX_AD_TICKETS)
    fun getCooldownEndMs(): Long = prefs.getLong(KEY_COOLDOWN_END_MS, 0L)

    /** Call when user fails (wrongCount >= 4). Stores sameTestToken for replay. */
    fun onFail(ctx: Context, sameTestToken: SameTestToken) {
        val tokenJson = sameTestToken.toJson()
        prefs.edit()
            .putString(KEY_SAME_TEST_TOKEN, tokenJson)
            .putInt(KEY_AD_TICKETS, MAX_AD_TICKETS)
            .apply()
    }

    fun getSameTestToken(): SameTestToken? {
        val json = prefs.getString(KEY_SAME_TEST_TOKEN, null) ?: return null
        return SameTestToken.fromJson(json)
    }

    /** Call after rewarded ad. Returns true if ticket consumed. */
    fun consumeAdTicket(): Boolean {
        if (premiumStore.isPremium()) return true
        val tickets = prefs.getInt(KEY_AD_TICKETS, MAX_AD_TICKETS)
        if (tickets <= 0) return false
        val newTickets = tickets - 1
        val cooldownEnd = if (newTickets == 0) System.currentTimeMillis() + COOLDOWN_MS else 0L
        prefs.edit()
            .putInt(KEY_AD_TICKETS, newTickets)
            .putLong(KEY_COOLDOWN_END_MS, cooldownEnd)
            .apply()
        return true
    }

    /** Call when user passes. Reset all. */
    fun onPass() {
        prefs.edit()
            .remove(KEY_SAME_TEST_TOKEN)
            .putInt(KEY_AD_TICKETS, MAX_AD_TICKETS)
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
        private const val PREFS = "bb_quiz_retry_policy"
        private const val KEY_SAME_TEST_TOKEN = "same_test_token"
        private const val KEY_AD_TICKETS = "ad_tickets"
        private const val KEY_COOLDOWN_END_MS = "cooldown_end_ms"
        const val MAX_AD_TICKETS = 3
        const val COOLDOWN_MS = 30 * 60 * 1000L
    }
}
