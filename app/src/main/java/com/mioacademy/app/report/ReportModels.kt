package com.mioacademy.app.report

/**
 * Structured, real-data payloads for Premium learning reports. Builders populate
 * these only from actual recorded activity; a renderer turns them into the email
 * ([ReportPayload]). When there isn't enough data, [hasEnoughData] is false and the
 * report says so honestly rather than inventing numbers.
 *
 * [studyTimeMinutes] is nullable on purpose: per-question timing is not captured yet,
 * so study time is reported as "not tracked" rather than fabricated.
 */
data class WeeklyReportData(
    val areaLabel: String,
    val hasEnoughData: Boolean,
    val questionsSolved: Int,
    val questionsPrevWeek: Int,
    val accuracy: Int?,
    val accuracyPrevWeek: Int?,
    val activeStudyDays: Int,
    val streakDays: Int,
    val xpEarned: Int,
    val mostImprovedTopic: String?,
    val mostImprovedDelta: Int,
    val strongestTopic: Pair<String, Int>?,
    val weakestTopic: Pair<String, Int>?,
    val questionsMastered: Int,
    val questionsUnderReview: Int,
    val readinessScore: Int?,
    val readinessChange: Int?,
    /** Questions answered per day for the last 7 days, oldest → today. */
    val heatmapLast7: List<Int>,
    val journeyMilestone: String?,
    val nextWeekRecommendation: String,
    val studyTimeMinutes: Int?
) {
    val questionsDelta: Int get() = questionsSolved - questionsPrevWeek
}

data class MonthlyReportData(
    val areaLabel: String,
    val hasEnoughData: Boolean,
    val questionsSolved: Int,
    val questionsPrevMonth: Int,
    val accuracy: Int?,
    val activeStudyDays: Int,
    val bestImprovementTopic: String?,
    val bestImprovementDelta: Int,
    val weakestAreas: List<String>,
    val questionsMastered: Int,
    val reviewCorrections: Int,
    val readinessScore: Int?,
    val readinessChange: Int?,
    val journeyMilestones: List<String>,
    val nextMonthRecommendation: String,
    val studyTimeMinutes: Int?
) {
    val questionsDelta: Int get() = questionsSolved - questionsPrevMonth
}
