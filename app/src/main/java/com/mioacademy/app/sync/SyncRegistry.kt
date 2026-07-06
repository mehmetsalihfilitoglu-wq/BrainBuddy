package com.mioacademy.app.sync

/**
 * Declares every store that participates in cloud sync. Each learning object lives
 * in a SharedPreferences file named "<baseName>" (GLOBAL) or "<baseName>_<areaId>"
 * (STUDY_AREA), so sync is a generic per-store snapshot and study-area isolation is
 * preserved *by construction* — an area's data can only ever land in that area's file.
 *
 * Base names mirror [com.mioacademy.app.core.ProfileScopedPrefs] exactly. Derived
 * surfaces (achievements, learning journey, insights, readiness) need no entry: they
 * are computed from these underlying stores, so syncing the stores syncs them too.
 *
 * Deliberately excluded: auth credentials (device-local), premium entitlement
 * (server-verified, not user data), and the sync bookkeeping itself.
 */
object SyncRegistry {

    /** Per-study-area learning data — isolated by areaId. */
    val areaStores: List<StoreSpec> = listOf(
        StoreSpec("analytics", "bb_analytics"),          // Progress + Analytics
        StoreSpec("gamification", "bb_gamification"),    // XP, streak, badges (Achievements)
        StoreSpec("wrong_scheduler", "bb_wrong_scheduler"), // Mastery ladder + review schedule
        StoreSpec("wrong_pool", "bb_wrong_question_pool"),  // Wrong-question pool
        StoreSpec("wrong_store", "bb_wrong_question_store"),
        StoreSpec("quiz_prefs", "bb_quiz_prefs"),        // Daily missions
        StoreSpec("student_profile", "bb_student_profile"),
        StoreSpec("avatar", "bb_avatar"),
        StoreSpec("weekly_reward", "bb_weekly_reward"),
        StoreSpec("reports", "bb_reports")
    )

    /** Account-level data shared across the whole account. */
    val globalStores: List<StoreSpec> = listOf(
        StoreSpec("profiles", "bb_profiles"),               // the study-area list itself
        StoreSpec("notifications", "bb_notification_prefs") // notification settings
    )

    private val byKey: Map<String, StoreSpec> =
        (areaStores.map { it.storeKey to it } + globalStores.map { it.storeKey to it }).toMap()

    fun specFor(storeKey: String): StoreSpec? = byKey[storeKey]
}

/** A store that participates in sync. [scope] is inferred by which list it's declared in. */
data class StoreSpec(
    val storeKey: String,
    val baseName: String,
    val schemaVersion: Int = 1
)
