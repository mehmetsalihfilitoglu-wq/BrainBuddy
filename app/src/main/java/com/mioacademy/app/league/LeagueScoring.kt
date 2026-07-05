package com.mioacademy.app.league

/**
 * League scoring model:
 * - base = 10
 * - wrong bonus: 0->+30, 1->+22, 2->+15, 3->+9, 4->+4, >=5->+0
 * - streak bonus: 0-1d->0, 2d->+1, 3d->+2, 5d->+3, 7d+->+5
 * - blank penalty = blankCount * 1
 * - fail penalty = -15 if wrongCount >= 4 (gate fail)
 * - clamp per test: [-25, +50]
 * - anti-farming: first 10 tests/day full, then 50%
 */
object LeagueScoring {

    private const val BASE = 10
    private const val FAIL_PENALTY = -15
    private const val BLANK_PENALTY_PER = 1
    private const val MAX_TESTS_FULL_POINTS = 10
    private const val ANTI_FARMING_MULTIPLIER = 0.5f
    private const val MIN_POINTS = -25
    private const val MAX_POINTS = 50

    private val WRONG_BONUS = mapOf(
        0 to 30, 1 to 22, 2 to 15, 3 to 9, 4 to 4
    )

    private val STREAK_BONUS = listOf(
        7 to 5, 5 to 3, 3 to 2, 2 to 1
    )

    fun computeStreakBonus(streakDays: Int): Int {
        for ((threshold, bonus) in STREAK_BONUS) {
            if (streakDays >= threshold) return bonus
        }
        return 0
    }

    /** Compute raw points for a single test (before anti-farming). */
    fun computeTestPoints(
        wrongCount: Int,
        blankCount: Int,
        isGateFail: Boolean,
        streakDays: Int = 0
    ): Int {
        val wrongBonus = WRONG_BONUS[wrongCount] ?: 0
        val blankPenalty = blankCount * BLANK_PENALTY_PER
        val failPenalty = if (isGateFail) FAIL_PENALTY else 0
        val streakBonus = computeStreakBonus(streakDays)
        return (BASE + wrongBonus + streakBonus - blankPenalty + failPenalty).coerceIn(MIN_POINTS, MAX_POINTS)
    }

    /** Apply anti-farming: tests beyond N per day get 50% points. */
    fun applyAntiFarming(rawPoints: Int, testIndexOfDay: Int): Int {
        return if (testIndexOfDay < MAX_TESTS_FULL_POINTS) {
            rawPoints
        } else {
            (rawPoints * ANTI_FARMING_MULTIPLIER).toInt()
        }
    }

    /** Full computation: raw points + anti-farming. */
    fun computeLeaguePoints(
        wrongCount: Int,
        blankCount: Int,
        isGateFail: Boolean,
        testIndexOfDay: Int,
        streakDays: Int = 0
    ): Int {
        val raw = computeTestPoints(wrongCount, blankCount, isGateFail, streakDays)
        return applyAntiFarming(raw, testIndexOfDay)
    }

    /** Points breakdown for parent analytics. */
    data class PointsBreakdown(
        val base: Int,
        val wrongBonus: Int,
        val streakBonus: Int,
        val blankPenalty: Int,
        val failPenalty: Int,
        val rawTotal: Int,
        val antiFarmingMultiplier: Float,
        val finalPoints: Int,
        val wrongCount: Int,
        val blankCount: Int,
        val isGateFail: Boolean,
        val testIndexOfDay: Int,
        val streakDays: Int
    )

    fun computeBreakdown(
        wrongCount: Int,
        blankCount: Int,
        isGateFail: Boolean,
        testIndexOfDay: Int,
        streakDays: Int = 0
    ): PointsBreakdown {
        val wrongBonus = WRONG_BONUS[wrongCount] ?: 0
        val blankPenalty = blankCount * BLANK_PENALTY_PER
        val failPenalty = if (isGateFail) FAIL_PENALTY else 0
        val streakBonus = computeStreakBonus(streakDays)
        val rawTotal = (BASE + wrongBonus + streakBonus - blankPenalty + failPenalty).coerceIn(MIN_POINTS, MAX_POINTS)
        val mult = if (testIndexOfDay < MAX_TESTS_FULL_POINTS) 1f else ANTI_FARMING_MULTIPLIER
        val finalPoints = (rawTotal * mult).toInt()
        return PointsBreakdown(
            base = BASE,
            wrongBonus = wrongBonus,
            streakBonus = streakBonus,
            blankPenalty = blankPenalty,
            failPenalty = failPenalty,
            rawTotal = rawTotal,
            antiFarmingMultiplier = mult,
            finalPoints = finalPoints,
            wrongCount = wrongCount,
            blankCount = blankCount,
            isGateFail = isGateFail,
            testIndexOfDay = testIndexOfDay,
            streakDays = streakDays
        )
    }
}
