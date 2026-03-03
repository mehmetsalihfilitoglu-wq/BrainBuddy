package com.brainbuddy.app.core

import android.content.Context

class ProtectionPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun profileKey(base: String): String {
        val profileId = ActiveProfileManager.getActiveProfileId(context)
        return "profile_${profileId}_$base"
    }

    fun isProtectionEnabled(): Boolean {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return false
        val killSwitch = KillSwitchPrefs(context)
        return !killSwitch.isKillSwitchActive()
    }
    fun isProtectionEnabledRaw(): Boolean = prefs.getBoolean(KEY_ENABLED, false)
    fun setProtectionEnabled(v: Boolean) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** Quiz gate cooldown (30, 45, or 60 min).
     * UI’da gösterilir ama kullanıcı değiştiremez.
     */
    fun quizIntervalMinutes(): Int = prefs.getInt(KEY_QUIZ_INTERVAL, 30).coerceIn(30, 60)
    fun setQuizIntervalMinutes(v: Int) = prefs.edit().putInt(KEY_QUIZ_INTERVAL, v.coerceIn(30, 60)).apply()

    /** Minimum success rate (correct / (correct + wrong)) to unlock apps. Default 60%, range 50–80%. */
    fun minSuccessRatePercent(): Int = prefs.getInt(KEY_MIN_SUCCESS_RATE, 60).coerceIn(50, 80)
    fun setMinSuccessRatePercent(v: Int) = prefs.edit().putInt(KEY_MIN_SUCCESS_RATE, v.coerceIn(50, 80)).apply()

    fun studentLevel(): StudentLevel {
        val scopedKey = profileKey(KEY_LEVEL)
        val rawProfile = prefs.getString(scopedKey, null)
        val raw = when {
            rawProfile != null -> rawProfile
            else -> {
                val legacy = prefs.getString(KEY_LEVEL, StudentLevel.AGE_3_5.name) ?: StudentLevel.AGE_3_5.name
                // Migrate legacy global value into profile-scoped key for active profile.
                prefs.edit().putString(scopedKey, legacy).apply()
                legacy
            }
        }
        return try {
            StudentLevel.valueOf(raw)
        } catch (_: Exception) {
            StudentLevel.AGE_3_5
        }
    }

    fun setStudentLevel(level: StudentLevel) {
        val scopedKey = profileKey(KEY_LEVEL)
        prefs.edit().putString(scopedKey, level.name).apply()
    }

    fun lastQuizPassedAtMs(): Long {
        val scopedKey = profileKey(KEY_LAST_QUIZ_PASSED_AT)
        if (!prefs.contains(scopedKey) && prefs.contains(KEY_LAST_QUIZ_PASSED_AT)) {
            val v = prefs.getLong(KEY_LAST_QUIZ_PASSED_AT, 0L).coerceAtLeast(0L)
            prefs.edit().putLong(scopedKey, v).apply()
        }
        return prefs.getLong(scopedKey, 0L).coerceAtLeast(0L)
    }

    fun setLastQuizPassedAtMs(v: Long) {
        val scopedKey = profileKey(KEY_LAST_QUIZ_PASSED_AT)
        prefs.edit().putLong(scopedKey, v).apply()
    }

    fun isQuizInProgress(): Boolean {
        val scopedKey = profileKey(KEY_QUIZ_IN_PROGRESS)
        if (!prefs.contains(scopedKey) && prefs.contains(KEY_QUIZ_IN_PROGRESS)) {
            val v = prefs.getBoolean(KEY_QUIZ_IN_PROGRESS, false)
            prefs.edit().putBoolean(scopedKey, v).apply()
        }
        return prefs.getBoolean(scopedKey, false)
    }

    fun setQuizInProgress(v: Boolean) {
        val scopedKey = profileKey(KEY_QUIZ_IN_PROGRESS)
        prefs.edit().putBoolean(scopedKey, v).apply()
    }

    fun userLocked(): Boolean {
        val scopedKey = profileKey(KEY_USER_LOCKED)
        if (!prefs.contains(scopedKey) && prefs.contains(KEY_USER_LOCKED)) {
            val v = prefs.getBoolean(KEY_USER_LOCKED, false)
            prefs.edit().putBoolean(scopedKey, v).apply()
        }
        return prefs.getBoolean(scopedKey, false)
    }

    fun setUserLocked(v: Boolean) {
        val scopedKey = profileKey(KEY_USER_LOCKED)
        prefs.edit().putBoolean(scopedKey, v).apply()
    }

    fun lastFailedWrongIds(): List<String> {
        val scopedKey = profileKey(KEY_LAST_FAILED_WRONG_IDS)
        val fromProfile = prefs.getStringSet(scopedKey, null)
        val source = fromProfile ?: prefs.getStringSet(KEY_LAST_FAILED_WRONG_IDS, emptySet())
        val list = source
            ?.filter { it.isNotBlank() && it.length <= 200 }
            ?.take(500)
            ?: emptyList()
        if (fromProfile == null && list.isNotEmpty()) {
            prefs.edit().putStringSet(scopedKey, list.toSet()).apply()
        }
        return list
    }

    fun setLastFailedWrongIds(ids: List<String>) {
        val scopedKey = profileKey(KEY_LAST_FAILED_WRONG_IDS)
        prefs.edit().putStringSet(scopedKey, ids.take(500).toSet()).apply()
    }

    fun lastFailedQuizId(): String {
        val scopedKey = profileKey(KEY_LAST_FAILED_QUIZ_ID)
        val fromProfile = prefs.getString(scopedKey, null)
        val value = fromProfile ?: prefs.getString(KEY_LAST_FAILED_QUIZ_ID, "") ?: ""
        if (fromProfile == null && value.isNotEmpty()) {
            prefs.edit().putString(scopedKey, value).apply()
        }
        return value
    }

    fun setLastFailedQuizId(id: String) {
        val scopedKey = profileKey(KEY_LAST_FAILED_QUIZ_ID)
        prefs.edit().putString(scopedKey, id).apply()
    }

    fun lastFailedQuestionIds(): List<String> {
        val scopedKey = profileKey(KEY_LAST_FAILED_QUESTION_IDS)
        val fromProfile = prefs.getStringSet(scopedKey, null)
        val source = fromProfile ?: prefs.getStringSet(KEY_LAST_FAILED_QUESTION_IDS, emptySet())
        val list = source?.toList() ?: emptyList()
        if (fromProfile == null && list.isNotEmpty()) {
            prefs.edit().putStringSet(scopedKey, list.toSet()).apply()
        }
        return list
    }

    fun setLastFailedQuestionIds(ids: List<String>) {
        val scopedKey = profileKey(KEY_LAST_FAILED_QUESTION_IDS)
        prefs.edit().putStringSet(scopedKey, ids.toSet()).apply()
    }

    fun lastFailedSessionJson(): String {
        val scopedKey = profileKey(KEY_LAST_FAILED_SESSION_JSON)
        val fromProfile = prefs.getString(scopedKey, null)
        val value = fromProfile ?: prefs.getString(KEY_LAST_FAILED_SESSION_JSON, "") ?: ""
        if (fromProfile == null && value.isNotEmpty()) {
            prefs.edit().putString(scopedKey, value).apply()
        }
        return value
    }

    fun setLastFailedSessionJson(json: String) {
        val scopedKey = profileKey(KEY_LAST_FAILED_SESSION_JSON)
        prefs.edit().putString(scopedKey, json).apply()
    }

    fun lastFailedQuestionsJson(): String {
        val scopedKey = profileKey(KEY_LAST_FAILED_QUESTIONS_JSON)
        val fromProfile = prefs.getString(scopedKey, null)
        val value = fromProfile ?: prefs.getString(KEY_LAST_FAILED_QUESTIONS_JSON, "") ?: ""
        if (fromProfile == null && value.isNotEmpty()) {
            prefs.edit().putString(scopedKey, value).apply()
        }
        return value
    }

    fun setLastFailedQuestionsJson(json: String) {
        val scopedKey = profileKey(KEY_LAST_FAILED_QUESTIONS_JSON)
        prefs.edit().putString(scopedKey, json.take(500000)).apply()
    }

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
        private const val KEY_LAST_FAILED_QUESTIONS_JSON = "last_failed_questions_json"
        private const val KEY_QUIZ_INTERVAL = "quiz_interval_minutes"
        private const val KEY_MIN_SUCCESS_RATE = "min_success_rate_percent"
        private const val KEY_PERMISSION_LOCK_REASON = "permission_lock_reason"
    }
}

enum class StudentLevel {
    AGE_3_5,
    GRADES_1_4,
    GRADES_5_8,
    GRADES_9_12
}