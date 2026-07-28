package com.edumio.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for deterministic, restart-stable option ordering. */
class DailyChallengeOptionsTest {

    private val identity5 = listOf(0, 1, 2, 3, 4)

    // ── The shuffle is REAL, not the old identity-order degeneracy ─────────────────────────────────

    @Test
    fun textOptions_areReallyShuffled_notIdentity() {
        // At least one question id must produce a genuinely non-identity order (the old algorithm never
        // did). Try a spread of ids; assert some order differs from 0,1,2,3,4.
        val ids = (0 until 40).map { "q_$it" }
        assertTrue(
            "expected at least one non-identity shuffle across ids",
            ids.any { DailyChallengeOptions.displayOrder(it, 5) != identity5 },
        )
    }

    @Test
    fun correctAnswerIsNotAlwaysInTheSameSlot() {
        // Across many questions, the correct answer's DISPLAY position must spread across slots — the
        // exact bug being fixed (it used to be pinned to the original index for every question).
        val answerIndex = 0
        val slots = (0 until 60)
            .map { "spread_$it" }
            .map { DailyChallengeOptions.displayIndexOfAnswer(it, 5, answerIndex) }
            .toSet()
        assertTrue("correct answer should not sit in a single fixed slot", slots.size >= 3)
    }

    // ── Determinism: same question + same session → identical order ────────────────────────────────

    @Test
    fun sameQuestionSameSession_isStable() {
        val a = DailyChallengeOptions.displayOrder("imat_bio_7", 5, sessionKey = "2026-07-24")
        val b = DailyChallengeOptions.displayOrder("imat_bio_7", 5, sessionKey = "2026-07-24")
        assertEquals("same id + same session must be identical (survives process death / restoration)", a, b)
    }

    @Test
    fun sameQuestionDifferentSession_canDiffer() {
        // The seed includes the session key, so different sessions CAN produce different orders. Find one.
        val differs = (0 until 40).any {
            val q = "sess_$it"
            DailyChallengeOptions.displayOrder(q, 5, sessionKey = "s1") !=
                DailyChallengeOptions.displayOrder(q, 5, sessionKey = "s2")
        }
        assertTrue("different session should be able to yield a different order", differs)
    }

    @Test
    fun differentQuestions_canDiffer() {
        val differs = (0 until 40).any {
            DailyChallengeOptions.displayOrder("qa_$it", 5) != DailyChallengeOptions.displayOrder("qb_$it", 5)
        }
        assertTrue("different questions should be able to yield different orders", differs)
    }

    // ── CRITICAL exception: bare-letter (options-in-image) questions are NEVER shuffled ────────────

    @Test
    fun letterOptions_areNeverShuffled() {
        // Whatever the id/session, A–E questions keep identity order so each letter matches the figure.
        listOf("q1", "imat_fig_42", "z", "sess_9").forEach { id ->
            assertEquals(identity5, DailyChallengeOptions.displayOrder(id, 5, letterOptions = true))
            assertEquals(identity5, DailyChallengeOptions.displayOrder(id, 5, sessionKey = "s2", letterOptions = true))
        }
    }

    // ── Mapping integrity that scoring / review / solution / retry all rely on ─────────────────────

    @Test
    fun mappingIsABijection_userChoiceRoundTrips() {
        // order[displayPos] is the ORIGINAL option index the user actually tapped at that display slot,
        // and the mapping is a bijection — so the stored answer and the highlighted answer stay in sync.
        val id = "cents_math_042"; val n = 5
        val order = DailyChallengeOptions.displayOrder(id, n, sessionKey = "sess")
        assertEquals((0 until n).toSet(), order.toSet()) // bijection
        for (originalAnswer in 0 until n) {
            val displayPos = DailyChallengeOptions.displayIndexOfAnswer(id, n, originalAnswer, sessionKey = "sess")
            assertTrue(displayPos in 0 until n)
            assertEquals("display position must map back to the original answer index", originalAnswer, order[displayPos])
        }
    }

    @Test
    fun fiveOptionQuestion_shufflesAllFiveDeterministically() {
        val order = DailyChallengeOptions.displayOrder("imat_5opt", 5)
        assertEquals(5, order.size)
        assertEquals((0..4).toSet(), order.toSet())
        assertFalse("a real shuffle must not repeat an index", order.size != order.toSet().size)
    }

    @Test
    fun displayOrderIsAValidPermutation() {
        for (n in listOf(4, 5)) {
            val order = DailyChallengeOptions.displayOrder("q123", n)
            assertEquals(n, order.size)
            assertEquals("must be a permutation of 0..n-1", (0 until n).toSet(), order.toSet())
        }
    }

    @Test
    fun displayOrderIsStableAcrossCalls_safeRestoration() {
        val a = DailyChallengeOptions.displayOrder("cents_math_042", 5)
        val b = DailyChallengeOptions.displayOrder("cents_math_042", 5)
        assertEquals("same id must yield identical order (survives process death)", a, b)
    }

    @Test
    fun differentQuestionsGetIndependentOrders() {
        val a = DailyChallengeOptions.displayOrder("q_a", 5)
        val b = DailyChallengeOptions.displayOrder("q_b", 5)
        // not asserting they differ (could coincide) — asserting both are valid permutations
        assertEquals((0..4).toSet(), a.toSet())
        assertEquals((0..4).toSet(), b.toSet())
    }

    @Test
    fun answerDisplayIndexRoundTrips() {
        val id = "til_phys_9"
        val n = 5
        val order = DailyChallengeOptions.displayOrder(id, n)
        for (answer in 0 until n) {
            val pos = DailyChallengeOptions.displayIndexOfAnswer(id, n, answer)
            assertTrue(pos in 0 until n)
            assertEquals("display position maps back to the original answer index", answer, order[pos])
        }
    }

    @Test
    fun singleOptionIsIdentity() {
        assertEquals(listOf(0), DailyChallengeOptions.displayOrder("x", 1))
        assertEquals(emptyList<Int>(), DailyChallengeOptions.displayOrder("x", 0))
    }
}
