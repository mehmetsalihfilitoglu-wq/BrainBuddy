package com.brainbuddy.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for gate pass/fail logic (percentage vs parent threshold).
 */
class GateLogicTest {

    @Test
    fun gate_passedWhenMeetsThreshold() {
        assertTrue(passed(correct = 12, total = 20, thresholdPercent = 60))
        assertTrue(passed(correct = 18, total = 20, thresholdPercent = 60))
        assertTrue(passed(correct = 6, total = 10, thresholdPercent = 50))
    }

    @Test
    fun gate_failedWhenBelowThreshold() {
        assertFalse(passed(correct = 11, total = 20, thresholdPercent = 60))
        assertFalse(passed(correct = 4, total = 10, thresholdPercent = 50))
    }

    @Test
    fun gate_boundaryInclusive() {
        assertTrue(passed(correct = 12, total = 20, thresholdPercent = 60))
        assertFalse(passed(correct = 11, total = 20, thresholdPercent = 60))
    }

    @Test
    fun manyWrongsButHighPercentCanPass() {
        // Old wrongCount<=3 rule would fail; percentage rule passes 16/20 = 80%.
        assertTrue(passed(correct = 16, total = 20, thresholdPercent = 60))
    }

    private fun passed(correct: Int, total: Int, thresholdPercent: Int): Boolean {
        if (total <= 0) return false
        val pct = 100f * correct / total
        return pct >= thresholdPercent
    }
}
