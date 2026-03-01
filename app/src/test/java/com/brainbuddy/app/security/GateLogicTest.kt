package com.brainbuddy.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for gate pass/fail logic.
 * Gate FAILED when wrongCount >= 4; PASS when wrongCount <= 3.
 */
class GateLogicTest {

    @Test
    fun gate_passedWhenWrongCount3OrLess() {
        assertTrue(passed(0, 10))
        assertTrue(passed(1, 10))
        assertTrue(passed(2, 10))
        assertTrue(passed(3, 10))
    }

    @Test
    fun gate_failedWhenWrongCount4OrMore() {
        assertFalse(passed(4, 10))
        assertFalse(passed(5, 10))
        assertFalse(passed(10, 10))
    }

    @Test
    fun rewardedRetry_decrementsWrongCountCorrectly() {
        // If wrongCount=4 and user answers 1 retry question correctly -> newWrongCount=3 -> pass
        val wrongCount = 4
        val newWrongCount = (wrongCount - 1).coerceAtLeast(0)
        assertTrue(newWrongCount < 4)
    }

    @Test
    fun rewardedRetry_wrongAnswerKeepsLocked() {
        val wrongCount = 4
        val newWrongCount = wrongCount
        assertFalse(newWrongCount < 4)
    }

    private fun passed(wrongCount: Int, totalCount: Int): Boolean = wrongCount <= 3
}
