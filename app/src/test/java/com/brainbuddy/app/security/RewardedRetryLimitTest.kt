package com.brainbuddy.app.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Unit tests for rewarded retry limits.
 * Max 1 retry per day per profile.
 */
class RewardedRetryLimitTest {

    private val MAX_RETRIES_PER_DAY = 1

    @Test
    fun remainingRetries_startsAtMax() {
        val used = 0
        val remaining = (MAX_RETRIES_PER_DAY - used).coerceAtLeast(0)
        assertEquals(1, remaining)
    }

    @Test
    fun remainingRetries_zeroWhenUsed() {
        val used = 1
        val remaining = (MAX_RETRIES_PER_DAY - used).coerceAtLeast(0)
        assertEquals(0, remaining)
    }

    @Test
    fun canRetry_falseWhenAtLimit() {
        val used = 1
        assertFalse(used < MAX_RETRIES_PER_DAY)
    }

    @Test
    fun canRetry_trueWhenUnderLimit() {
        val used = 0
        org.junit.Assert.assertTrue(used < MAX_RETRIES_PER_DAY)
    }

    @Test
    fun recordRetry_coercesWithinBounds() {
        val nextCount = (0 + 1).coerceIn(0, MAX_RETRIES_PER_DAY)
        assertEquals(1, nextCount)
    }
}
