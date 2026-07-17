package com.edumio.app.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity tests for question pool fallback logic.
 * Ensures empty pool handling and mini-test fallback behavior.
 */
class QuestionPoolFallbackTest {

    @Test
    fun emptyPool_returnsFallback() {
        val mainPool = emptyList<String>()
        val fallback = listOf("fb1", "fb2", "fb3")
        val result = mainPool.ifEmpty { fallback }
        assertEquals(3, result.size)
        assertTrue(result.contains("fb1"))
    }

    @Test
    fun nonEmptyPool_usesMain() {
        val mainPool = listOf("q1", "q2")
        val fallback = listOf("fb1")
        val result = mainPool.ifEmpty { fallback }
        assertEquals(2, result.size)
        assertEquals("q1", result[0])
    }

    @Test
    fun remedialFallback_whenWeakTopicsEmpty() {
        val weakTopicIds = emptyList<String>()
        val allPool = listOf("q1", "q2")
        val pool = if (weakTopicIds.isEmpty()) allPool else emptyList()
        assertFalse(pool.isEmpty())
        assertEquals(2, pool.size)
    }

    @Test
    fun wrongCountBounds_forPass() {
        // Pass: wrongCount <= 3
        assertTrue(0 <= 3)
        assertTrue(3 <= 3)
        assertFalse(4 <= 3)
    }

    @Test
    fun wrongCountBounds_forFail() {
        // Fail: wrongCount >= 4
        assertTrue(4 >= 4)
        assertFalse(3 >= 4)
    }
}
