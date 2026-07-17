package com.edumio.app.dailychallenge

import kotlin.math.max

/**
 * Pure streak-transition logic (no Android/DB deps → testable). Extracted from the engine so the
 * release-critical streak rules — consecutive days increment, same-day is idempotent, any gap resets
 * to 1, milestones fire exactly once — can be exercised across date/timezone/DST boundaries.
 *
 * "Consecutive" is defined purely on the YYYY-MM-DD label via [DailyChallengeDates.shiftDate], so a
 * 23- or 25-hour DST day still counts as exactly one calendar step.
 */
object DailyChallengeStreak {

    val MILESTONES = setOf(3, 7, 14, 30, 50, 100, 180, 365)

    data class Result(
        val current: Int,
        val longest: Int,
        val incremented: Boolean,
        val newMilestones: List<Int>,
        val lastCompletedLocalDate: String,
        val milestonesCsv: String,
    )

    /**
     * Compute the streak after a completion on [todayLocalDate].
     * @param prevLastDate the previously recorded completed date (""/blank if never).
     */
    fun onComplete(
        prevCurrent: Int,
        prevLongest: Int,
        prevLastDate: String,
        prevMilestonesCsv: String,
        todayLocalDate: String,
    ): Result {
        val yesterday = DailyChallengeDates.shiftDate(todayLocalDate, -1)
        val current = when (prevLastDate) {
            todayLocalDate -> prevCurrent          // already counted today → no change
            yesterday -> prevCurrent + 1           // consecutive day → extend
            else -> 1                              // never, or a gap of ≥1 day → fresh streak
        }
        val incremented = prevLastDate != todayLocalDate

        val reached = prevMilestonesCsv.split(",").filter { it.isNotBlank() }.toMutableSet()
        val newMilestones = ArrayList<Int>()
        if (current in MILESTONES && current.toString() !in reached) {
            reached += current.toString()
            newMilestones += current
        }
        return Result(
            current = current,
            longest = max(prevLongest, current),
            incremented = incremented,
            newMilestones = newMilestones,
            lastCompletedLocalDate = todayLocalDate,
            milestonesCsv = reached.joinToString(","),
        )
    }
}
