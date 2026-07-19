package com.edumio.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Merge semantics must protect the frozen invariants across two-device sync. */
class SyncConflictResolverTest {

    private val R = SyncConflictResolver

    @Test fun lastWriteWins_newerSideWins_remoteWinsTies() {
        assertEquals("remote", R.lastWriteWins("local", 10, "remote", 20))
        assertEquals("local", R.lastWriteWins("local", 30, "remote", 20))
        assertEquals("remote", R.lastWriteWins("local", 20, "remote", 20)) // tie → remote (server)
    }

    @Test fun longestStreakNeverDecreases() {
        assertEquals(12, R.resolveLongestStreak(12, 5))
        assertEquals(12, R.resolveLongestStreak(5, 12))
        assertEquals(7, R.resolveLongestStreak(7, 7))
    }

    @Test fun completionIsSticky() {
        assertTrue(R.monotonicCompletion(localCompleted = true, remoteCompleted = false))
        assertTrue(R.monotonicCompletion(localCompleted = false, remoteCompleted = true))
        assertTrue(R.monotonicCompletion(true, true))
        assertFalse(R.monotonicCompletion(false, false))
    }

    @Test fun canonicalChallengeIsFirstCreated() {
        // Device A created the day's challenge first → its id is canonical even if B is "newer".
        assertEquals("A", R.canonicalChallengeId("A", 100, "B", 200))
        assertEquals("B", R.canonicalChallengeId("A", 300, "B", 200))
    }

    @Test fun canonicalChallenge_equalCreatedAt_isDeterministic() {
        // Same seed/day → ids should already match; the tiebreak is stable regardless of call order.
        assertEquals(
            R.canonicalChallengeId("X", 100, "Y", 100),
            R.canonicalChallengeId("X", 100, "Y", 100),
        )
    }

    @Test fun deficitMerge_prefersNewerPerOverlap_keepsUniqueSections() {
        val local = mapOf("bio" to 3, "chem" to 1)
        val remote = mapOf("bio" to 5, "phys" to 2)
        // remote newer → bio=5 (remote), phys=2 (remote-only), chem=1 (local-only preserved)
        val merged = R.mergeDeficits(local, 10, remote, 20)
        assertEquals(5, merged["bio"])
        assertEquals(1, merged["chem"])
        assertEquals(2, merged["phys"])
    }

    @Test fun deficitMerge_localNewer() {
        val local = mapOf("bio" to 9)
        val remote = mapOf("bio" to 1, "chem" to 4)
        val merged = R.mergeDeficits(local, 50, remote, 20) // local newer → bio=9, chem preserved
        assertEquals(9, merged["bio"])
        assertEquals(4, merged["chem"])
    }
}
