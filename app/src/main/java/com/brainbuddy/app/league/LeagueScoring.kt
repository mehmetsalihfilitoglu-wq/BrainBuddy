package com.brainbuddy.app.league

/**
 * League scoring model:
 * - base = 10
 * - wrong bonus: 0->+30, 1->+22, 2->+15, 3->+9, 4->+4, >=5->+0
 * - blank penalty = blankCount * 1
 * - fail penalty = -15 if wrongCount >= 4 (gate fail)
 * - clamp per test: [-25, +45]
 * - anti-farming: first 10 tests/day full, then 50%
 */
object LeagueScoring {

    private const val BASE = 10
    private const val FAIL_PENALTY = -15
    private const val BLANK_PENALTY_PER = 1
    private const val MAX_TESTS_FULL_POINTS = 10
    private const val ANTI_FARMING_MULTIPLIER = 0.5f
    private const val MIN_POINTS = -25
    private const val MAX_POINTS = 45

    private val WRONG_BONUS = mapOf(
        0 to 30, 1 to 22, 2 to 15, 3 to 9, 4 to 4
    )

    /** Compute raw points for a single test (before anti-farming). */
    fun computeTestPoints(
        wrongCount: Int,
        blankCount: Int,
        isGateFail: Boolean
    ): Int {
        val wrongBonus = WRONG_BONUS[wrongCount] ?: 0
        val blankPenalty = blankCount * BLANK_PENALTY_PER
        val failPenalty = if (isGateFail) FAIL_PENALTY else 0
        return (BASE + wrongBonus - blankPenalty + failPenalty).coerceIn(MIN_POINTS, MAX_POINTS)
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
        testIndexOfDay: Int
    ): Int {
        val raw = computeTestPoints(wrongCount, blankCount, isGateFail)
        return applyAntiFarming(raw, testIndexOfDay)
    }

    /** Points breakdown for parent analytics. */
    data class PointsBreakdown(
        val base: Int,
        val wrongBonus: Int,
        val blankPenalty: Int,
        val failPenalty: Int,
        val rawTotal: Int,
        val antiFarmingMultiplier: Float,
        val finalPoints: Int,
        val wrongCount: Int,
        val blankCount: Int,
        val isGateFail: Boolean,
        val testIndexOfDay: Int
    )

    fun computeBreakdown(
        wrongCount: Int,
        blankCount: Int,
        isGateFail: Boolean,
        testIndexOfDay: Int
    ): PointsBreakdown {
        val wrongBonus = WRONG_BONUS[wrongCount] ?: 0
        val blankPenalty = blankCount * BLANK_PENALTY_PER
        val failPenalty = if (isGateFail) FAIL_PENALTY else 0
        val rawTotal = (BASE + wrongBonus - blankPenalty + failPenalty).coerceIn(MIN_POINTS, MAX_POINTS)
        val mult = if (testIndexOfDay < MAX_TESTS_FULL_POINTS) 1f else ANTI_FARMING_MULTIPLIER
        val finalPoints = (rawTotal * mult).toInt()
        return PointsBreakdown(
            base = BASE,
            wrongBonus = wrongBonus,
            blankPenalty = blankPenalty,
            failPenalty = failPenalty,
            rawTotal = rawTotal,
            antiFarmingMultiplier = mult,
            finalPoints = finalPoints,
            wrongCount = wrongCount,
            blankCount = blankCount,
            isGateFail = isGateFail,
            testIndexOfDay = testIndexOfDay
        )
    }
}
