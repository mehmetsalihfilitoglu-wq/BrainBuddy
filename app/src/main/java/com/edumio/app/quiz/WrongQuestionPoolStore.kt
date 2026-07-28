package com.edumio.app.quiz

import android.content.Context
import com.edumio.app.core.ProfileScopedPrefs

/**
 * Persistent set of question IDs the user got wrong and has not yet cleared in "Yanlışlarını Çöz" mode.
 * Independent from [WrongQuestionStore] (used for test weighting / Gelişim stats).
 *
 * SharedPreferences [StringSet] must never be mutated in place; always copy to a new [HashSet] before [putStringSet].
 */
class WrongQuestionPoolStore(context: Context) {
    private val prefs = ProfileScopedPrefs.wrongQuestionPool(context)

    fun add(questionId: String) {
        if (questionId.isBlank()) return
        val cur = prefs.getStringSet(KEY_POOL, null)?.let { HashSet(it) } ?: HashSet()
        if (!cur.add(questionId)) return
        prefs.edit().putStringSet(KEY_POOL, HashSet(cur)).apply()
    }

    fun remove(questionId: String) {
        if (questionId.isBlank()) return
        val cur = prefs.getStringSet(KEY_POOL, null)?.let { HashSet(it) } ?: return
        if (!cur.remove(questionId)) return
        prefs.edit().putStringSet(KEY_POOL, HashSet(cur)).apply()
    }

    fun getIds(): Set<String> {
        val raw = prefs.getStringSet(KEY_POOL, null) ?: emptySet()
        return raw.asSequence().map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    fun size(): Int = getIds().size

    fun isEmpty(): Boolean = getIds().isEmpty()

    fun isNotEmpty(): Boolean = !isEmpty()

    fun applyAnswer(record: AnswerRecord) {
        if (record.questionId.isBlank()) return
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
