package com.brainbuddy.app.quiz

/**
 * Strict production policy: only thinking-based items are served.
 * EASY tier is never served; there is no fallback to EASY.
 *
 * Thresholds align with [QuestionDao] candidate queries (literal 40 in SQL).
 */
object QuizQualityPolicy {

    /** Served content tiers only. */
    val PLAYABLE_TIERS_PRIMARY: List<String> = listOf("MEDIUM", "HARD")

    /** 0..100 reasoning score — must be >= this to be pool-eligible (reasoning level >= 2). */
    const val MIN_REASONING_SCORE_TO_SERVE: Int = 40

    /** HARD tier requires at least this reasoning score (level >= 3 on 0..5 scale). */
    const val MIN_REASONING_SCORE_FOR_HARD_TIER: Int = 60

    /** 0..100 distractor score — must be >= this to be pool-eligible. */
    const val MIN_DISTRACTOR_SCORE_TO_SERVE: Int = 40
}
