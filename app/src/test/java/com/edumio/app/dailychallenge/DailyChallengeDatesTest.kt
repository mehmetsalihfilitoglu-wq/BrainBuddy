package com.edumio.app.dailychallenge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Release-critical: timezone / DST / day-boundary date math (pure). */
class DailyChallengeDatesTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val ny = TimeZone.getTimeZone("America/New_York")
    private val H = 3_600_000L
    private val DAY = 86_400_000L

    @Test
    fun localDateWalksTheMidnightBoundary() {
        val start = DailyChallengeDates.startOfLocalDay("2026-05-10", utc)
        assertEquals("2026-05-10", DailyChallengeDates.localDate(utc, start))                 // 00:00
        assertEquals("2026-05-10", DailyChallengeDates.localDate(utc, start + DAY - 1000))    // 23:59:59
        assertEquals("2026-05-11", DailyChallengeDates.localDate(utc, start + DAY))           // next 00:00
    }

    @Test
    fun sameInstantIsDifferentDateEastVsWest() {
        val instant = DailyChallengeDates.startOfLocalDay("2026-01-01", utc) // 2026-01-01T00:00Z
        val east = DailyChallengeDates.localDate(TimeZone.getTimeZone("Etc/GMT-14"), instant) // UTC+14
        val west = DailyChallengeDates.localDate(TimeZone.getTimeZone("Etc/GMT+10"), instant) // UTC-10
        assertEquals("2026-01-01", east)
        assertEquals("2025-12-31", west)
        assertTrue(east > west)
    }

    @Test
    fun endOfLocalDayIsDstAware_23hAnd25hDays() {
        fun durationMs(d: String) =
            DailyChallengeDates.endOfLocalDay(d, ny) - DailyChallengeDates.startOfLocalDay(d, ny)
        assertEquals("normal day is 24h", 24 * H, durationMs("2026-06-15"))
        assertEquals("US spring-forward day is 23h", 23 * H, durationMs("2026-03-08"))
        assertEquals("US fall-back day is 25h", 25 * H, durationMs("2026-11-01"))
    }

    @Test
    fun boundaryHoldsAcrossTheDstTransition() {
        val end = DailyChallengeDates.endOfLocalDay("2026-03-08", ny) // next local midnight
        assertEquals("2026-03-08", DailyChallengeDates.localDate(ny, end - 1000))
        assertEquals("2026-03-09", DailyChallengeDates.localDate(ny, end))
    }

    @Test
    fun shiftDateIsCalendarCorrectAndDstNeutral() {
        assertEquals("2026-03-01", DailyChallengeDates.shiftDate("2026-02-28", 1)) // 2026 not leap
        assertEquals("2025-01-01", DailyChallengeDates.shiftDate("2024-12-31", 1)) // year rollover
        assertEquals("2024-02-29", DailyChallengeDates.shiftDate("2024-03-01", -1)) // leap day
        assertEquals("2026-03-09", DailyChallengeDates.shiftDate("2026-03-08", 1)) // DST day → label unaffected
        assertEquals("2026-03-07", DailyChallengeDates.shiftDate("2026-03-08", -1))
    }

    @Test
    fun isSameLocalDayRespectsZone() {
        val a = DailyChallengeDates.startOfLocalDay("2026-05-10", utc)
        assertTrue(DailyChallengeDates.isSameLocalDay(a, a + 3 * H, utc))
        assertFalse(DailyChallengeDates.isSameLocalDay(a, a + DAY, utc))
    }
}
