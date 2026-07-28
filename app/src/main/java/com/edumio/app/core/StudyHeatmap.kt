package com.edumio.app.core

import android.content.Context
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * A GitHub-style contribution calendar built purely from real study sessions of
 * the active study area. Shows which days a student actually practised, their
 * longest consistency streak, and how consistent the current month has been.
 * Nothing is fabricated — an untouched day is simply empty (level 0).
 */
object StudyHeatmap {

    /** One calendar cell. [count] = questions answered that day; [level] 0..4 for shading. */
    data class Day(val epochDay: Long, val count: Int, val level: Int)

    /**
     * @param weeks columns of the calendar; each week is 7 cells (Mon..Sun), null = outside range/future.
     * @param longestStreak longest run of consecutive active days ever recorded (in the window).
     * @param activeDaysThisMonth distinct days studied in the current calendar month.
     * @param activeDaysTotal distinct active days in the whole window.
     */
    data class Data(
        val weeks: List<List<Day?>>,
        val longestStreak: Int,
        val activeDaysThisMonth: Int,
        val activeDaysTotal: Int
    )

    private const val DEFAULT_WEEKS = 16

    fun compute(context: Context, numWeeks: Int = DEFAULT_WEEKS): Data {
        val sessions = AnalyticsStore(context).getSessions()
        // Questions answered per (UTC) day.
        val perDay = HashMap<Long, Int>()
        for (s in sessions) {
            val day = TimeUnit.MILLISECONDS.toDays(s.tsMs)
            perDay[day] = (perDay[day] ?: 0) + s.total
        }

        val today = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis())
        // Align the last column to the week (Mon..Sun) containing today.
        val lastWeekMonday = today - dowMonday(today)
        val start = lastWeekMonday - (numWeeks - 1) * 7L

        val weeks = ArrayList<List<Day?>>(numWeeks)
        for (w in 0 until numWeeks) {
            val col = ArrayList<Day?>(7)
            for (row in 0 until 7) {
                val epochDay = start + w * 7L + row
                col.add(
                    if (epochDay > today) null
                    else {
                        val count = perDay[epochDay] ?: 0
                        Day(epochDay, count, levelFor(count))
                    }
                )
            }
            weeks.add(col)
        }

        val activeDays = perDay.filterValues { it > 0 }.keys.sorted()
        return Data(
            weeks = weeks,
            longestStreak = longestConsecutive(activeDays),
            activeDaysThisMonth = activeDays.count { it >= currentMonthStartDay() },
            activeDaysTotal = activeDays.size
        )
    }

    /** Monday = 0 … Sunday = 6. epochDay 0 (1970-01-01) is a Thursday. */
    private fun dowMonday(epochDay: Long): Int = (((epochDay % 7) + 3) % 7).toInt()

    private fun levelFor(count: Int): Int = when {
        count <= 0 -> 0
        count < 5 -> 1
        count < 10 -> 2
        count < 20 -> 3
        else -> 4
    }

    private fun longestConsecutive(sortedDays: List<Long>): Int {
        if (sortedDays.isEmpty()) return 0
        var best = 1
        var run = 1
        for (i in 1 until sortedDays.size) {
            run = if (sortedDays[i] == sortedDays[i - 1] + 1) run + 1 else 1
            if (run > best) best = run
        }
        return best
    }

    private fun currentMonthStartDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return TimeUnit.MILLISECONDS.toDays(cal.timeInMillis)
    }
}
