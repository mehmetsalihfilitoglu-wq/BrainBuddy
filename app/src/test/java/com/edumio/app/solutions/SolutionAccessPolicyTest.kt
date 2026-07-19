package com.edumio.app.solutions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Premium gate for solution bodies: fail-closed, no side channel. */
class SolutionAccessPolicyTest {

    @Test
    fun premium_getsFullSolution() {
        assertEquals(SolutionAccessPolicy.Access.FULL_SOLUTION, SolutionAccessPolicy.access(true))
        assertTrue(SolutionAccessPolicy.mayBindSolutionText(SolutionAccessPolicy.access(true)))
    }

    @Test
    fun free_isLocked() {
        assertEquals(SolutionAccessPolicy.Access.LOCKED, SolutionAccessPolicy.access(false))
        assertFalse(SolutionAccessPolicy.mayBindSolutionText(SolutionAccessPolicy.access(false)))
    }

    @Test
    fun unknownOrFailedEntitlement_behavesAsFree() {
        assertEquals(SolutionAccessPolicy.Access.LOCKED, SolutionAccessPolicy.access(null))
        assertFalse(SolutionAccessPolicy.mayBindSolutionText(SolutionAccessPolicy.access(null)))
    }

    @Test
    fun correctnessVisibility_isNeverGated() {
        // Free users always see whether they were correct and which option is correct.
        assertTrue(SolutionAccessPolicy.canSeeCorrectness())
    }
}
