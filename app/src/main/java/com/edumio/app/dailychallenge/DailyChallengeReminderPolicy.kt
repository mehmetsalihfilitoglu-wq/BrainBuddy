package com.edumio.app.dailychallenge

/**
 * Pure scheduling policy for the three local Daily-Challenge reminders (no Android deps → testable).
 *
 * Slots (user's local time): 09:00, 16:00, 20:30. The scheduler enqueues one periodic worker per
 * slot; each worker consults [shouldFire] before posting. Completion of today's challenge suppresses
 * every remaining slot for the day — that is how "cancel remaining reminders after completion" is
 * honored without fragile per-occurrence cancellation (tomorrow's slots stay intact automatically).
 */
object DailyChallengeReminderPolicy {

    const val MINUTES_PER_DAY = 24 * 60

    /** Reminder times as minute-of-day: 09:00, 16:00, 20:30. */
    val SLOT_MINUTES = intArrayOf(9 * 60, 16 * 60, 20 * 60 + 30)

    fun slotCount(): Int = SLOT_MINUTES.size

    /** Minutes from [nowMinuteOfDay] until the next occurrence of [slotMinuteOfDay] (today or tomorrow). */
    fun delayMinutesToSlot(slotMinuteOfDay: Int, nowMinuteOfDay: Int): Int {
        val diff = slotMinuteOfDay - nowMinuteOfDay
        return if (diff > 0) diff else diff + MINUTES_PER_DAY
    }

    /** Slots still ahead of [nowMinuteOfDay] within the current day (in order). */
    fun remainingSlotsToday(nowMinuteOfDay: Int): List<Int> =
        SLOT_MINUTES.filter { it > nowMinuteOfDay }

    /**
     * Whether a reminder should actually be posted for its slot right now.
     * Suppressed if notifications are off, today's challenge is already completed, or this slot
     * has already fired today (idempotency guard against WorkManager re-runs).
     */
    fun shouldFire(
        notificationsEnabled: Boolean,
        completedToday: Boolean,
        alreadyFiredThisSlotToday: Boolean,
    ): Boolean = notificationsEnabled && !completedToday && !alreadyFiredThisSlotToday
}
