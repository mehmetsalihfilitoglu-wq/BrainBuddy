package com.brainbuddy.app.core

import android.content.Context

class ProtectionPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isProtectionEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setProtectionEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /**
     * Cooldown sabit: 10 dakika.
     * UI’da gösterilir ama kullanıcı değiştiremez.
     */
    fun quizIntervalMinutes(): Int = 10

    fun studentLevel(): StudentLevel =
        StudentLevel.valueOf(prefs.getString(KEY_LEVEL, StudentLevel.AGE_3_5.name)!!)

    fun setStudentLevel(level: StudentLevel) =
        prefs.edit().putString(KEY_LEVEL, level.name).apply()

    fun lastQuizPassedAtMs(): Long = prefs.getLong(KEY_LAST_QUIZ_PASSED_AT, 0L)
    fun setLastQuizPassedAtMs(v: Long) = prefs.edit().putLong(KEY_LAST_QUIZ_PASSED_AT, v).apply()

    fun isQuizInProgress(): Boolean = prefs.getBoolean(KEY_QUIZ_IN_PROGRESS, false)
    fun setQuizInProgress(v: Boolean) = prefs.edit().putBoolean(KEY_QUIZ_IN_PROGRESS, v).apply()

    companion object {
        private const val PREFS = "bb_protection_prefs"
        private const val KEY_ENABLED = "protection_enabled"
        private const val KEY_LEVEL = "student_level"
        private const val KEY_LAST_QUIZ_PASSED_AT = "last_quiz_passed_at_ms"
        private const val KEY_QUIZ_IN_PROGRESS = "quiz_in_progress"
    }
}

enum class StudentLevel {
    AGE_3_5,
    GRADES_1_4,
    GRADES_5_8,
    GRADES_9_12
}