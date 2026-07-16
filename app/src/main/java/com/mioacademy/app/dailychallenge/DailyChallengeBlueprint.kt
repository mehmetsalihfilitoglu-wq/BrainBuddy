package com.mioacademy.app.dailychallenge

import com.mioacademy.app.core.ExamType
import kotlin.math.floor

/**
 * Official-blueprint section allocation for the 5-question Daily Challenge, using cumulative
 * proportional balancing (deficit method). The blueprint — never bank size, never random — decides
 * how many of the 5 questions come from each section; across days the accumulated mix converges to the
 * exact official exam proportions with bounded (<1) rounding drift.
 *
 * See content/shared_ingestion/daily_challenge_*.md for the validated design.
 */
object DailyChallengeBlueprint {

    const val CHALLENGE_SIZE = 5

    /**
     * Official section weights per exam (section code == QuestionEntity.subject / ExamType blueprint code).
     * IMAT: the "Reading" (4/60) and "Logical reasoning" (5/60) official areas are merged into a single
     * `logic` section (9/60) because the frozen IMAT bank stores both under subject='logic'; the
     * Biology-largest ordering and overall proportions are preserved.
     */
    private val WEIGHTS: Map<ExamType, LinkedHashMap<String, Int>> = mapOf(
        ExamType.IMAT to linkedMapOf("biology" to 23, "chemistry" to 15, "physics_math" to 13, "logic" to 9),
        ExamType.TIL_I to linkedMapOf("math" to 16, "physics" to 10, "logic_reading" to 10, "basic_technical" to 6),
        ExamType.CENT_S to linkedMapOf("math" to 15, "reading_data" to 15, "biology" to 10, "chemistry" to 10, "physics" to 5),
    )

    /** Sub-section rotation weights inside a bundled section (TIL only). Editorial split; Reading floored so it recurs. */
    val SUBSECTIONS: Map<String, LinkedHashMap<String, Int>> = mapOf(
        "logic_reading" to linkedMapOf("logic" to 6, "reading" to 4),
        "basic_technical" to linkedMapOf("computer_science" to 3, "representation" to 2),
    )

    fun isSupported(exam: ExamType): Boolean = WEIGHTS.containsKey(exam)

    fun sections(exam: ExamType): List<String> = WEIGHTS[exam]?.keys?.toList() ?: emptyList()

    private fun full(exam: ExamType): Double = (WEIGHTS[exam]?.values?.sum() ?: 1).toDouble()

    fun perChallengeTarget(exam: ExamType, section: String): Double =
        (WEIGHTS[exam]?.get(section)?.toDouble() ?: 0.0) / full(exam) * CHALLENGE_SIZE

    /**
     * Allocate this challenge's [total] slots across [sections] by the cumulative-deficit rule, mutating
     * the running [expected]/[actual] ledgers. base = floor(perChallengeTarget); the remaining slots go to
     * the sections with the greatest cumulative deficit. Returns section -> count summing to [total].
     */
    fun allocate(
        exam: ExamType,
        expected: MutableMap<String, Double>,
        actual: MutableMap<String, Double>,
        total: Int = CHALLENGE_SIZE,
    ): LinkedHashMap<String, Int> {
        val secs = sections(exam)
        val base = LinkedHashMap<String, Int>()
        for (s in secs) {
            val t = perChallengeTarget(exam, s)
            expected[s] = (expected[s] ?: 0.0) + t
            base[s] = floor(t).toInt()
        }
        var remaining = total - base.values.sum()
        val ranked = secs.sortedByDescending { s -> (expected[s] ?: 0.0) - ((actual[s] ?: 0.0) + base[s]!!) }
        var i = 0
        while (remaining > 0) { val s = ranked[i % ranked.size]; base[s] = base[s]!! + 1; remaining--; i++ }
        for (s in secs) actual[s] = (actual[s] ?: 0.0) + base[s]!!
        return base
    }

    /**
     * Split a bundled section's [count] slots into sub-sections (TIL logic_reading / basic_technical)
     * using the same deficit rule over [SUBSECTIONS] weights and the running [expected]/[actual] ledgers.
     * Returns subSection -> count summing to [count]. If [section] has no sub-sections, returns empty.
     */
    fun allocateSubSections(
        section: String,
        count: Int,
        expected: MutableMap<String, Double>,
        actual: MutableMap<String, Double>,
    ): LinkedHashMap<String, Int> {
        val w = SUBSECTIONS[section] ?: return LinkedHashMap()
        val fullW = w.values.sum().toDouble()
        val subs = w.keys.toList()
        val base = LinkedHashMap<String, Int>()
        for (s in subs) {
            val t = w[s]!!.toDouble() / fullW * count
            expected[s] = (expected[s] ?: 0.0) + t
            base[s] = floor(t).toInt()
        }
        var remaining = count - base.values.sum()
        val ranked = subs.sortedByDescending { s -> (expected[s] ?: 0.0) - ((actual[s] ?: 0.0) + base[s]!!) }
        var i = 0
        while (remaining > 0) { val s = ranked[i % ranked.size]; base[s] = base[s]!! + 1; remaining--; i++ }
        for (s in subs) actual[s] = (actual[s] ?: 0.0) + base[s]!!
        return base
    }
}
