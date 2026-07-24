package com.edumio.app.dailychallenge

import java.util.Locale

/**
 * Pure presentation logic for the home "Today's Challenge" card and the challenge flow (no Android
 * deps → unit-testable). Maps engine facts (answered/total/completed/unlock time) to what the UI
 * renders: card state, progress text, resume index, countdown, estimated duration. Keeping this pure
 * lets us test every home-card state, 0/5→5/5 progress, restoration, the no-6th-question rule, and
 * the countdown without a device.
 */
object DailyChallengeHomePresenter {

    /** Seconds of estimated effort per question (used for the "~N dk" hint). */
    const val SECONDS_PER_QUESTION = 60

    enum class CardState { UNAVAILABLE, AVAILABLE, IN_PROGRESS, COMPLETED, ERROR }

    /** The card state given engine facts. [supported] is false when the active exam has no bank. */
    fun cardState(supported: Boolean, answered: Int, total: Int, completed: Boolean): CardState = when {
        !supported -> CardState.UNAVAILABLE
        completed || (total > 0 && answered >= total) -> CardState.COMPLETED
        answered > 0 -> CardState.IN_PROGRESS
        else -> CardState.AVAILABLE
    }

    /**
     * The card state when the engine returned NO challenge (today() == null). This is NEVER "completed":
     * a genuinely completed challenge is a persisted row and always comes back as a non-null result, so a
     * null means today's challenge could not be built. For a supported exam that means the question bank
     * could not be loaded (e.g. not yet seeded / read error) → [CardState.ERROR] with a retry, not
     * "Tamamlandı"; for an unsupported exam there is simply no content for it yet → [CardState.UNAVAILABLE].
     */
    fun emptyState(supported: Boolean): CardState =
        if (supported) CardState.ERROR else CardState.UNAVAILABLE

    /** "2/5" style progress. */
    fun progressText(answered: Int, total: Int): String = "$answered/$total"

    /**
     * Where the flow resumes after a restart/process death: the number already answered (each answer
     * is persisted immediately), clamped to [total]. A completed challenge resumes at [total] (its
     * result screen), never at a 6th question.
     */
    fun resumeIndex(answered: Int, total: Int, completed: Boolean): Int =
        if (completed) total else answered.coerceIn(0, total)

    /** Estimated effort, e.g. "~5 dk" for 5 questions. */
    fun estimatedDurationText(total: Int): String {
        val minutes = (total * SECONDS_PER_QUESTION + 59) / 60
        return "~$minutes dk"
    }

    /**
     * Countdown to the next unlock (challenge expiry / next local midnight). Returns "" when already
     * unlocked (now past the target). Format: "Ns Mdk" (hours+minutes) or "Mdk" under an hour.
     */
    fun countdownText(nextUnlockAtMs: Long, nowMs: Long): String {
        val remaining = nextUnlockAtMs - nowMs
        if (remaining <= 0) return ""
        val totalMinutes = (remaining / 60000L).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) String.format(Locale.US, "%ds %ddk", hours, minutes)
        else String.format(Locale.US, "%ddk", minutes.coerceAtLeast(1))
    }
}
