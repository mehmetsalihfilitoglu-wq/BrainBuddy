package com.brainbuddy.app.quiz

import com.brainbuddy.app.db.QuestionStemHash
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reusable validator for MAT question packs and individual questions.
 * Validates schema compliance and quality rules before import.
 *
 * Schema: root { version, mode, subject, publisher, questions }
 * Question: { difficulty, topic, skills, questionType, stem, options, answerIndex,
 *             explanation, imageAsset, source, sourceRef }
 */
object MatQuestionValidator {

    /** Rejection reason codes for logging and reporting. */
    object RejectionReason {
        const val STEM_BLANK = "stem_blank"
        const val STEM_TOO_SHORT = "stem_too_short"
        const val STEM_TRIVIAL_SINGLE_LINE = "stem_trivial_single_line"
        const val OPTIONS_COUNT_NOT_4 = "options_count_not_4"
        const val OPTION_BLANK = "option_blank"
        const val ANSWER_INDEX_OUT_OF_RANGE = "answer_index_out_of_range"
        const val TOPIC_BLANK = "topic_blank"
        const val DIFFICULTY_INVALID = "difficulty_invalid"
        const val QUESTION_TYPE_BLANK = "question_type_blank"
        const val SKILLS_MISSING_OR_EMPTY = "skills_missing_or_empty"
        const val EXPLANATION_FIELD_MISSING = "explanation_field_missing"
        const val SOURCE_MISSING = "source_missing"
        const val SOURCE_REF_MISSING = "source_ref_missing"
        const val DUPLICATE_OPTIONS = "duplicate_options"
        const val DUPLICATE_STEM_IN_PACK = "duplicate_stem_in_pack"
    }

    private const val MIN_STEM_LENGTH = 40
    private const val TRIVIAL_STEM_MAX_LENGTH = 60
    private const val VALID_DIFFICULTY_MIN = 0
    private const val VALID_DIFFICULTY_MAX = 2

    data class ValidationResult(
        val isValid: Boolean,
        val reasons: List<String>
    ) {
        val summary: String get() = reasons.joinToString(", ")
    }

    data class PackValidationResult(
        val isValid: Boolean,
        val packReasons: List<String>,
        val questionResults: List<Pair<Int, ValidationResult>>
    ) {
        val totalQuestions: Int get() = questionResults.size
        val validCount: Int get() = questionResults.count { it.second.isValid }
        val rejectedCount: Int get() = questionResults.count { !it.second.isValid }
        val rejectedReasons: Map<String, Int> get() =
            questionResults.flatMap { (_, r) -> r.reasons }.groupingBy { it }.eachCount()
    }

    data class ValidationReport(
        val packName: String?,
        val totalQuestions: Int,
        val validCount: Int,
        val rejectedCount: Int,
        val reasonsByCount: Map<String, Int>,
        val perQuestionRejections: List<Pair<Int, String>>
    ) {
        fun formatForLog(): String = buildString {
            appendLine("MAT validation report: $packName")
            appendLine("  Total questions: $totalQuestions")
            appendLine("  Valid: $validCount | Rejected: $rejectedCount")
            if (reasonsByCount.isNotEmpty()) {
                appendLine("  Rejection reasons:")
                reasonsByCount.entries.sortedByDescending { it.value }.forEach { (reason, count) ->
                    appendLine("    $reason: $count")
                }
            }
            if (perQuestionRejections.isNotEmpty()) {
                appendLine("  Per-question rejections:")
                perQuestionRejections.forEach { (idx, reasons) ->
                    appendLine("    [q$idx] $reasons")
                }
            }
        }
    }

    /**
     * Validates an entire MAT pack. Checks pack-level rules (empty questions)
     * and per-question validation including duplicate stems within pack.
     * @param packName Optional pack identifier for report context (passed to [buildReport] by caller).
     */
    @Suppress("UNUSED_PARAMETER")
    fun validatePack(root: JSONObject, packName: String? = null): PackValidationResult {
        val questionsArr = root.optJSONArray("questions")
        if (questionsArr == null || questionsArr.length() == 0) {
            return PackValidationResult(
                isValid = false,
                packReasons = listOf("pack_empty_questions"),
                questionResults = emptyList()
            )
        }

        val seenNormalizedStems = mutableSetOf<String>()
        val questionResults = mutableListOf<Pair<Int, ValidationResult>>()

        for (i in 0 until questionsArr.length()) {
            val qObj = questionsArr.optJSONObject(i)
            if (qObj == null) {
                questionResults.add(i to ValidationResult(false, listOf("question_null")))
                continue
            }
            val result = validateQuestion(qObj, seenNormalizedStems)
            questionResults.add(i to result)
            val stem = qObj.optString("stem", "").trim()
            if (stem.isNotBlank()) {
                seenNormalizedStems.add(QuestionStemHash.normalizeStem(stem))
            }
        }

        val packReasons = mutableListOf<String>()
        if (questionResults.isEmpty()) packReasons.add("pack_empty_questions")

        return PackValidationResult(
            isValid = packReasons.isEmpty() && questionResults.any { it.second.isValid },
            packReasons = packReasons,
            questionResults = questionResults
        )
    }

    /**
     * Validates a single question. If [seenNormalizedStems] is provided and the
     * stem normalizes to an already-seen value, adds DUPLICATE_STEM_IN_PACK.
     * Caller is responsible for adding valid stems to seenNormalizedStems.
     */
    fun validateQuestion(q: JSONObject, seenNormalizedStems: MutableSet<String>? = null): ValidationResult {
        val reasons = mutableListOf<String>()

        // stem not blank
        val stem = q.optString("stem", "").trim()
        if (stem.isBlank()) {
            reasons.add(RejectionReason.STEM_BLANK)
            return ValidationResult(false, reasons)
        }

        // very short stem
        if (stem.length < MIN_STEM_LENGTH) {
            reasons.add(RejectionReason.STEM_TOO_SHORT)
        }

        // single-line trivial prompt (likely short_item)
        if (stem.length <= TRIVIAL_STEM_MAX_LENGTH && !stem.contains("\n") && stem.split(Regex("[.!?]")).none { it.trim().length > 50 }) {
            val words = stem.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size <= 8) {
                reasons.add(RejectionReason.STEM_TRIVIAL_SINGLE_LINE)
            }
        }

        // options count exactly 4
        val optionsArr = q.optJSONArray("options") ?: q.optJSONArray("choices")
        if (optionsArr == null || optionsArr.length() != 4) {
            reasons.add(RejectionReason.OPTIONS_COUNT_NOT_4)
        } else {
            // options all non-blank
            for (j in 0 until 4) {
                val opt = optionsArr.optString(j, "").trim()
                if (opt.isBlank()) {
                    reasons.add(RejectionReason.OPTION_BLANK)
                    break
                }
            }
            // duplicate options
            val opts = (0 until 4).map { optionsArr.optString(it, "").trim() }
            if (opts.size != opts.distinct().size) {
                reasons.add(RejectionReason.DUPLICATE_OPTIONS)
            }
        }

        // answerIndex in 0..3
        val answerIndex = q.optInt("answerIndex", -1).takeIf { q.has("answerIndex") }
            ?: q.optInt("correctIndex", -1).takeIf { q.has("correctIndex") }
            ?: -1
        if (answerIndex !in 0..3) {
            reasons.add(RejectionReason.ANSWER_INDEX_OUT_OF_RANGE)
        }

        // topic not blank
        val topic = q.optString("topic", "").trim()
        if (topic.isBlank()) reasons.add(RejectionReason.TOPIC_BLANK)

        // difficulty valid (0, 1, 2)
        val difficulty = when (val d = q.opt("difficulty")) {
            is Int -> if (d in VALID_DIFFICULTY_MIN..VALID_DIFFICULTY_MAX) d else null
            is String -> when (d.uppercase()) {
                "EASY" -> 0
                "MEDIUM", "MED" -> 1
                "HARD", "VERY_HARD" -> 2
                else -> null
            }
            else -> null
        }
        if (difficulty == null) reasons.add(RejectionReason.DIFFICULTY_INVALID)

        // questionType not blank
        val questionType = q.optString("questionType", "").trim()
        if (questionType.isBlank()) reasons.add(RejectionReason.QUESTION_TYPE_BLANK)

        // skills exists and not empty
        val skillsArr = q.optJSONArray("skills")
        val skillsValid = skillsArr != null && skillsArr.length() > 0 &&
            (0 until skillsArr.length()).any { skillsArr.optString(it, "").trim().isNotBlank() }
        if (!skillsValid) reasons.add(RejectionReason.SKILLS_MISSING_OR_EMPTY)

        // explanation field exists (value can be null/blank per convention)
        if (!q.has("explanation")) reasons.add(RejectionReason.EXPLANATION_FIELD_MISSING)

        // source exists
        val source = q.optString("source", "").trim()
        if (source.isBlank()) reasons.add(RejectionReason.SOURCE_MISSING)

        // sourceRef exists
        val sourceRef = q.optString("sourceRef", "").trim()
        if (sourceRef.isBlank()) reasons.add(RejectionReason.SOURCE_REF_MISSING)

        // duplicate stem in pack
        if (seenNormalizedStems != null && stem.isNotBlank()) {
            val norm = QuestionStemHash.normalizeStem(stem)
            if (norm in seenNormalizedStems) {
                reasons.add(RejectionReason.DUPLICATE_STEM_IN_PACK)
            }
        }

        return ValidationResult(reasons.isEmpty(), reasons)
    }

    /** Builds a [ValidationReport] from pack validation result for logging. */
    fun buildReport(
        packValidation: PackValidationResult,
        packName: String? = null
    ): ValidationReport {
        val perQuestionRejections = packValidation.questionResults
            .filter { !it.second.isValid }
            .map { (idx, r) -> idx to r.summary }
        return ValidationReport(
            packName = packName,
            totalQuestions = packValidation.totalQuestions,
            validCount = packValidation.validCount,
            rejectedCount = packValidation.rejectedCount,
            reasonsByCount = packValidation.rejectedReasons,
            perQuestionRejections = perQuestionRejections
        )
    }
}
