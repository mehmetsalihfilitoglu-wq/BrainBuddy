package com.edumio.app.core

import android.content.Context
import android.content.SharedPreferences

/**
 * Helper for per-profile SharedPreferences with one-time migration
 * from legacy global preference files.
 *
 * Convention:
 * - Legacy global file: e.g. "bb_analytics"
 * - Per-profile file:  "bb_analytics_<profileId>"
 *
 * Each accessor below:
 * - Resolves the active profile id via ActiveProfileManager.
 * - Ensures legacy data (if any) is copied once into the active profile's file.
 */
object ProfileScopedPrefs {

    private const val MIGRATION_PREFS = "bb_profile_scoped_migration"

    private fun activeProfileId(context: Context): String =
        ActiveProfileManager.getActiveProfileId(context)

    // === Public factories for profile-scoped stores ===

    fun analytics(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_analytics", migrationKey = "analytics_v1")

    fun gamification(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_gamification", migrationKey = "gamification_v1")

    fun wrongQuestion(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_wrong_question_store", migrationKey = "wrong_question_v1")

    /** Dedicated wrong-question pool (Yanlışlarını Çöz) — separate from test generation. */
    fun wrongQuestionPool(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_wrong_question_pool", migrationKey = "wrong_question_pool_v1")

    /** Wrong-question scheduler: spacing by completed tests (wrongPool + completedTests). */
    fun wrongScheduler(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_wrong_scheduler", migrationKey = "wrong_scheduler_v1")

    fun league(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_league", migrationKey = "league_v1")

    fun reports(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_reports", migrationKey = "reports_v1")

    /** Exam-readiness snapshots over time (for readiness-change trends + reports). */
    fun readinessSnapshots(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_readiness_snapshots", migrationKey = "readiness_snapshots_v1")

    fun blockedApps(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_blocked_apps", migrationKey = "blocked_apps_v1")

    fun timeLimits(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_time_limits", migrationKey = "time_limits_v1")

    fun schedules(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_schedules", migrationKey = "schedules_v1")

    fun examPacks(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_exam_packs", migrationKey = "exam_packs_v1")

    fun avatar(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_avatar", migrationKey = "avatar_v1")

    fun studentProfile(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_student_profile", migrationKey = "student_profile_v1")

    fun quizPrefs(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_quiz_prefs", migrationKey = "quiz_prefs_v1")

    fun weeklyReward(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_weekly_reward", migrationKey = "weekly_reward_v1")

    fun rewardContracts(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_reward_contract", migrationKey = "reward_contract_v1")

    fun gateLockedPackages(context: Context): SharedPreferences =
        profilePrefsWithMigration(context, baseName = "bb_gate_locked_packages", migrationKey = "gate_locked_v1")

    // === Core helper ===

    private fun profilePrefsWithMigration(
        context: Context,
        baseName: String,
        migrationKey: String
    ): SharedPreferences {
        val profileId = activeProfileId(context)
        val perProfileName = "${baseName}_$profileId"
        maybeMigrateGlobalToProfile(context, legacyName = baseName, profileName = perProfileName, migrationKey = migrationKey)
        return context.getSharedPreferences(perProfileName, Context.MODE_PRIVATE)
    }

    private fun maybeMigrateGlobalToProfile(
        context: Context,
        legacyName: String,
        profileName: String,
        migrationKey: String
    ) {
        val migrationPrefs = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (migrationPrefs.getBoolean(migrationKey, false)) return

        val legacy = context.getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        val legacyAll = legacy.all
        if (legacyAll.isEmpty()) {
            migrationPrefs.edit().putBoolean(migrationKey, true).apply()
            return
        }

        val profilePrefs = context.getSharedPreferences(profileName, Context.MODE_PRIVATE)
        if (profilePrefs.all.isNotEmpty()) {
            // Profile already has data; do not overwrite, just mark migrated.
            migrationPrefs.edit().putBoolean(migrationKey, true).apply()
            return
        }

        val editor = profilePrefs.edit()
        for ((key, value) in legacyAll) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val set = (value as? Set<*>)?.filterIsInstance<String>()?.toSet() ?: emptySet()
                    editor.putStringSet(key, set)
                }
            }
        }
        editor.apply()

        migrationPrefs.edit().putBoolean(migrationKey, true).apply()
    }
}

