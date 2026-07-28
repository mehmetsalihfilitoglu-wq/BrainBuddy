package com.edumio.app.email

/**
 * Pure email-governance rules (consent + frequency), unit-tested so the legal/UX guarantees hold
 * independent of any provider: transactional always sends; learning is opt-OUT (on by default,
 * user-controllable); marketing is opt-IN (explicit consent); the daily reminder email is ≤1/day and stops
 * on completion; duplicates are suppressed by idempotency key.
 *
 * (Email VERIFICATION + PASSWORD RESET are sent natively by Firebase Auth — not through this layer.)
 */
enum class EmailCategory { TRANSACTIONAL, LEARNING, MARKETING }

object EmailConsentPolicy {

    /**
     * @param learningEnabled user's learning-email preference (default true; opt-out)
     * @param marketingConsent explicit marketing opt-in (default false; opt-in)
     */
    fun canSend(category: EmailCategory, learningEnabled: Boolean, marketingConsent: Boolean): Boolean =
        when (category) {
            EmailCategory.TRANSACTIONAL -> true
            EmailCategory.LEARNING -> learningEnabled
            EmailCategory.MARKETING -> marketingConsent
        }

    /** Every non-transactional email must honour an unsubscribe; transactional cannot be unsubscribed. */
    fun canUnsubscribe(category: EmailCategory): Boolean = category != EmailCategory.TRANSACTIONAL
}

object EmailFrequencyPolicy {

    /** The daily-reminder email: at most one per day and never after the challenge is completed. */
    fun dailyReminderAllowed(alreadySentTodayForUser: Boolean, completedToday: Boolean): Boolean =
        !alreadySentTodayForUser && !completedToday

    /** Category frequency cap within a window (e.g. marketing ≤ N per week). */
    fun withinCap(sentInWindow: Int, cap: Int): Boolean = sentInWindow < cap

    /** Idempotency / dedup: an email with a key already processed must not be re-sent. */
    fun isDuplicate(idempotencyKey: String, alreadyProcessed: Boolean): Boolean =
        idempotencyKey.isNotBlank() && alreadyProcessed

    /** A suppressed address (hard bounce / complaint) blocks all further sends. */
    fun isSuppressed(bounced: Boolean, complained: Boolean): Boolean = bounced || complained
}
