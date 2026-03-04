package com.brainbuddy.app.core

import android.content.Context

/**
 * Sınıf seçimi (2-8) - userId/profil bazlı kalıcı kayıt.
 * DataStore benzeri kalıcılık: SharedPreferences ile persist edilir.
 * Uygulama kapanıp açılsa bile aynı kalır. Çoklu hesap varsa her hesap için ayrı.
 */
class GradePrefs(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun profileKey(): String {
        val profileId = ActiveProfileManager.getActiveProfileId(context)
        return "profile_${profileId}_$KEY_SELECTED_GRADE"
    }

    /** 0 = seçilmemiş, 2..8 = sınıf. */
    fun getSelectedGrade(): Int {
        return prefs.getInt(profileKey(), 0).coerceIn(0, 8)
    }

    fun setSelectedGrade(grade: Int) {
        val v = when {
            grade in 2..8 -> grade
            else -> 0
        }
        prefs.edit().putInt(profileKey(), v).apply()
    }

    /** Grade seçilmiş mi (2-8 aralığında) */
    fun hasGradeSelected(): Boolean = getSelectedGrade() in 2..8

    companion object {
        private const val PREFS = "bb_grade_prefs"
        private const val KEY_SELECTED_GRADE = "selected_grade"
    }
}
