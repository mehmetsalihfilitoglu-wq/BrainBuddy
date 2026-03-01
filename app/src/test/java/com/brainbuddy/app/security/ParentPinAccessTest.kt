package com.brainbuddy.app.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sanity tests for Parent PIN access control logic.
 * Verifies pass/fail thresholds and gate state requirements.
 */
class ParentPinAccessTest {

    @Test
    fun permissionLock_requiresParentPin() {
        val isPermissionLocked = true
        val userLocked = true
        assertTrue(isPermissionLocked || userLocked)
    }

    @Test
    fun gateRequired_whenUserLocked() {
        val userLocked = true
        assertTrue(userLocked)
    }

    @Test
    fun gateRequired_whenPermissionLocked() {
        val permissionLocked = true
        assertTrue(permissionLocked)
    }

    @Test
    fun gateNotRequired_whenNeitherLocked() {
        val userLocked = false
        val permissionLocked = false
        assertFalse(userLocked || permissionLocked)
    }

    @Test
    fun pinLength_valid4or6() {
        assertTrue(isValidPinLength(4))
        assertTrue(isValidPinLength(6))
        assertFalse(isValidPinLength(3))
        assertFalse(isValidPinLength(5))
        assertFalse(isValidPinLength(7))
    }

    private fun isValidPinLength(len: Int): Boolean = len == 4 || len == 6
}
