package com.mioacademy.app.quiz

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
    HAYAT("Hayat Bilgisi"),
    ING("İngilizce"),
    INKILAP("İnkılap Tarihi"),
    DIN("Din Kültürü")
}

/** Exam pack types. Turkish K-12 exams + Italian admission exams (IMAT). */
enum class ExamType(val displayName: String) {
    LGS("LGS"),
    TYT("TYT"),
    AYT("AYT"),
    IMAT("IMAT"),
    GENERAL("Genel")
}

data class Question(
    val id: String,
    val levelGroup: LevelGroup,
    val subject: Subject,
    val gradeTag: String,
    /** Sınıf (1=Junior, 1..7=aktif sınıflar). Sınıf bazlı havuz için kullanılır. */
    val grade: Int,
    val stem: String,
    val choices: List<String>,
    val correctIndex: Int,
    val hint: String?,
    val imageAsset: String?,
    val difficulty: QuizDifficulty = QuizDifficulty.MEDIUM,
    val examType: ExamType = ExamType.GENERAL,
    val topic: String? = null,
    /** Coarse question type for diversity (e.g. PROBLEM, PARAGRAPH, MAP). */
    val type: String = "UNKNOWN",
    /** Finer-grained sub-skill/topic for diversity. */
    val skill: String = "UNKNOWN",
    /** Runtime-only stem override (quality upgrade); does not change DB. */
    val presentationStem: String? = null,
    /** Runtime-only choices override (distractor fix); does not change DB. */
    val presentationChoices: List<String>? = null,
    /** Content tier at serve time (HARD/MEDIUM/BORDERLINE/EASY). */
    val contentQualityTier: String? = null,
) {
    /** For header/subtitle: LGS shows "LGS", IMAT shows no grade, normal mode shows numeric grade (gradeTag). */
    val gradeDisplayLabel: String get() = when (examType) {
        ExamType.LGS -> "LGS"
        ExamType.IMAT -> ""
        else -> gradeTag
    }
}

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