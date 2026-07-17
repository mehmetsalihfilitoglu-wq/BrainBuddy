package com.edumio.app.dailychallenge

/**
 * Deterministic per-question option ordering for the Daily Challenge (pure → testable).
 *
 * Options are shown in a stable shuffled order derived from the question id, so:
 *  - the correct answer isn't always in the same slot (fairness), and
 *  - the order is identical across process death / restart (safe restoration): the same question id
 *    always yields the same display order, so a resumed challenge looks unchanged.
 *
 * [displayOrder] returns display-position → original-option-index. Map a tapped display position to
 * its original index with `displayOrder(id, n)[pos]`, then compare to the question's answerIndex.
 */
object DailyChallengeOptions {

    fun displayOrder(questionId: String, optionCount: Int): List<Int> {
        if (optionCount <= 1) return (0 until optionCount).toList()
        // Stable, id-seeded key per original index; sort by it for a deterministic permutation.
        return (0 until optionCount).sortedBy { idx ->
            var h = 1125899906842597L // FNV-ish seed
            val s = "$questionId#$idx"
            for (c in s) h = 31 * h + c.code
            h
        }
    }

    /** Display position at which [originalAnswerIndex] appears. */
    fun displayIndexOfAnswer(questionId: String, optionCount: Int, originalAnswerIndex: Int): Int =
        displayOrder(questionId, optionCount).indexOf(originalAnswerIndex).coerceAtLeast(0)
}
