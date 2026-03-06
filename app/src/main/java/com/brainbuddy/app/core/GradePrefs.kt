package com.brainbuddy.app.core

import android.content.Context

/** Quiz mode: GRADE = grade levels 1-7, LGS = LGS exam pool only. */
enum class LevelMode {
    GRADE,
    LGS
}

/**
 * Level/mode and grade selection - profile-scoped persistence.
 * - selectedMode: GRADE | LGS
 * - selectedGrade: 1..7 (only meaningful when mode == GRADE)
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

    /** 0 = seçilmemiş, 1..7 = sınıf. Only used when mode == GRADE. */
    fun getSelectedGrade(): Int {
        return prefs.getInt(profileKey(KEY_SELECTED_GRADE), 0).coerceIn(0, 7)
    }

    fun setSelectedGrade(grade: Int) {
        val v = when {
            grade in 1..7 -> grade
            else -> 0
        }
        prefs.edit().putInt(profileKey(KEY_SELECTED_GRADE), v).apply()
    }

    /** Grade seçilmiş mi (1-7 aralığında) */
    fun hasGradeSelected(): Boolean = getSelectedGrade() in 1..7

    /** Quiz can start when (mode==GRADE && hasGrade) || (mode==LGS) */
    fun hasLevelSelected(): Boolean =
        when (getSelectedMode()) {
            LevelMode.GRADE -> hasGradeSelected()
            LevelMode.LGS -> true
        }

    companion object {
        private const val PREFS = "bb_grade_prefs"
        private const val KEY_SELECTED_MODE = "selected_mode"
        private const val KEY_SELECTED_GRADE = "selected_grade"
    }
}
