package com.brainbuddy.app.quiz

import android.content.Context
import com.brainbuddy.app.core.ProfileScopedPrefs

/**
 * Persistent set of question IDs the user got wrong and has not yet cleared in "Yanlışlarını Çöz" mode.
 * Independent from [WrongQuestionStore] (used for test weighting / Gelişim stats).
 */
class WrongQuestionPoolStore(context: Context) {
    private val prefs = ProfileScopedPrefs.wrongQuestionPool(context)

    fun add(questionId: String) {
        if (questionId.isBlank()) return
        val cur = prefs.getStringSet(KEY_POOL, null)?.toMutableSet() ?: mutableSetOf()
        cur.add(questionId)
        prefs.edit().putStringSet(KEY_POOL, HashSet(cur)).apply()
    }

    fun remove(questionId: String) {
        if (questionId.isBlank()) return
        val cur = prefs.getStringSet(KEY_POOL, null)?.toMutableSet() ?: return
        if (!cur.remove(questionId)) return
        prefs.edit().putStringSet(KEY_POOL, HashSet(cur)).apply()
    }

    fun getIds(): Set<String> =
        prefs.getStringSet(KEY_POOL, emptySet())?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    fun size(): Int = getIds().size

    fun isEmpty(): Boolean = getIds().isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun applyAnswer(record: AnswerRecord) {
        if (record.isCorrect) {
            remove(record.questionId)
        } else {
            add(record.questionId)
        }
    }

    companion object {
        private const val KEY_POOL = "wrong_question_pool_ids"
    }
}
