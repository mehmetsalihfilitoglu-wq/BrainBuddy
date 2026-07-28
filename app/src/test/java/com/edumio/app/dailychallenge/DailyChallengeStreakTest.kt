package com.edumio.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release-critical: streak transitions across consecutive / same-day / gap / DST boundaries (pure). */
class DailyChallengeStreakTest {

    private fun onComplete(cur: Int, long: Int, last: String, csv: String, today: String) =
        DailyChallengeStreak.onComplete(cur, long, last, csv, today)

    @Test
    fun firstEverCompletionStartsAtOne() {
        val r = onComplete(0, 0, "", "", "2026-05-10")
        assertEquals(1, r.current); assertEquals(1, r.longest)
        assertTrue(r.incremented)
    }

    @Test
    fun consecutiveDayExtendsStreak() {
        val r = onComplete(1, 1, "2026-05-09", "", "2026-05-10")
        assertEquals(2, r.current); assertEquals(2, r.longest); assertTrue(r.incremented)
    }

    @Test
    fun sameDayCompletionIsIdempotent() {
        val r = onComplete(4, 6, "2026-05-10", "3", "2026-05-10")
        assertEquals("no double increment", 4, r.current)
        assertEquals(6, r.longest)
        assertFalse("same-day must not re-increment", r.incremented)
        assertTrue("no new milestone on a same-day repeat", r.newMilestones.isEmpty())
    }

    @Test
    fun oneMissedDayResetsToOne() {
        val r = onComplete(5, 5, "2026-05-08", "3", "2026-05-10") // gap: 05-08 -> 05-10
        assertEquals(1, r.current)
        assertTrue(r.incremented)
        assertEquals("longest is preserved across a break", 5, r.longest)
    }

    @Test
    fun manyMissedDaysAlsoResetToOne_noAccumulation() {
        val r = onComplete(9, 9, "2026-04-01", "3,7", "2026-05-10")
        assertEquals(1, r.current)
        assertEquals(9, r.longest)
    }

    @Test
    fun milestoneFiresExactlyOnce() {
        val hit = onComplete(2, 2, "2026-05-09", "", "2026-05-10") // reaches 3
        assertEquals(3, hit.current)
        assertEquals(listOf(3), hit.newMilestones)
        assertTrue(hit.milestonesCsv.split(",").contains("3"))
        // Reaching 3 again with 3 already recorded must not re-fire.
        val again = onComplete(2, 3, "2026-05-09", "3", "2026-05-10")
        assertEquals(3, again.current)
        assertTrue("milestone 3 must not re-fire", again.newMilestones.isEmpty())
    }

    @Test
    fun streakSurvivesDstDayBoundary() {
        // 2026-03-08 (US spring-forward, 23h) -> 2026-03-09 is still one consecutive calendar step.
        val r = onComplete(1, 1, "2026-03-08", "", "2026-03-09")
        assertEquals("DST 23h day must not break the streak", 2, r.current)
        assertTrue(r.incremented)
    }

    @Test
    fun higherMilestonesAccumulateInCsvWithoutLosingEarlierOnes() {
        val r = onComplete(6, 6, "2026-05-09", "3", "2026-05-10") // reaches 7
        assertEquals(7, r.current)
        assertEquals(listOf(7), r.newMilestones)
        val set = r.milestonesCsv.split(",").toSet()
        assertTrue(set.contains("3") && set.contains("7"))
    }
}
