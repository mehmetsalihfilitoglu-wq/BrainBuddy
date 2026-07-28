package com.edumio.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the v1 notification policy after real-device reminders were observed at 04:02 and 08:10.
 *
 * Policy: ONE reminder a day at 11:30 local, hard window 11:00–20:00, never a catch-up fire, never
 * more than once a day. (This replaces the old three-slot 09:00 / 16:00 / 20:30 policy.)
 */
class DailyChallengeReminderPolicyTest {

    private fun min(h: Int, m: Int = 0) = h * 60 + m

    // ── the observed bad times must be structurally impossible ─────────────────────────────────────

    @Test
    fun the0402Observation_isImpossible() {
        assertFalse("04:02 is outside the allowed window", DailyChallengeReminderPolicy.isWithinAllowedWindow(min(4, 2)))
        assertFalse(
            "must never post at 04:02, even if WorkManager runs the job then",
            DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = true, completedToday = false, alreadyFiredToday = false,
                nowMinuteOfDay = min(4, 2),
            ),
        )
    }

    @Test
    fun the0810Observation_isImpossible() {
        assertFalse(DailyChallengeReminderPolicy.isWithinAllowedWindow(min(8, 10)))
        assertFalse(
            DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = true, completedToday = false, alreadyFiredToday = false,
                nowMinuteOfDay = min(8, 10),
            ),
        )
    }

    @Test
    fun nothingFiresBefore1100() {
        for (m in 0 until min(11, 0)) {
            assertFalse("minute $m must be blocked", DailyChallengeReminderPolicy.isWithinAllowedWindow(m))
        }
    }

    @Test
    fun nothingFiresAfter2000() {
        for (m in min(20, 1) until 24 * 60) {
            assertFalse("minute $m must be blocked", DailyChallengeReminderPolicy.isWithinAllowedWindow(m))
        }
    }

    @Test
    fun theAllowedWindowIsExactlyElevenToTwenty() {
        assertTrue(DailyChallengeReminderPolicy.isWithinAllowedWindow(min(11, 0)))
        assertTrue(DailyChallengeReminderPolicy.isWithinAllowedWindow(min(11, 30)))
        assertTrue(DailyChallengeReminderPolicy.isWithinAllowedWindow(min(20, 0)))
        assertFalse(DailyChallengeReminderPolicy.isWithinAllowedWindow(min(10, 59)))
        assertFalse(DailyChallengeReminderPolicy.isWithinAllowedWindow(min(20, 1)))
    }

    // ── scheduling: today if still ahead, otherwise tomorrow — never instantly ─────────────────────

    @Test
    fun schedulesTodayWhenElevenThirtyIsStillAhead() {
        assertEquals("09:00 → 150 min to 11:30", 150, DailyChallengeReminderPolicy.delayMinutesToNextReminder(min(9, 0)))
        assertEquals("11:29 → 1 min", 1, DailyChallengeReminderPolicy.delayMinutesToNextReminder(min(11, 29)))
    }

    @Test
    fun schedulesTomorrowWhenTodaysTimeHasPassed_neverInstantly() {
        val atNoon = DailyChallengeReminderPolicy.delayMinutesToNextReminder(min(12, 0))
        assertEquals("12:00 → 23h30 until tomorrow 11:30", 23 * 60 + 30, atNoon)
        assertTrue("a passed slot must never resolve to an immediate fire", atNoon > 0)

        val at2300 = DailyChallengeReminderPolicy.delayMinutesToNextReminder(min(23, 0))
        assertEquals(12 * 60 + 30, at2300)
        assertTrue(at2300 > 0)
    }

    @Test
    fun everyPossibleNowResolvesToAStrictlyFutureDelayLandingOnElevenThirty() {
        for (now in 0 until 24 * 60) {
            val d = DailyChallengeReminderPolicy.delayMinutesToNextReminder(now)
            assertTrue("now=$now must be strictly in the future", d > 0)
            assertTrue("now=$now must be within a day", d <= DailyChallengeReminderPolicy.MINUTES_PER_DAY)
            assertEquals(
                "the post time is always 11:30",
                DailyChallengeReminderPolicy.REMINDER_MINUTE,
                (now + d) % DailyChallengeReminderPolicy.MINUTES_PER_DAY,
            )
        }
    }

    @Test
    fun aDeferredOvernightRun_postsNothing_ratherThanCatchingUp() {
        // The device was asleep and WorkManager wakes the job at 03:00: it must post nothing.
        assertFalse(
            DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = true, completedToday = false, alreadyFiredToday = false,
                nowMinuteOfDay = min(3, 0),
            ),
        )
    }

    // ── one per day, and suppression rules ────────────────────────────────────────────────────────

    @Test
    fun onlyOneReminderPerDay() {
        val inWindow = min(11, 30)
        assertTrue(
            DailyChallengeReminderPolicy.shouldFire(true, completedToday = false, alreadyFiredToday = false, nowMinuteOfDay = inWindow),
        )
        assertFalse(
            "a second run the same day must not post again",
            DailyChallengeReminderPolicy.shouldFire(true, completedToday = false, alreadyFiredToday = true, nowMinuteOfDay = inWindow),
        )
    }

    @Test
    fun completingTodaysChallengeSuppressesTodaysReminder() {
        assertFalse(
            DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = true, completedToday = true, alreadyFiredToday = false,
                nowMinuteOfDay = min(11, 30),
            ),
        )
    }

    @Test
    fun notificationsOffSuppressesEverything() {
        assertFalse(
            DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = false, completedToday = false, alreadyFiredToday = false,
                nowMinuteOfDay = min(11, 30),
            ),
        )
    }

    @Test
    fun firesOnlyWhenEnabledNotCompletedNotYetFiredAndInWindow() {
        assertTrue(
            DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = true, completedToday = false, alreadyFiredToday = false,
                nowMinuteOfDay = min(11, 30),
            ),
        )
    }

    @Test
    fun theDefaultReminderTimeIsElevenThirtyAndInsideTheWindow() {
        assertEquals(11 * 60 + 30, DailyChallengeReminderPolicy.REMINDER_MINUTE)
        assertTrue(DailyChallengeReminderPolicy.isWithinAllowedWindow(DailyChallengeReminderPolicy.REMINDER_MINUTE))
    }

    // ── the SECOND (18:30) reminder ───────────────────────────────────────────────────────────────

    @Test
    fun secondReminderIsAtEighteenThirtyAndInsideTheWindow() {
        assertEquals(18 * 60 + 30, DailyChallengeReminderPolicy.SECOND_REMINDER_MINUTE)
        assertTrue(DailyChallengeReminderPolicy.isWithinAllowedWindow(DailyChallengeReminderPolicy.SECOND_REMINDER_MINUTE))
    }

    @Test
    fun atMostTwoRemindersPerDay() {
        assertEquals(2, DailyChallengeReminderPolicy.MAX_REMINDERS_PER_DAY)
    }

    @Test
    fun theTwoRemindersAreSevenHoursApart_neverCloseTogether() {
        val gap = DailyChallengeReminderPolicy.SECOND_REMINDER_MINUTE - DailyChallengeReminderPolicy.REMINDER_MINUTE
        assertEquals(7 * 60, gap)
        assertTrue("reminders must not be bunched together", gap >= 4 * 60)
    }

    @Test
    fun secondReminderSchedulesTodayWhenStillAhead_otherwiseTomorrow() {
        // 09:00 → 9h30 to today's 18:30
        assertEquals(9 * 60 + 30, DailyChallengeReminderPolicy.delayMinutesToSecondReminder(min(9, 0)))
        // 12:00 (first reminder passed, second still ahead) → 6h30 to today's 18:30
        assertEquals(6 * 60 + 30, DailyChallengeReminderPolicy.delayMinutesToSecondReminder(min(12, 0)))
        // 19:00 (both passed) → tomorrow's 18:30, never instant
        val after = DailyChallengeReminderPolicy.delayMinutesToSecondReminder(min(19, 0))
        assertEquals(23 * 60 + 30, after)
        assertTrue(after > 0)
    }

    @Test
    fun everyPossibleNowResolvesTheSecondReminderToAFutureEighteenThirty() {
        for (now in 0 until 24 * 60) {
            val d = DailyChallengeReminderPolicy.delayMinutesToSecondReminder(now)
            assertTrue("now=$now must be strictly future", d > 0)
            assertEquals(
                DailyChallengeReminderPolicy.SECOND_REMINDER_MINUTE,
                (now + d) % DailyChallengeReminderPolicy.MINUTES_PER_DAY,
            )
        }
    }

    @Test
    fun completingAfterTheFirstReminderSuppressesTheSecond() {
        // 18:30, challenge finished during the afternoon → the follow-up must not post.
        assertFalse(
            DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = true, completedToday = true, alreadyFiredToday = false,
                nowMinuteOfDay = DailyChallengeReminderPolicy.SECOND_REMINDER_MINUTE,
            ),
        )
    }

    @Test
    fun secondReminderStillFiresWhenTheChallengeIsStillIncomplete() {
        assertTrue(
            DailyChallengeReminderPolicy.shouldFire(
                notificationsEnabled = true, completedToday = false, alreadyFiredToday = false,
                nowMinuteOfDay = DailyChallengeReminderPolicy.SECOND_REMINDER_MINUTE,
            ),
        )
    }

    @Test
    fun bothRemindersSitInsideTheAllowedWindow_andNothingElseDoes() {
        val both = listOf(
            DailyChallengeReminderPolicy.REMINDER_MINUTE,
            DailyChallengeReminderPolicy.SECOND_REMINDER_MINUTE,
        )
        both.forEach { assertTrue(DailyChallengeReminderPolicy.isWithinAllowedWindow(it)) }
        // The classic bad deliveries remain impossible for either slot.
        listOf(min(4, 2), min(8, 10), min(21, 0), min(23, 59), 0).forEach {
            assertFalse("minute $it must never post", DailyChallengeReminderPolicy.isWithinAllowedWindow(it))
        }
    }
}
