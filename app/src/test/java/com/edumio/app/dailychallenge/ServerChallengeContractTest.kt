package com.edumio.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server-authoritative challenge contract must enforce the frozen invariants. */
class ServerChallengeContractTest {

    private val C = ServerChallengeContract

    @Test fun challengeSizeIsFive() {
        assertEquals(5, C.CHALLENGE_SIZE)
    }

    @Test fun idempotencyKeyFormat() {
        assertEquals("dailyChallenge:uid123:2026-07-19", C.idempotencyKey("uid123", "2026-07-19"))
    }

    @Test fun proposalMustBeExactlyFiveDistinct() {
        assertTrue(C.isValidProposal(listOf("a", "b", "c", "d", "e")))
        assertFalse("only four", C.isValidProposal(listOf("a", "b", "c", "d")))
        assertFalse("six", C.isValidProposal(listOf("a", "b", "c", "d", "e", "f")))
        assertFalse("duplicate", C.isValidProposal(listOf("a", "b", "c", "d", "a")))
        assertFalse("blank", C.isValidProposal(listOf("a", "b", "c", "d", "")))
    }

    @Test fun completionOnlyForServerCurrentDay() {
        assertTrue(C.canCompleteForDay("2026-07-19", "2026-07-19"))
        assertFalse("backdated", C.canCompleteForDay("2026-07-18", "2026-07-19"))
        assertFalse("future", C.canCompleteForDay("2026-07-20", "2026-07-19"))
    }

    @Test fun completionIsMonotonic() {
        assertEquals(C.STATUS_COMPLETED, C.nextStatusOnComplete(C.STATUS_OPEN))
        assertEquals(C.STATUS_COMPLETED, C.nextStatusOnComplete(C.STATUS_COMPLETED))
        assertEquals(C.STATUS_COMPLETED, C.nextStatusOnComplete(null))
    }

    @Test fun existingChallengeMustBeReturnedNotRecreated() {
        assertTrue(C.mustReturnExisting(C.STATUS_OPEN))
        assertTrue(C.mustReturnExisting(C.STATUS_COMPLETED))
        assertFalse(C.mustReturnExisting(null)) // no challenge yet → may create exactly one
    }
}
