package com.brainbuddy.app.quiz

/**
 * Runtime policy for which content `qualityTier` values may be served in quizzes.
 * EASY is excluded from the primary pool unless [ALLOW_EASY_FALLBACK] is enabled (debug only).
 */
object QuizQualityPolicy {

    /** Last-resort only; keep false in production for medium-hard pools. */
    const val ALLOW_EASY_FALLBACK: Boolean = false

    val PLAYABLE_TIERS_PRIMARY: List<String> = listOf("MEDIUM", "HARD")

    val PLAYABLE_TIERS_WITH_EASY_FALLBACK: List<String> = listOf("EASY", "MEDIUM", "HARD")
}
