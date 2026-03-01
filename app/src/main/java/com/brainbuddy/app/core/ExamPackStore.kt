package com.brainbuddy.app.core

import android.content.Context
import com.brainbuddy.app.quiz.ExamType

/**
 * Parent-selectable exam packs. Quiz generator uses only selected packs.
 * Supports LGS, TYT, AYT. JSON/CSV import can tag questions with examType.
 */
class ExamPackStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Active exam packs. If empty, use all questions (backward compatible). */
    fun getActiveExamTypes(): Set<ExamType> {
        val set = prefs.getStringSet(KEY_ACTIVE, null) ?: return emptySet()
        return set.mapNotNull { s ->
            try { ExamType.valueOf(s) } catch (_: Exception) { null }
        }.toSet()
    }

    fun setActiveExamTypes(types: Set<ExamType>) {
        prefs.edit().putStringSet(KEY_ACTIVE, types.map { it.name }.toSet()).apply()
    }

    fun isPackActive(examType: ExamType): Boolean {
        val active = getActiveExamTypes()
        return active.isEmpty() || examType in active || ExamType.GENERAL in active
    }

    companion object {
        private const val PREFS = "bb_exam_packs"
        private const val KEY_ACTIVE = "active_exam_types"
    }
}
