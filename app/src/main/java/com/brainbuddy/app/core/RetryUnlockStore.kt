package com.brainbuddy.app.core

import android.content.Context
import java.util.UUID

/**
 * One-shot unlock token for fail retry flow.
 * Used after ad or cooldown to prevent intent bypass.
 * QuizActivity consumes token on entry.
 */
class RetryUnlockStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun createRetryToken(quizId: String, questionIds: List<String>): String {
        val token = UUID.randomUUID().toString()
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_QUIZ_ID, quizId)
            .putString(KEY_QUESTION_IDS, questionIds.joinToString(","))
            .apply()
        return token
    }

    /** Returns questionIds if token valid for quizId, then consumes token. */
    fun consumeRetryToken(token: String, quizId: String): List<String>? {
        val storedToken = prefs.getString(KEY_TOKEN, null) ?: return null
        val storedQuizId = prefs.getString(KEY_QUIZ_ID, null) ?: return null
        if (storedToken != token || storedQuizId != quizId) return null
        val idsStr = prefs.getString(KEY_QUESTION_IDS, "") ?: ""
        prefs.edit().remove(KEY_TOKEN).remove(KEY_QUIZ_ID).remove(KEY_QUESTION_IDS).apply()
        return idsStr.split(",").filter { it.isNotBlank() }
    }

    companion object {
        private const val PREFS = "bb_retry_unlock"
        private const val KEY_TOKEN = "token"
        private const val KEY_QUIZ_ID = "quiz_id"
        private const val KEY_QUESTION_IDS = "question_ids"
    }
}
