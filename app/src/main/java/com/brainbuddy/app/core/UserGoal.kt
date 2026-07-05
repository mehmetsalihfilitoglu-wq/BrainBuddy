package com.brainbuddy.app.core

data class UserGoal(
    val careerPath: CareerPath,
    val destinationCities: List<String>,
    val applicationYear: Int,           // 0 = just exploring
    val italianLevel: ItalianLevel,
    val dailyGoalQuestions: Int,
    val studentName: String,
    val examDate: Long = 0L             // epoch ms; 0 = not set
) {
    val examType: ExamType get() = careerPath.examType
    val isExploring: Boolean get() = applicationYear == 0
}
