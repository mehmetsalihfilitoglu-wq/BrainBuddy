package com.brainbuddy.app.core

import android.content.Context

class ProtectionPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isProtectionEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setProtectionEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Quiz gate cooldown (30, 45, or 60 min).
     * UI’da gösterilir ama kullanıcı değiştiremez.
     */
    fun quizIntervalMinutes(): Int = prefs.getInt(KEY_QUIZ_INTERVAL, 30).coerceIn(30, 60)
    fun setQuizIntervalMinutes(v: Int) = prefs.edit().putInt(KEY_QUIZ_INTERVAL, v.coerceIn(30, 60)).apply()

    fun studentLevel(): StudentLevel {
        val raw = prefs.getString(KEY_LEVEL, StudentLevel.AGE_3_5.name) ?: StudentLevel.AGE_3_5.name
        return try {
            StudentLevel.valueOf(raw)
        } catch (_: Exception) {
            StudentLevel.AGE_3_5
        }
    }

    fun setStudentLevel(level: StudentLevel) =
        prefs.edit().putString(KEY_LEVEL, level.name).apply()

    fun lastQuizPassedAtMs(): Long = prefs.getLong(KEY_LAST_QUIZ_PASSED_AT, 0L).coerceAtLeast(0L)
    fun setLastQuizPassedAtMs(v: Long) = prefs.edit().putLong(KEY_LAST_QUIZ_PASSED_AT, v).apply()

    fun isQuizInProgress(): Boolean = prefs.getBoolean(KEY_QUIZ_IN_PROGRESS, false)
    fun setQuizInProgress(v: Boolean) = prefs.edit().putBoolean(KEY_QUIZ_IN_PROGRESS, v).apply()

    fun userLocked(): Boolean = prefs.getBoolean(KEY_USER_LOCKED, false)
    fun setUserLocked(v: Boolean) = prefs.edit().putBoolean(KEY_USER_LOCKED, v).apply()

    fun lastFailedWrongIds(): List<String> =
        prefs.getStringSet(KEY_LAST_FAILED_WRONG_IDS, emptySet())
            ?.filter { it.isNotBlank() && it.length <= 200 }
            ?.take(500)
            ?: emptyList()
    fun setLastFailedWrongIds(ids: List<String>) =
        prefs.edit().putStringSet(KEY_LAST_FAILED_WRONG_IDS, ids.take(500).toSet()).apply()

    fun lastFailedQuizId(): String = prefs.getString(KEY_LAST_FAILED_QUIZ_ID, "") ?: ""
    fun setLastFailedQuizId(id: String) = prefs.edit().putString(KEY_LAST_FAILED_QUIZ_ID, id).apply()

    fun lastFailedQuestionIds(): List<String> =
        prefs.getStringSet(KEY_LAST_FAILED_QUESTION_IDS, emptySet())?.toList() ?: emptyList()
    fun setLastFailedQuestionIds(ids: List<String>) =
        prefs.edit().putStringSet(KEY_LAST_FAILED_QUESTION_IDS, ids.toSet()).apply()

    fun lastFailedSessionJson(): String = prefs.getString(KEY_LAST_FAILED_SESSION_JSON, "") ?: ""
    fun setLastFailedSessionJson(json: String) = prefs.edit().putString(KEY_LAST_FAILED_SESSION_JSON, json).apply()

    /** When accessibility is disabled, we lock with this reason. Only Parent PIN can fix. */
    fun permissionDisabledLockReason(): String = prefs.getString(KEY_PERMISSION_LOCK_REASON, "") ?: ""
    fun setPermissionDisabledLockReason(reason: String) = prefs.edit().putString(KEY_PERMISSION_LOCK_REASON, reason).apply()

    fun isPermissionLocked(): Boolean = permissionDisabledLockReason().isNotEmpty()

    companion object {
        private const val PREFS = "bb_protection_prefs"
        private const val KEY_ENABLED = "protection_enabled"
        private const val KEY_LEVEL = "student_level"
        private const val KEY_LAST_QUIZ_PASSED_AT = "last_quiz_passed_at_ms"
        private const val KEY_QUIZ_IN_PROGRESS = "quiz_in_progress"
        private const val KEY_USER_LOCKED = "user_locked"
        private const val KEY_LAST_FAILED_WRONG_IDS = "last_failed_wrong_ids"
        private const val KEY_LAST_FAILED_QUIZ_ID = "last_failed_quiz_id"
        private const val KEY_LAST_FAILED_QUESTION_IDS = "last_failed_question_ids"
        private const val KEY_LAST_FAILED_SESSION_JSON = "last_failed_session_json"
        private const val KEY_QUIZ_INTERVAL = "quiz_interval_minutes"
        private const val KEY_PERMISSION_LOCK_REASON = "permission_lock_reason"
    }
}

enum class StudentLevel {
    AGE_3_5,
    GRADES_1_4,
    GRADES_5_8,
    GRADES_9_12
}