package com.edumio.app.core

import android.content.Context

class UserGoalPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveGoal(goal: UserGoal) {
        prefs.edit()
            .putString(KEY_CAREER, goal.careerPath.name)
            .putString(KEY_CITIES, goal.destinationCities.joinToString(","))
            .putInt(KEY_YEAR, goal.applicationYear)
            .putString(KEY_ITALIAN_LEVEL, goal.italianLevel.name)
            .putInt(KEY_DAILY_GOAL, goal.dailyGoalQuestions)
            .putString(KEY_NAME, goal.studentName)
            .putLong(KEY_EXAM_DATE, goal.examDate)
            .apply()
    }

    fun getGoal(): UserGoal {
        val career = safeCareer(prefs.getString(KEY_CAREER, null))
        val cities = prefs.getString(KEY_CITIES, "")
            ?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val year = prefs.getInt(KEY_YEAR, 2026)
        val level = safeLevel(prefs.getString(KEY_ITALIAN_LEVEL, null))
        val goal = prefs.getInt(KEY_DAILY_GOAL, 15)
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val examDate = prefs.getLong(KEY_EXAM_DATE, 0L)
        return UserGoal(career, cities, year, level, goal, name, examDate)
    }

    fun hasGoal(): Boolean = prefs.contains(KEY_CAREER)

    fun getStudentName(): String = prefs.getString(KEY_NAME, "") ?: ""

    fun getDailyGoalQuestions(): Int = prefs.getInt(KEY_DAILY_GOAL, 15)

    fun getExamType(): ExamType = try {
        CareerPath.valueOf(prefs.getString(KEY_CAREER, CareerPath.OTHER.name)!!)
            .examType
    } catch (_: Exception) {
        ExamType.UNKNOWN
    }

    fun getCareerPath(): CareerPath = safeCareer(prefs.getString(KEY_CAREER, null))

    /**
     * Sets the active career/study area. Kept in sync with the active study-area
     * profile so every existing consumer (Home, StudyHub, GrowthHub, Coach,
     * daily mission subjects) automatically follows the active area.
     */
    fun setCareerPath(career: CareerPath) =
        prefs.edit().putString(KEY_CAREER, career.name).apply()

    fun setExamDate(epochMs: Long) = prefs.edit().putLong(KEY_EXAM_DATE, epochMs).apply()

    fun setDailyGoal(questions: Int) = prefs.edit().putInt(KEY_DAILY_GOAL, questions).apply()

    private fun safeCareer(name: String?): CareerPath = try {
        CareerPath.valueOf(name ?: "")
    } catch (_: Exception) {
        CareerPath.OTHER
    }

    private fun safeLevel(name: String?): ItalianLevel = try {
        ItalianLevel.valueOf(name ?: "")
    } catch (_: Exception) {
        ItalianLevel.A0
    }

    companion object {
        private const val PREFS_NAME = "user_goal_prefs"
        private const val KEY_CAREER = "career_path"
        private const val KEY_CITIES = "destination_cities"
        private const val KEY_YEAR = "application_year"
        private const val KEY_ITALIAN_LEVEL = "italian_level"
        private const val KEY_DAILY_GOAL = "daily_goal_questions"
        private const val KEY_NAME = "student_name"
        private const val KEY_EXAM_DATE = "exam_date"
    }
}
