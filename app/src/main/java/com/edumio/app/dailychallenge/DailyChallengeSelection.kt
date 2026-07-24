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
}
