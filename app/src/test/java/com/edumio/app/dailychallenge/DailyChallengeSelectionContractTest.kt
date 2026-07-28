package com.edumio.app.dailychallenge

import com.edumio.app.db.QuestionCandidateRow
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/**
 * Regression guard for the comparator-contract bug that made a whole exam fail to generate a challenge
 * (first observed on CEnT-S; the pipeline is shared, so it was never exam-specific).
 *
 * pickN once sorted with `compareBy(tierOrder, { rng.nextInt() })`. That selector returns a DIFFERENT
 * value on every comparison, violating the Comparator contract, so TimSort throws
 * IllegalArgumentException("Comparison method violates its general contract!") for some pools/seeds. The
 * throw propagated out of getOrCreateToday, was swallowed to null, and Home showed "Sorular yüklenemedi".
 *
 * pickN must NEVER throw (any pool, any seed), must be deterministic for a fixed seed, and must still
 * prefer higher quality tiers.
 */
class DailyChallengeSelectionContractTest {

    private fun row(i: Int, tier: String) = QuestionCandidateRow(
        id = "q$i", subject = "math", difficulty = 2, grade = 13,
        stemHash = "h$i", stemNormalized = "s$i", type = "PROBLEM",
        skill = "math", topic = "t${i % 20}", qualityTier = tier, reasoningLevel = 2,
    )

    private fun pool(n: Int, tiers: List<String>): List<QuestionCandidateRow> =
        (0 until n).map { row(it, tiers[it % tiers.size]) }

    @Test
    fun pickN_neverThrows_acrossManySeeds_onLargeMixedTierPool() {
        // A large pool with many equal-tier elements is exactly what triggers TimSort's contract check.
        val pool = pool(400, listOf("ELITE", "HARD", "MEDIUM", "EASY"))
        for (seed in 0 until 500) {
            val picked = DailyChallengeSelection.pickN(pool, 5, mutableSetOf(), mutableSetOf(), Random(seed.toLong()))
            assertEquals("seed=$seed must still pick 5", 5, picked.size)
            assertEquals("seed=$seed no duplicate ids", 5, picked.map { it.id }.toSet().size)
        }
    }

    @Test
    fun pickN_isDeterministicForAFixedSeed() {
        val pool = pool(200, listOf("ELITE", "HARD", "MEDIUM"))
        val a = DailyChallengeSelection.pickN(pool, 5, mutableSetOf(), mutableSetOf(), Random(42))
        val b = DailyChallengeSelection.pickN(pool, 5, mutableSetOf(), mutableSetOf(), Random(42))
        assertEquals("same seed → identical selection", a.map { it.id }, b.map { it.id })
    }

    @Test
    fun pickN_stillPrefersHigherTier() {
        // One ELITE amongst many MEDIUM: tier ordering must survive the fix (ELITE picked first).
        val rows = ArrayList<QuestionCandidateRow>()
        rows += QuestionCandidateRow(
            id = "elite", subject = "math", difficulty = 2, grade = 13, stemHash = "hE",
            stemNormalized = "sE", type = "PROBLEM", skill = "math", topic = "tE", qualityTier = "ELITE", reasoningLevel = 3,
        )
        for (i in 0 until 50) rows += row(i, "MEDIUM")
        val picked = DailyChallengeSelection.pickN(rows, 1, mutableSetOf(), mutableSetOf(), Random(7))
        assertEquals("ELITE is chosen before MEDIUM", "elite", picked.single().id)
    }
}
