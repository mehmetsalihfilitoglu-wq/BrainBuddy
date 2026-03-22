package com.brainbuddy.app.quiz

/**
 * Last grade-quiz build audit (volatile; debug / UI).
 */
object QualityAudit {

    @Volatile
    var hardServed: Int = 0

    @Volatile
    var mediumServed: Int = 0

    @Volatile
    var borderlineServed: Int = 0

    @Volatile
    var easyEmergencyUsed: Int = 0

    @Volatile
    var upgradedQuestionsCount: Int = 0

    @Volatile
    var syntheticGeneratedCount: Int = 0

    @Volatile
    var emergencyFallbackUsed: Boolean = false

    fun reset() {
        hardServed = 0
        mediumServed = 0
        borderlineServed = 0
        easyEmergencyUsed = 0
        upgradedQuestionsCount = 0
        syntheticGeneratedCount = 0
        emergencyFallbackUsed = false
    }

    fun recordTier(tier: String?) {
        when (tier?.uppercase()) {
            QuizQualityPolicy.TIER_HARD -> hardServed++
            QuizQualityPolicy.TIER_MEDIUM -> mediumServed++
            QuizQualityPolicy.TIER_BORDERLINE -> borderlineServed++
            QuizQualityPolicy.TIER_EASY -> { /* EASY counted only when emergency path */ }
        }
    }
}
