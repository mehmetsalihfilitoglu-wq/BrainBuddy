package com.edumio.app.quiz

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

    /** Last picker: GRADE vs LGS */
    @Volatile
    var currentSelectedMode: String = "UNKNOWN"

    @Volatile
    var currentSelectedGrade: Int = -1

    @Volatile
    var currentPoolSourceSummary: String = ""

    @Volatile
    var servedGeneralCount: Int = 0

    @Volatile
    var servedLgsCount: Int = 0

    @Volatile
    var rejectedWrongModeCount: Int = 0

    @Volatile
    var quarantinedLowQualityCount: Int = 0

    @Volatile
    var playablePoolSizeLastQuery: Int = 0

    /** Aliases for debug dashboards (same backing fields). */
    val servedHardCount: Int get() = hardServed
    val servedMediumCount: Int get() = mediumServed
    val servedBorderlineCount: Int get() = borderlineServed
    val servedEasyEmergencyCount: Int get() = easyEmergencyUsed

    fun reset() {
        hardServed = 0
        mediumServed = 0
        borderlineServed = 0
        easyEmergencyUsed = 0
        upgradedQuestionsCount = 0
        syntheticGeneratedCount = 0
        emergencyFallbackUsed = false
        currentSelectedMode = "UNKNOWN"
        currentSelectedGrade = -1
        currentPoolSourceSummary = ""
        servedGeneralCount = 0
        servedLgsCount = 0
        rejectedWrongModeCount = 0
        quarantinedLowQualityCount = 0
        playablePoolSizeLastQuery = 0
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
