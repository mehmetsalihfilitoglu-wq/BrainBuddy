package com.edumio.app.dailychallenge

import com.edumio.app.db.QuestionCandidateRow
import kotlin.random.Random

/**
 * Pure question-selection helpers for the Daily Challenge (no Android/DB deps → unit-testable).
 * Selection prefers the selective tiers (Elite/Hard), distinct topics, and unique stems.
 */
object DailyChallengeSelection {

    fun tierOrder(t: String): Int = when (t.uppercase()) { "ELITE" -> 0; "HARD" -> 1; "MEDIUM" -> 2; else -> 3 }

    /**
     * Pick up to [count] rows from [pool], skipping any whose stemHash is in [usedStems] or whose id is
     * already chosen. First pass enforces distinct topics; a relaxed pass fills any remaining slots.
     * Mutates [usedTopics]/[usedStems] so callers can chain across sections within one challenge.
     */
    fun pickN(
        pool: List<QuestionCandidateRow>,
        count: Int,
        usedTopics: MutableSet<String>,
        usedStems: MutableSet<String>,
        rng: Random,
    ): List<QuestionCandidateRow> {
        if (count <= 0 || pool.isEmpty()) return emptyList()
        // Randomize within each quality tier, then order by tier. The random tiebreak MUST be a stable key
        // (shuffle once), NOT `compareBy(..., { rng.nextInt() })`: a comparator selector that returns a new
        // random value on every comparison violates the Comparator contract, so TimSort throws
        // IllegalArgumentException("Comparison method violates its general contract!") for some pools/seeds.
        // That surfaced as a whole exam (seen first on CEnT-S) failing to generate a challenge —
        // getOrCreateToday threw, was swallowed to null, and Home showed "Sorular yüklenemedi".
        // shuffled(rng) is deterministic for a fixed seed and sortedBy is a stable sort, so this keeps the
        // engine reproducible while fixing the contract violation.
        val ranked = pool.filter { it.stemHash !in usedStems }
            .shuffled(rng)
            .sortedBy { tierOrder(it.qualityTier) }
        val out = ArrayList<QuestionCandidateRow>(count)
        for (c in ranked) {
            if (out.size >= count) break
            if (c.topic in usedTopics || c.stemHash in usedStems || out.any { it.id == c.id }) continue
            out += c; usedTopics += c.topic; usedStems += c.stemHash
        }
        if (out.size < count) for (c in ranked) {
            if (out.size >= count) break
            if (c.stemHash in usedStems || out.any { it.id == c.id }) continue
            out += c; usedTopics += c.topic; usedStems += c.stemHash
        }
        return out
    }

    /** One due scheduled review, reduced to what injection needs. */
    data class DueReview(val questionId: String, val section: String, val nextReviewAt: Long)

    /** Outcome of injecting due reviews into a freshly selected challenge. */
    data class Injection(
        /** Final ordered ids, still exactly [selected].size — reviews have REPLACED same-section picks. */
        val orderedIds: List<String>,
        /** Ids of the reviews that were injected (subset of [orderedIds]). */
        val injectedIds: List<String>,
        /** Newly selected questions that were displaced. NEVER exposed/retired — still fully eligible. */
        val displacedIds: List<String>,
        /** Due reviews that found no matching section slot; left scheduled for a later challenge. */
        val deferredIds: List<String>,
    )

    /**
     * Replaces up to [maxInjected] freshly selected questions with due scheduled reviews FROM THE SAME
     * SECTION, keeping the challenge size and the blueprint's section distribution exactly as generated.
     *
     * Rules, in order:
     *  - a review may only displace a new question whose subject equals the review's section (never
     *    forced into another subject);
     *  - reviews are considered oldest-due first, so the longest-overdue item wins a contested slot;
     *  - a review already present among the selected ids is skipped (never duplicated);
     *  - a review with no matching section slot is DEFERRED, not forced;
     *  - injected questions are spread out — two reviews are never left adjacent when the length allows
     *    separation.
     *
     * Pure and deterministic: no I/O, no randomness, so the whole contract is unit-testable.
     */
    fun injectDueReviews(
        selected: List<QuestionCandidateRow>,
        due: List<DueReview>,
        maxInjected: Int = 2,
    ): Injection {
        val ids = selected.map { it.id }
        if (due.isEmpty() || maxInjected <= 0 || selected.isEmpty()) {
            return Injection(ids, emptyList(), emptyList(), due.map { it.questionId })
        }

        val slots = selected.toMutableList()          // index -> current occupant
        val injectedAt = LinkedHashMap<Int, String>() // slot index -> review question id
        val displaced = ArrayList<String>()
        val deferred = ArrayList<String>()

        // Oldest due first settles contention for a shared section slot deterministically.
        for (review in due.sortedBy { it.nextReviewAt }) {
            if (injectedAt.size >= maxInjected) { deferred += review.questionId; continue }
            // Never duplicate a question already in today's five.
            if (slots.any { it.id == review.questionId }) { deferred += review.questionId; continue }
            // Same-section only, and never steal a slot already given to another review.
            val candidateIdx = slots.indices.filter { i ->
                i !in injectedAt.keys && slots[i].subject == review.section
            }
            if (candidateIdx.isEmpty()) { deferred += review.questionId; continue }
            val idx = pickSpreadSlot(candidateIdx, injectedAt.keys)
            displaced += slots[idx].id
            injectedAt[idx] = review.questionId
        }

        val ordered = slots.mapIndexed { i, row -> injectedAt[i] ?: row.id }
        return Injection(ordered, injectedAt.values.toList(), displaced, deferred)
    }

    /**
     * Of the eligible slots, prefer one that is not adjacent to an already-injected slot, so two
     * reviews are not shown back to back. Falls back to the first eligible slot when the layout leaves
     * no separated option (e.g. only adjacent slots match the section).
     */
    private fun pickSpreadSlot(eligible: List<Int>, taken: Set<Int>): Int {
        if (taken.isEmpty()) return eligible.first()
        val separated = eligible.filter { i -> taken.none { t -> kotlin.math.abs(t - i) <= 1 } }
        // Maximise distance from the nearest existing injection for a visibly even spread.
        return separated.maxByOrNull { i -> taken.minOf { t -> kotlin.math.abs(t - i) } }
            ?: eligible.first()
    }
}
