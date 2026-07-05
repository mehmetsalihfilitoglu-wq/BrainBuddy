package com.mioacademy.app.core

import android.content.Context

/** Quiz mode: GRADE = grade levels 1-7, LGS = LGS exam pool only. */
enum class LevelMode {
    GRADE,
    LGS
}

/**
 * Level/mode and grade selection - profile-scoped persistence.
 * - selectedMode: GRADE | LGS
 * - selectedGrade: -1 = none, 0 = Junior, 1..7 = grade (only meaningful when mode == GRADE)
 */
class GradePrefs(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun profileKey(base: String): String {
        val profileId = ActiveProfileManager.getActiveProfileId(context)
        return "profile_${profileId}_$base"
    }

    /** Selected mode. Default GRADE for existing users. */
    fun getSelectedMode(): LevelMode {
        val raw = prefs.getString(profileKey(KEY_SELECTED_MODE), null)
        return when (raw) {
            LevelMode.LGS.name -> LevelMode.LGS
            else -> LevelMode.GRADE
        }
    }

    fun setSelectedMode(mode: LevelMode) {
        prefs.edit().putString(profileKey(KEY_SELECTED_MODE), mode.name).apply()
    }

    /** -1 = seçilmemiş, 0 = Junior, 1..7 = sınıf. Only used when mode == GRADE. */
    fun getSelectedGrade(): Int {
        val raw = prefs.getInt(profileKey(KEY_SELECTED_GRADE), -1)
        return when {
            raw in 1..7 -> raw
            raw == 0 -> 0  // Junior
            else -> 6
        }
    }

    fun setSelectedGrade(grade: Int) {
        val v = when {
            grade in 0..7 -> grade
            else -> -1
        }
        prefs.edit().putInt(profileKey(KEY_SELECTED_GRADE), v).apply()
    }

    /** Grade seçilmiş mi (Junior=0 veya 1-7) */
    fun hasGradeSelected(): Boolean = getSelectedGrade() in 0..7

    /** Quiz can start when (mode==GRADE && hasGrade) || (mode==LGS) */
    fun hasLevelSelected(): Boolean =
        when (getSelectedMode()) {
            LevelMode.GRADE -> hasGradeSelected()
            LevelMode.LGS -> true
        }

    /** Quiz için efektif sınıf: Junior(0) -> 1, aksi halde seçili sınıf. */
    fun getEffectiveGradeForQuiz(): Int {
        val g = getSelectedGrade()
        return if (g in 1..7) g else 1
    }

    companion object {
        /** Junior = okul öncesi / 1. sınıf okuma-yazma seviyesi */
        const val GRADE_JUNIOR = 0
        private const val PREFS = "bb_grade_prefs"
        private const val KEY_SELECTED_MODE = "selected_mode"
        private const val KEY_SELECTED_GRADE = "selected_grade"
    }
}
