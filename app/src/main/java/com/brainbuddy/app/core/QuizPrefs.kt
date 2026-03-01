package com.brainbuddy.app.core

import android.content.Context
import com.brainbuddy.app.quiz.QuizDifficulty

class QuizPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun difficulty(): QuizDifficulty = try {
        QuizDifficulty.valueOf(prefs.getString(KEY_DIFFICULTY, QuizDifficulty.MEDIUM.name) ?: QuizDifficulty.MEDIUM.name)
    } catch (_: Exception) { QuizDifficulty.MEDIUM }

    fun setDifficulty(d: QuizDifficulty) =
        prefs.edit().putString(KEY_DIFFICULTY, d.name).apply()

    fun questionsPerSession(): Int =
        prefs.getInt(KEY_QUESTIONS_PER_SESSION, 10).coerceIn(5, 15)

    fun setQuestionsPerSession(count: Int) =
        prefs.edit().putInt(KEY_QUESTIONS_PER_SESSION, count.coerceIn(5, 15)).apply()

    fun selectedCategories(): Set<String> =
        prefs.getStringSet(KEY_CATEGORIES, null) ?: emptySet()

    fun setSelectedCategories(cats: Set<String>) =
        prefs.edit().putStringSet(KEY_CATEGORIES, cats).apply()

    companion object {
        private const val PREFS = "bb_quiz_prefs"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_QUESTIONS_PER_SESSION = "questions_per_session"
        private const val KEY_CATEGORIES = "categories"
    }
}
