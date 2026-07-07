package com.mioacademy.app.sync

/**
 * Declares every user-owned store that participates in cloud sync. Each learning
 * object lives in a SharedPreferences file named "<baseName>" (GLOBAL) or
 * "<baseName>_<areaId>" (STUDY_AREA), so sync is a generic per-store snapshot and
 * study-area isolation is preserved *by construction* — an area's data can only ever
 * land in that area's file.
 *
 * [remotePath] is the Firestore path segment for each store (see
 * docs/BACKEND_ARCHITECTURE.md §2): GLOBAL → users/{uid}/<remotePath>,
 * STUDY_AREA → users/{uid}/areas/{areaId}/<remotePath].
 *
 * Base names mirror [com.mioacademy.app.core.ProfileScopedPrefs] / each store's PREFS.
 * Derived surfaces (heatmap, learning journey, insights, readiness *view*) need no
 * entry — they recompute from these stores, so syncing the stores syncs them too.
 *
 * Deliberately excluded: auth credentials (device-local) and the sync bookkeeping itself.
 */
object SyncRegistry {

    /** Per-study-area learning data — isolated by areaId. */
    val areaStores: List<StoreSpec> = listOf(
        StoreSpec("analytics", "bb_analytics", "analytics"),                 // sessions + past tests
        StoreSpec("gamification", "bb_gamification", "gamification"),        // XP, level, streak, achievements
        StoreSpec("wrong_scheduler", "bb_wrong_scheduler", "mastery"),      // Leitner boxes + review queue
        StoreSpec("wrong_pool", "bb_wrong_question_pool", "wrongQuestions"),
        StoreSpec("wrong_store", "bb_wrong_question_store", "wrongQuestionsStore"),
        StoreSpec("quiz_prefs", "bb_quiz_prefs", "dailyMissions"),           // daily mission state
        StoreSpec("student_profile", "bb_student_profile", "studentProfile"),
        StoreSpec("avatar", "bb_avatar", "avatar"),
        StoreSpec("weekly_reward", "bb_weekly_reward", "weeklyReward"),
        StoreSpec("reports", "bb_reports", "reports"),
        StoreSpec("readiness_snapshots", "bb_readiness_snapshots", "readinessSnapshots")
    )

    /** Account-level data shared across the whole account. */
    val globalStores: List<StoreSpec> = listOf(
        StoreSpec("profiles", "bb_profiles", "profile"),                     // study-area list + active area
        StoreSpec("settings", "user_goal_prefs", "settings"),               // goals / app settings
        StoreSpec("onboarding", "bb_onboarding_prefs", "onboarding"),
        StoreSpec("notifications", "bb_notification_prefs", "notificationPreferences"),
        StoreSpec("report_prefs", "bb_email_report_prefs", "reportPreferences"),
        StoreSpec("premium", "bb_premium", "premium"),                       // entitlement cache (server-authoritative)
        StoreSpec("subscription", "bb_subscription", "purchases"),           // subscription status (server-verified)
        StoreSpec("question_history", "bb_question_history", "questionHistory"),
        StoreSpec("favorites", "bb_favorites", "favorites"),
        StoreSpec("discovery_progress", "bb_discovery_progress", "discoveryProgress")
    )

    private val byKey: Map<String, StoreSpec> =
        (areaStores + globalStores).associateBy { it.storeKey }

    fun specFor(storeKey: String): StoreSpec? = byKey[storeKey]
}

/**
 * A store that participates in sync.
 * @param storeKey stable logical key (also the sync document key component)
 * @param baseName SharedPreferences file base name
 * @param remotePath Firestore path segment under the scope root
 */
data class StoreSpec(
    val storeKey: String,
    val baseName: String,
    val remotePath: String,
    val schemaVersion: Int = 1
)
