package com.brainbuddy.app.quiz

/**
 * TEK kaynak model dosyası.
 * Bu dosyadan başka yerde Question/Subject TANIMLAMAYACAKSIN.
 */

enum class LevelGroup {
    AGE_3_5, GRADE_1_4, GRADE_5_8, GRADE_9_12
}

enum class Subject(val tr: String) {
    MAT("Matematik"),
    TURKCE("Türkçe"),
    FEN("Fen Bilimleri"),
    SOSYAL("Sosyal Bilgiler"),
    ING("İngilizce")
}

data class Question(
    val id: String,
    val levelGroup: LevelGroup,
    val subject: Subject,
    val gradeTag: String,
    val stem: String,
    val choices: List<String>,
    val correctIndex: Int,
    val hint: String?,
    val imageAsset: String?,
    val difficulty: QuizDifficulty = QuizDifficulty.MEDIUM
)

enum class QuizDifficulty(val tr: String) {
    EASY("Kolay"),
    MEDIUM("Orta"),
    HARD("Zor")
}

data class AnswerRecord(
    val questionId: String,
    val selectedIndex: Int,
    val correctIndex: Int
) {
    val isCorrect: Boolean get() = selectedIndex == correctIndex
}