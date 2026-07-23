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

    /**
     * [letterOptions] = the choices are bare letters whose real options live inside the figure
     * (see [com.edumio.app.quiz.OptionLabels.isLetterOptions]). Those must be shown in identity order so
     * each letter still points at the correct region of the image; only genuine text options are shuffled.
     */
    fun displayOrder(questionId: String, optionCount: Int, letterOptions: Boolean = false): List<Int> {
        if (optionCount <= 1 || letterOptions) return (0 until optionCount).toList()
        // Stable, id-seeded key per original index; sort by it for a deterministic permutation.
        return (0 until optionCount).sortedBy { idx ->
            var h = 1125899906842597L // FNV-ish seed
            val s = "$questionId#$idx"
            for (c in s) h = 31 * h + c.code
            h
        }
    }

    /** Display position at which [originalAnswerIndex] appears. */
    fun displayIndexOfAnswer(questionId: String, optionCount: Int, originalAnswerIndex: Int, letterOptions: Boolean = false): Int =
        displayOrder(questionId, optionCount, letterOptions).indexOf(originalAnswerIndex).coerceAtLeast(0)
}
