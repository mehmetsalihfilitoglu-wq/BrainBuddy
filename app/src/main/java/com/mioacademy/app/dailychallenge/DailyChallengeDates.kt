package com.mioacademy.app.dailychallenge

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure, timezone-aware date math for the Daily Challenge (Calendar-based → safe on minSdk 24, no
 * java.time desugaring). Extracted from the engine so the release-critical day-boundary / DST /
 * streak-date logic can be unit-tested with an explicit zone and clock.
 *
 *  - [localDate] / [endOfLocalDay] use the user's real (DST-aware) timezone.
 *  - [shiftDate] does pure calendar-date arithmetic in UTC so a ±1 day shift can never be skewed by a
 *    DST transition in the local zone (it operates on the Y-M-D label, not a wall-clock instant).
 */
object DailyChallengeDates {

    /** Local calendar date (YYYY-MM-DD) at [nowMs] in [zone]. */
    fun localDate(zone: TimeZone = TimeZone.getDefault(), nowMs: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance(zone); c.timeInMillis = nowMs
        return format(c)
    }

    /** Absolute millis at the end of [localDate] (its next local midnight) in [zone] — DST-aware. */
    fun endOfLocalDay(localDate: String, zone: TimeZone = TimeZone.getDefault()): Long {
        val p = parse(localDate)
        val c = Calendar.getInstance(zone); c.clear(); c.set(p[0], p[1] - 1, p[2], 0, 0, 0)
        c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /** Absolute millis at the start of [localDate] (its local midnight) in [zone]. */
    fun startOfLocalDay(localDate: String, zone: TimeZone = TimeZone.getDefault()): Long {
        val p = parse(localDate)
        val c = Calendar.getInstance(zone); c.clear(); c.set(p[0], p[1] - 1, p[2], 0, 0, 0)
        return c.timeInMillis
    }

    /** Shift a YYYY-MM-DD label by [days], DST-neutral (UTC calendar arithmetic on the date only). */
    fun shiftDate(localDate: String, days: Int): String {
        val p = parse(localDate)
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")); c.clear(); c.set(p[0], p[1] - 1, p[2])
        c.add(Calendar.DAY_OF_MONTH, days)
        return format(c)
    }

    /** True if [aMs] and [bMs] fall on the same local calendar date in [zone]. */
    fun isSameLocalDay(aMs: Long, bMs: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        localDate(zone, aMs) == localDate(zone, bMs)

    private fun parse(localDate: String): IntArray {
        val parts = localDate.split("-")
        return intArrayOf(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }

    private fun format(c: Calendar): String = String.format(
        "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
    )
}
