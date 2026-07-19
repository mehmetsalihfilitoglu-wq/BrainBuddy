package com.edumio.app.dailychallenge

/**
 * The client↔server contract for the server-authoritative Daily Challenge (Phase 3), as pure, unit-tested
 * logic shared in spirit with the Cloud Function (`functions/index.js`). Because the question banks are
 * bundled in the app, selection stays on the client; the **server owns identity**: it persists exactly ONE
 * immutable challenge per (uid, canonical day) and validates every state transition. This object encodes the
 * rules both sides must agree on, so they can be verified without a backend.
 *
 * Model: client computes the canonical day + proposes 5 question ids → server transaction creates the
 * challenge iff none exists (first-writer-wins across devices), else returns the existing one → answers work
 * offline → completion syncs idempotently and is monotonic.
 */
object ServerChallengeContract {

    val CHALLENGE_SIZE = DailyChallengeBlueprint.CHALLENGE_SIZE

    const val STATUS_OPEN = "OPEN"
    const val STATUS_COMPLETED = "COMPLETED"

    /** Idempotency key for challenge creation / completion. */
    fun idempotencyKey(uid: String, canonicalDay: String): String = "dailyChallenge:$uid:$canonicalDay"

    /** A proposal is valid iff it is exactly [CHALLENGE_SIZE] distinct, non-blank question ids. */
    fun isValidProposal(questionIds: List<String>): Boolean =
        questionIds.size == CHALLENGE_SIZE &&
            questionIds.none { it.isBlank() } &&
            questionIds.toSet().size == CHALLENGE_SIZE

    /**
     * A completion may only be recorded for the server's CURRENT canonical day — this defeats backdating
     * and future-dating via a manipulated device clock (the server computes [serverCurrentDay] from server
     * time + the user's stored timezone).
     */
    fun canCompleteForDay(requestedDay: String, serverCurrentDay: String): Boolean =
        requestedDay == serverCurrentDay

    /** Completion is monotonic (sticky): once COMPLETED it never re-opens. */
    fun nextStatusOnComplete(currentStatus: String?): String = STATUS_COMPLETED

    /** True if a create request must be rejected because the day already has a challenge (no 2nd challenge). */
    fun mustReturnExisting(existingStatus: String?): Boolean = existingStatus != null
}
