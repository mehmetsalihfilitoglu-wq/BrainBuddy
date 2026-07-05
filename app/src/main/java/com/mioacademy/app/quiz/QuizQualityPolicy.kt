package com.mioacademy.app.quiz

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

    /**
     * STRICT QUALITY MODE: EASY emergency completely disabled.
     * A short quiz is acceptable; injecting EASY questions is not.
     */
    const val EMERGENCY_EASY_MAX_FRACTION: Double = 0.0

    /** Legacy 0..100 scores kept for analytics; tier comes from [reasoningLevel] 0..3. */
    const val LEGACY_REASONING_STRONG: Int = 68
    const val LEGACY_REASONING_OK: Int = 45
    const val LEGACY_REASONING_WEAK: Int = 20

    /** Soft floor for distractor heuristic warnings (not a DB filter). */
    const val DISTRACTOR_SOFT_FLOOR: Int = 40

    /**
     * STRICT QUALITY MODE: raised from 28 to 40.
     * Questions scoring below these thresholds are quarantined at runtime.
     * Accepts a smaller effective pool in exchange for no trivial/recall-only questions.
     */
    const val MIN_REASONING_SCORE_SERVE: Int = 40
    const val MIN_DISTRACTOR_SCORE_SERVE: Int = 40

    /** Persisted when a row is quarantined from normal serving (low quality / recall). */
    const val LOW_QUALITY_QUARANTINED: String = "LOW_QUALITY_QUARANTINED"

    /** OFF: never mix GENERAL grade banks into LGS picker or vice versa. */
    const val DEBUG_ALLOW_CROSS_MODE_FALLBACK: Boolean = false
}
