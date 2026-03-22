package com.brainbuddy.app.db

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
}
