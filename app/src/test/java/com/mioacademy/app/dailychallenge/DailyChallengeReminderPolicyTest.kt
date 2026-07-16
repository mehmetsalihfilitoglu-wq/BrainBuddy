package com.mioacademy.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure JVM tests for the Daily Challenge reminder scheduling policy (no Android deps). */
class DailyChallengeReminderPolicyTest {

    @Test
    fun threeSlotsAt0900_1600_2030InOrder() {
        assertEquals(3, DailyChallengeReminderPolicy.slotCount())
        assertEquals(listOf(540, 960, 1230), DailyChallengeReminderPolicy.SLOT_MINUTES.toList())
        val sorted = DailyChallengeReminderPolicy.SLOT_MINUTES.toList().sorted()
        assertEquals("slots must be ascending", sorted, DailyChallengeReminderPolicy.SLOT_MINUTES.toList())
    }

    @Test
    fun delayToUpcomingSlotIsSameDay() {
        // now 08:00 (480). 09:00 slot is 60 min away.
        assertEquals(60, DailyChallengeReminderPolicy.delayMinutesToSlot(540, 480))
        // now 08:00, 16:00 slot is 480 min away.
        assertEquals(480, DailyChallengeReminderPolicy.delayMinutesToSlot(960, 480))
    }

    @Test
    fun delayToPassedSlotWrapsToTomorrow() {
        // now 10:00 (600). 09:00 slot already passed → 23h to tomorrow's 09:00.
        assertEquals(540 - 600 + 1440, DailyChallengeReminderPolicy.delayMinutesToSlot(540, 600))
        // exactly at the slot → schedule for tomorrow (never negative, never zero).
        assertEquals(1440, DailyChallengeReminderPolicy.delayMinutesToSlot(540, 540))
        assertTrue(DailyChallengeReminderPolicy.delayMinutesToSlot(540, 600) > 0)
    }

    @Test
    fun remainingSlotsExcludePastTimes() {
        assertEquals(listOf(540, 960, 1230), DailyChallengeReminderPolicy.remainingSlotsToday(0))
        assertEquals(listOf(960, 1230), DailyChallengeReminderPolicy.remainingSlotsToday(600)) // after 09:00
        assertEquals(listOf(1230), DailyChallengeReminderPolicy.remainingSlotsToday(1000)) // after 16:00
        assertTrue(DailyChallengeReminderPolicy.remainingSlotsToday(1300).isEmpty()) // after 20:30
    }

    @Test
    fun completionSuppressesAllRemainingReminders() {
        // once completed today, no slot fires regardless of enabled/fired state
        assertFalse(DailyChallengeReminderPolicy.shouldFire(notificationsEnabled = true, completedToday = true, alreadyFiredThisSlotToday = false))
    }

    @Test
    fun disabledNotificationsNeverFire() {
        assertFalse(DailyChallengeReminderPolicy.shouldFire(notificationsEnabled = false, completedToday = false, alreadyFiredThisSlotToday = false))
    }

    @Test
    fun alreadyFiredSlotIsIdempotent() {
        assertFalse(DailyChallengeReminderPolicy.shouldFire(notificationsEnabled = true, completedToday = false, alreadyFiredThisSlotToday = true))
    }

    @Test
    fun firesOnlyWhenEnabledNotCompletedNotYetFired() {
        assertTrue(DailyChallengeReminderPolicy.shouldFire(notificationsEnabled = true, completedToday = false, alreadyFiredThisSlotToday = false))
    }
}
