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
        val ranked = pool.filter { it.stemHash !in usedStems }
            .sortedWith(compareBy({ tierOrder(it.qualityTier) }, { rng.nextInt() }))
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
