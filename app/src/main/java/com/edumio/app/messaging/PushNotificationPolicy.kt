package com.edumio.app.messaging

/**
 * Pure eligibility rules for SERVER (FCM) pushes, complementing the local `DailyChallengeReminderPolicy`.
 * Tested so the "≤3/day, respect quiet hours, no duplicates, stop reminders after completion" guarantees
 * hold regardless of who triggers the send.
 */
object PushNotificationPolicy {

    const val MAX_PUSH_PER_DAY = 3

    /** Quiet-hours window as minute-of-day; supports windows that wrap past midnight (e.g. 22:00–08:00). */
    fun isQuietHours(minuteOfDay: Int, quietStartMin: Int, quietEndMin: Int): Boolean =
        if (quietStartMin == quietEndMin) false
        else if (quietStartMin < quietEndMin) minuteOfDay in quietStartMin until quietEndMin
        else minuteOfDay >= quietStartMin || minuteOfDay < quietEndMin

    /** Global guards for ANY push: enabled, under the daily cap, outside quiet hours, not a duplicate. */
    fun passesGuards(
        enabled: Boolean,
        sentTodayCount: Int,
        minuteOfDay: Int,
        quietStartMin: Int,
        quietEndMin: Int,
        isDuplicate: Boolean,
    ): Boolean = enabled &&
        sentTodayCount < MAX_PUSH_PER_DAY &&
        !isQuietHours(minuteOfDay, quietStartMin, quietEndMin) &&
        !isDuplicate

    /** A Daily-Challenge reminder additionally stops once today's challenge is completed. */
    fun shouldSendChallengeReminder(guardsPass: Boolean, completedToday: Boolean): Boolean =
        guardsPass && !completedToday
}
