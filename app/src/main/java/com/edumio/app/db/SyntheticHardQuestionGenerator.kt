package com.edumio.app.db

/**
 * Inserts HARD-tier synthetic rows when natural pools are thin (same packs as quota, tier patched).
 */
object SyntheticHardQuestionGenerator {

    fun generate(grade: Int, subjectKey: String, count: Int): List<QuestionEntity> {
        if (count <= 0) return emptyList()
        val raw = QuotaSyntheticQuestions.generateEmergencyTopUp(grade, subjectKey, count)
        return raw.map { e ->
            e.copy(
                qualityTier = "HARD",
                reasoningLevel = 3,
                reasoningScore = 85,
                distractorQualityScore = 72,
                contextComplexityScore = 78,
                unservableReason = null,
                isNewGenerationLike = true,
                qualityScore = (e.qualityScore).coerceAtLeast(85),
            )
        }
    }

    /** LGS mode only: same stems as grade quota pack but tagged examType=LGS (never mixed into grade pools). */
    fun generateLgs(subjectKey: String, count: Int): List<QuestionEntity> {
        if (count <= 0) return emptyList()
        val salt = System.nanoTime()
        val raw = QuotaSyntheticQuestions.generateEmergencyTopUp(7, subjectKey, count)
        return raw.mapIndexed { i, e ->
            e.copy(
                id = "lgs_syn_${subjectKey}_${salt}_$i",
                examType = "LGS",
                grade = 8,
                qualityTier = "HARD",
                reasoningLevel = 3,
                reasoningScore = 85,
                distractorQualityScore = 72,
                contextComplexityScore = 78,
                unservableReason = null,
                isNewGenerationLike = true,
                qualityScore = e.qualityScore.coerceAtLeast(85),
            )
        }
    }
}
