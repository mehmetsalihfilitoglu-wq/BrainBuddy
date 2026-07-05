package com.mioacademy.app.quiz

/**
 * Cross-quiz repeat prevention policy.
 *
 * Holds exclusion snapshots consumed by [QuizOutputGuard.enforce]
 * to block recently-seen questions by ID or content fingerprint.
 */
object RepeatPolicy {

    /**
     * Immutable snapshot of IDs and fingerprints to exclude from the next quiz.
     * Built by the caller (repository / picker) from recent test history.
     */
    data class ExclusionSnapshot(
        /** Question IDs seen in recent tests. */
        val excludedIds: Set<String> = emptySet(),
        /** Content fingerprints seen in recent tests. */
        val excludedFingerprints: Set<String> = emptySet(),
    )
}
