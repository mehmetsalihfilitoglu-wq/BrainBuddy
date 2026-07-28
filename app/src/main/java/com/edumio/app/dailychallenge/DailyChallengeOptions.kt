package com.edumio.app.dailychallenge

import kotlin.random.Random

/**
 * Deterministic per-question option ordering for the Daily Challenge (pure → testable).
 *
 * Options are shown in a stable, genuinely-shuffled order derived from the question id (plus an optional
 * session key and a fixed salt), so:
 *  - the correct answer is NOT biased to any fixed slot. The previous algorithm sorted the indices by a
 *    per-index hash `"$id#$idx"`; because those hashes differed only in the trailing index digit and
 *    increased monotonically with it, `sortedBy { hash }` always returned 0,1,2,3,4 — i.e. identity
 *    order, leaving the correct answer permanently in its original position. This uses a seeded
 *    Fisher–Yates shuffle whose seed mixes EVERY character of the id, producing a real permutation.
 *  - the order is identical across process death / restart, and across the challenge, review, solution
 *    and retry screens for the same question (safe restoration + consistent UX): the same inputs always
 *    yield the same permutation.
 *
 * [displayOrder] returns display-position → original-option-index. Map a tapped display position to its
 * original index with `displayOrder(id, n)[pos]`, then compare to the question's answerIndex. This is the
 * single, central place option ordering is decided; every flow screen calls through here.
 */
object DailyChallengeOptions {

    /** Fixed salt so the seed can never collide with another id-derived value elsewhere in the app. */
    private const val SALT = "edumio-dc-options-v1"

    /**
     * Display order (display-position → original-option-index) for a question's options.
     *
     * @param letterOptions true when the choices are ONLY the bare letters A, B, C, … whose real options
     *   live inside the question figure — these are NEVER shuffled (identity order), so each on-screen
     *   letter keeps pointing at the correct region of the image. See
     *   [com.edumio.app.quiz.OptionLabels.isLetterOptions].
     * @param sessionKey optional extra entropy (e.g. a challenge/session id): different keys can yield a
     *   different order for the same question. Production seeds per-question (sessionKey = "") so the
     *   order stays consistent across the challenge/review/solution/retry screens; the parameter exists
     *   so callers may vary the order per session if ever needed.
     */
    fun displayOrder(
        questionId: String,
        optionCount: Int,
        sessionKey: String = "",
        letterOptions: Boolean = false,
    ): List<Int> {
        if (optionCount <= 1 || letterOptions) return (0 until optionCount).toList()
        val order = (0 until optionCount).toMutableList()
        order.shuffle(Random(seedFor(questionId, sessionKey))) // deterministic Fisher–Yates
        return order
    }

    /** Display position at which [originalAnswerIndex] appears (same inputs as [displayOrder]). */
    fun displayIndexOfAnswer(
        questionId: String,
        optionCount: Int,
        originalAnswerIndex: Int,
        sessionKey: String = "",
        letterOptions: Boolean = false,
    ): Int =
        displayOrder(questionId, optionCount, sessionKey, letterOptions)
            .indexOf(originalAnswerIndex).coerceAtLeast(0)

    /** Stable 64-bit FNV-1a hash over id|session|salt — mixes ALL characters, not just the last one. */
    private fun seedFor(questionId: String, sessionKey: String): Long {
        var h = -3750763034362895579L // FNV-1a 64-bit offset basis (14695981039346656037 as a signed Long)
        val s = "$questionId|$sessionKey|$SALT"
        for (c in s) {
            h = h xor c.code.toLong()
            h *= 1099511628211L // FNV-1a 64-bit prime
        }
        return h
    }
}
