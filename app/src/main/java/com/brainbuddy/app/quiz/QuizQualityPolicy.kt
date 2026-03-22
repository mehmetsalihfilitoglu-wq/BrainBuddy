package com.brainbuddy.app.quiz

/**
 * Production adaptive quality: prefer HARD → MEDIUM → BORDERLINE, then EASY only as emergency (capped).
 * No rows are deleted; serving is tier-aware at runtime.
 */
object QuizQualityPolicy {

    const val TIER_HARD: String = "HARD"
    const val TIER_MEDIUM: String = "MEDIUM"
    const val TIER_BORDERLINE: String = "BORDERLINE"
    const val TIER_EASY: String = "EASY"

    /** Primary serving order (EASY excluded until emergency). */
    val SERVING_TIER_PRIORITY: List<String> = listOf(TIER_HARD, TIER_MEDIUM, TIER_BORDERLINE)

    /** All tiers that may appear in DB / classifier. */
    val ALL_CONTENT_TIERS: List<String> = listOf(TIER_HARD, TIER_MEDIUM, TIER_BORDERLINE, TIER_EASY)

    /** Default Room candidate fetch (all tiers); [QuestionRepository] applies HARD→MEDIUM→BORDERLINE→EASY rules. */
    val PLAYABLE_TIERS_PRIMARY: List<String> = ALL_CONTENT_TIERS

    /** Max share of EASY items in a single quiz when emergency fallback is required (e.g. 20% of 20 = 4). */
    const val EMERGENCY_EASY_MAX_FRACTION: Double = 0.20

    /** Legacy 0..100 scores kept for analytics; tier comes from [reasoningLevel] 0..3. */
    const val LEGACY_REASONING_STRONG: Int = 68
    const val LEGACY_REASONING_OK: Int = 45
    const val LEGACY_REASONING_WEAK: Int = 20

    /** Soft floor for distractor heuristic warnings (not a DB filter). */
    const val DISTRACTOR_SOFT_FLOOR: Int = 40
}
