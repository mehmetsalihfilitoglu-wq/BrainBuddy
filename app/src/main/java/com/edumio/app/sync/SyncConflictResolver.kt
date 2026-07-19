package com.edumio.app.sync

import kotlin.math.max

/**
 * Pure conflict-resolution rules for two-device cloud sync, kept separate from any Firestore code so the
 * release-critical merge semantics are unit-tested without a backend. These rules protect the frozen
 * product invariants during a merge:
 *
 *  - **Last-write-wins** by `updatedAt`; the **server/remote** copy wins ties (deterministic across devices).
 *  - **One immutable challenge per account per day:** if both devices hold a challenge for the same day, the
 *    **first-created** one is canonical (smaller `createdAt`); identity/order never change. Two *different*
 *    challenges for one day is a bug — the earlier one is kept, never a merge that could yield a 6th question.
 *  - **Completion is monotonic (sticky):** once today's challenge is completed it stays completed — an older
 *    or other-device record can never re-open it (so "no second challenge after completion" holds).
 *  - **Streak longest never decreases:** the merged longest streak is the max of the two.
 *  - **Deficit ledger merges per-section** taking the value from whichever side wrote more recently.
 *
 * Premium entitlement and canonical challenge *issuance* are NOT resolved here — those become
 * server-authoritative (server-write-only) in Phases 3–4; this resolver only reconciles the user's own
 * client-owned learning mirror.
 */
object SyncConflictResolver {

    /** Generic last-write-wins. Ties go to [remote] (server-authoritative direction). */
    fun <T> lastWriteWins(local: T, localAt: Long, remote: T, remoteAt: Long): T =
        if (remoteAt >= localAt) remote else local

    /** The longest streak can only ever grow across a merge. */
    fun resolveLongestStreak(localLongest: Int, remoteLongest: Int): Int = max(localLongest, remoteLongest)

    /**
     * Current streak on merge: if the two sides disagree, prefer the more recently written value, but never
     * below the highest *current* the account legitimately reached today (completion is sticky).
     */
    fun resolveCurrentStreak(localCurrent: Int, localAt: Long, remoteCurrent: Int, remoteAt: Long): Int =
        lastWriteWins(localCurrent, localAt, remoteCurrent, remoteAt)

    /** Completion of today's challenge is sticky: true on either side → true. */
    fun monotonicCompletion(localCompleted: Boolean, remoteCompleted: Boolean): Boolean =
        localCompleted || remoteCompleted

    /**
     * Which challenge document is canonical for a given day. The first-created wins (server-authoritative
     * once Phase 3 lands); equal createdAt → the challengeIds MUST already match (same deterministic seed).
     * @return the canonical challengeId.
     */
    fun canonicalChallengeId(
        localId: String, localCreatedAt: Long,
        remoteId: String, remoteCreatedAt: Long,
    ): String = when {
        localCreatedAt < remoteCreatedAt -> localId
        remoteCreatedAt < localCreatedAt -> remoteId
        else -> remoteId // identical timestamps → deterministic; pick remote for a stable tiebreak
    }

    /** Merge two deficit ledgers, taking each section's value from the more recently written ledger. */
    fun mergeDeficits(
        local: Map<String, Int>, localAt: Long,
        remote: Map<String, Int>, remoteAt: Long,
    ): Map<String, Int> {
        val newer = if (remoteAt >= localAt) remote else local
        val older = if (remoteAt >= localAt) local else remote
        val merged = LinkedHashMap(older)
        merged.putAll(newer) // newer overrides on overlap; sections only in `older` are preserved
        return merged
    }
}
