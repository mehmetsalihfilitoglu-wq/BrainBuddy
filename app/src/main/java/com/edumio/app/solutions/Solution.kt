package com.edumio.app.solutions

import org.json.JSONObject

/**
 * One verified, student-facing solution record (see
 * content/shared_ingestion/premium_solution_architecture.md). Loaded from the per-exam
 * `solutions.json` asset overlay — never from the question banks, which stay untouched.
 *
 * [correctOption] indexes the bank's ORIGINAL choice order; the UI maps through its display
 * shuffle. Text fields never reference options by letter for exactly that reason.
 * Internal verification metadata is stripped before shipping, so this model has none.
 */
data class Solution(
    val questionId: String,
    val correctOption: Int,
    val shortExplanation: String,
    val solutionSteps: List<String>,
    val keyConcept: String,
    val commonMistake: String,
    /** original-option-index -> why that distractor is wrong (main distractors only). */
    val optionExplanations: Map<Int, String>,
    val figureExplanation: String?,
    val formulaNotes: String?,
    val solutionVersion: Int,
) {
    companion object {
        fun fromJson(o: JSONObject): Solution? {
            val id = o.optString("questionId").takeIf { it.isNotBlank() } ?: return null
            val steps = o.optJSONArray("solutionSteps") ?: return null
            val optionExpl = LinkedHashMap<Int, String>()
            o.optJSONObject("optionExplanations")?.let { oe ->
                for (k in oe.keys()) k.toIntOrNull()?.let { idx -> optionExpl[idx] = oe.optString(k) }
            }
            return Solution(
                questionId = id,
                correctOption = o.optInt("correctOption", -1),
                shortExplanation = o.optString("shortExplanation"),
                solutionSteps = (0 until steps.length()).map { steps.optString(it) }.filter { it.isNotBlank() },
                keyConcept = o.optString("keyConcept"),
                commonMistake = o.optString("commonMistake"),
                optionExplanations = optionExpl,
                figureExplanation = o.optString("figureExplanation").takeIf { it.isNotBlank() },
                formulaNotes = o.optString("formulaNotes").takeIf { it.isNotBlank() },
                solutionVersion = o.optInt("solutionVersion", 1),
            )
        }
    }
}
