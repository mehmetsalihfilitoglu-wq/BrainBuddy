package com.mioacademy.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for deterministic, restart-stable option ordering. */
class DailyChallengeOptionsTest {

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
