package com.edumio.app.dailychallenge

/**
 * Pure scheduling policy for EDUmio's ONE daily study reminder (no Android deps → fully testable).
 *
 * v1 policy: exactly one respectful reminder per day at 11:30 local, and a hard allowed window of
 * 11:00–20:00 local. A study reminder must never wake or irritate a student.
 *
 * Why the window guard exists as well as the target time: WorkManager gives no wall-clock guarantee.
 * Deferred work (Doze, device asleep, battery saver) runs when the device next wakes, which is how
 * reminders were observed firing at 04:02 and 08:10. [isWithinAllowedWindow] is checked again at
 * POST time, so a late/catch-up run posts nothing and simply re-arms for tomorrow.
 */
object DailyChallengeReminderPolicy {

    const val MINUTES_PER_DAY = 24 * 60

    /** Hard boundaries — nothing may ever be posted outside these (minute-of-day, local). */
    const val WINDOW_START_MINUTE = 11 * 60      // 11:00
    const val WINDOW_END_MINUTE = 20 * 60        // 20:00

    /** The single daily reminder time: 11:30 local. */
    const val REMINDER_MINUTE = 11 * 60 + 30

    /**
     * Minutes from [nowMinuteOfDay] until the next 11:30. If today's time has already passed, this
     * returns the delay to TOMORROW's 11:30 — a missed slot must never fire instantly.
     */
    fun delayMinutesToNextReminder(nowMinuteOfDay: Int): Int {
        val diff = REMINDER_MINUTE - nowMinuteOfDay
        return if (diff > 0) diff else diff + MINUTES_PER_DAY
    }

    /** Whether a reminder may be POSTED at this local minute-of-day. */
    fun isWithinAllowedWindow(minuteOfDay: Int): Boolean =
        minuteOfDay >= WINDOW_START_MINUTE && minuteOfDay <= WINDOW_END_MINUTE

    /**
     * Whether the reminder should actually be posted right now. Suppressed when notifications are off,
     * today's challenge is already done, it already fired today (idempotency against WorkManager
     * re-runs), or the current local time is outside the allowed window.
     */
    fun shouldFire(
        notificationsEnabled: Boolean,
        completedToday: Boolean,
        alreadyFiredToday: Boolean,
        nowMinuteOfDay: Int,
    ): Boolean = notificationsEnabled &&
        !completedToday &&
        !alreadyFiredToday &&
        isWithinAllowedWindow(nowMinuteOfDay)
}
