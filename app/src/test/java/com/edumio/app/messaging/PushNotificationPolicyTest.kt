package com.edumio.app.messaging

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationPolicyTest {

    private val P = PushNotificationPolicy

    @Test fun quietHoursWithinSameDay() {
        assertTrue(P.isQuietHours(minuteOfDay = 30, quietStartMin = 0, quietEndMin = 60))
        assertFalse(P.isQuietHours(minuteOfDay = 120, quietStartMin = 0, quietEndMin = 60))
    }

    @Test fun quietHoursWrappingMidnight() {
        // 22:00–08:00
        val start = 22 * 60; val end = 8 * 60
        assertTrue(P.isQuietHours(23 * 60, start, end))   // 23:00
        assertTrue(P.isQuietHours(2 * 60, start, end))    // 02:00
        assertFalse(P.isQuietHours(12 * 60, start, end))  // 12:00
    }

    @Test fun guardsRespectCapAndQuietAndDuplicate() {
        val open = P.passesGuards(true, 0, 12 * 60, 22 * 60, 8 * 60, false)
        assertTrue(open)
        assertFalse("disabled", P.passesGuards(false, 0, 12 * 60, 22 * 60, 8 * 60, false))
        assertFalse("at cap", P.passesGuards(true, 3, 12 * 60, 22 * 60, 8 * 60, false))
        assertFalse("quiet", P.passesGuards(true, 0, 2 * 60, 22 * 60, 8 * 60, false))
        assertFalse("duplicate", P.passesGuards(true, 0, 12 * 60, 22 * 60, 8 * 60, true))
    }

    @Test fun challengeReminderStopsAfterCompletion() {
        assertTrue(P.shouldSendChallengeReminder(guardsPass = true, completedToday = false))
        assertFalse(P.shouldSendChallengeReminder(guardsPass = true, completedToday = true))
        assertFalse(P.shouldSendChallengeReminder(guardsPass = false, completedToday = false))
    }
}
