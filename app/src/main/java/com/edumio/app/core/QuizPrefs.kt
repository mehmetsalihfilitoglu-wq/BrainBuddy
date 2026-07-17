package com.edumio.app.core

import android.content.Context
import com.edumio.app.quiz.QuizDifficulty

class QuizPrefs(context: Context) {
    private val prefs = ProfileScopedPrefs.quizPrefs(context)

    fun difficulty(): QuizDifficulty = try {
        QuizDifficulty.valueOf(prefs.getString(KEY_DIFFICULTY, QuizDifficulty.MEDIUM.name) ?: QuizDifficulty.MEDIUM.name)
    } catch (_: Exception) { QuizDifficulty.MEDIUM }

    fun setDifficulty(d: QuizDifficulty) =
        prefs.edit().putString(KEY_DIFFICULTY, d.name).apply()

    fun questionsPerSession(): Int =
        prefs.getInt(KEY_QUESTIONS_PER_SESSION, 20).coerceIn(20, 50)

    fun setQuestionsPerSession(count: Int) =
        prefs.edit().putInt(KEY_QUESTIONS_PER_SESSION, count.coerceIn(20, 50)).apply()

    fun selectedCategories(): Set<String> =
        prefs.getStringSet(KEY_CATEGORIES, null) ?: emptySet()

    fun setSelectedCategories(cats: Set<String>) =
        prefs.edit().putStringSet(KEY_CATEGORIES, cats).apply()

    companion object {
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_QUESTIONS_PER_SESSION = "questions_per_session"
        private const val KEY_CATEGORIES = "categories"
    }
}
