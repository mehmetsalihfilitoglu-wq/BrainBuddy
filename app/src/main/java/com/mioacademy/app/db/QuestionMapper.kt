package com.mioacademy.app.db

import android.util.Log
import com.mioacademy.app.quiz.AdaptiveQuizRuntime
import com.mioacademy.app.quiz.ExamType
import com.mioacademy.app.quiz.LevelGroup
import com.mioacademy.app.quiz.Question
import com.mioacademy.app.quiz.QuizDifficulty
import com.mioacademy.app.quiz.Subject
import org.json.JSONArray

object QuestionMapper {
    private const val TAG = "QuestionMapper"

    fun toQuestion(j: HistoryJoinedQuestion): Question {
        val rawChoices = parseChoices(j.optionsJson)
        val choices = stripSuffixPatterns(rawChoices)
        val subject = mapSubject(j.subject)
        val levelGroup = j.levelGroup?.let { parseLevelGroup(it) } ?: LevelGroup.GRADE_5_8
        val grade = j.gradeTag?.toIntOrNull()?.coerceIn(1, 7) ?: 6
        return Question(
            id = j.questionId,
            levelGroup = levelGroup,
            subject = subject,
            gradeTag = j.gradeTag ?: "",
            grade = grade,
            stem = j.text,
            choices = choices,
            correctIndex = j.correctIndex.coerceIn(0, choices.size - 1),
            hint = j.hint?.takeIf { it.isNotBlank() },
            imageAsset = null,
            difficulty = QuizDifficulty.MEDIUM,
            examType = ExamType.GENERAL,
            topic = null
        )
    }

    /**
     * Yeni `QuestionEntity` şemasından domain `Question` modeline map.
     * QuestionEntity:
     *  - grade: 1..7 (1 = Junior)
     *  - subject: "mat" | "turkce" | "fen" | "sosyal" | "ing"
     *  - difficulty: 0=EASY,1=MEDIUM,2=HARD (eski verilerde 3=HARD olarak ele alınır)
     */
    fun toQuestion(
        e: QuestionEntity,
        presentationStem: String? = null,
        presentationChoices: List<String>? = null,
        contentQualityTier: String? = null,
    ): Question {
        val rawChoices = presentationChoices ?: parseChoices(e.optionsJson)
        val stem = presentationStem ?: e.questionText
        // ALWAYS sanitize choices — every single Question that reaches UI goes through this.
        val choices = if (presentationChoices != null) {
            // Caller already fixed — but still strip any suffix that leaked
            stripSuffixPatterns(rawChoices)
        } else if (e.examType == "IMAT") {
            // Official IMAT items are verbatim A–E (up to 5 options). Never run the K-12
            // distractor pipeline — it caps to 4 options and would drop option E.
            rawChoices
        } else {
            // DB choices — run full fixDistractors pipeline
            val fixed = AdaptiveQuizRuntime.fixDistractors(rawChoices, stem, e.answerIndex)
            if (fixed != null) {
                fixed
            } else {
                // fixDistractors couldn't repair → strip suffixes as last resort
                Log.w(TAG, "DISPLAY_SANITIZE id=${e.id} fixDistractors=null, stripping suffixes")
                stripSuffixPatterns(rawChoices)
            }
        }
        val subject = mapSubject(e.subject)
        val levelGroup = LevelGroup.GRADE_5_8
        val difficulty = when (e.difficulty) {
            0 -> QuizDifficulty.EASY
            2, 3 -> QuizDifficulty.HARD
            else -> QuizDifficulty.MEDIUM
        }
        // Preserve true grade for LGS (typically 8); clamp to 1..7 only for non-LGS content.
        val rawGrade = e.grade
        val examType = e.examType?.let {
            try { ExamType.valueOf(it) } catch (_: Exception) { ExamType.GENERAL }
        } ?: ExamType.GENERAL
        val grade = if (examType == ExamType.LGS) {
            rawGrade.coerceAtLeast(1)
        } else {
            rawGrade.coerceIn(1, 7)
        }
        return Question(
            id = e.id,
            levelGroup = levelGroup,
            subject = subject,
            gradeTag = grade.toString(),
            grade = grade,
            stem = stem,
            choices = choices,
            correctIndex = e.answerIndex.coerceIn(0, choices.size - 1),
            hint = e.explanation?.takeIf { it.isNotBlank() },
            imageAsset = e.imageAsset?.takeIf { it.isNotBlank() },
            difficulty = difficulty,
            examType = examType,
            // For IMAT, carry the exam-subject label in topic so the quiz chip can show it
            // (the K-12 Subject enum has no IMAT subjects). Preserve null for other exams.
            topic = if (examType == ExamType.IMAT) imatSubjectLabel(e.subject) else null,
            type = e.type,
            skill = e.skill,
            presentationStem = presentationStem,
            presentationChoices = presentationChoices,
            contentQualityTier = contentQualityTier ?: e.qualityTier,
        )
    }

    /**
     * Last-resort sanitizer: strips any parenthetical suffix pattern from choices.
     * This ensures NO suffix-based fake distractor EVER reaches the UI, regardless of source.
     * Examples removed: "(yanlış yön)", "(geçersiz değer)", "(farklı durum)", etc.
     */
    private val STRIP_SUFFIX_REGEX = Regex("""\s*\((?:yanlış|geçersiz|farklı|hatalı|eksik|ters|fazla)[^)]*\)\s*""", RegexOption.IGNORE_CASE)
    private val STRIP_ELLIPSIS_SUFFIX = Regex("""…\s*$""")

    private fun stripSuffixPatterns(choices: List<String>): List<String> {
        return choices.map { opt ->
            var cleaned = STRIP_SUFFIX_REGEX.replace(opt, "").trim()
            cleaned = STRIP_ELLIPSIS_SUFFIX.replace(cleaned, "").trim()
            if (cleaned != opt.trim()) {
                Log.w(TAG, "SUFFIX_STRIPPED '${opt.take(40)}' -> '${cleaned.take(40)}'")
            }
            cleaned.ifBlank { opt.trim() }
        }
    }

    private fun parseChoices(json: String): List<String> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.optString(it, "") }
        } catch (_: Exception) {
            listOf("A", "B", "C", "D")
        }
    }

    /** Turkish label for an IMAT exam-subject code (matches AdmissionExamRegistry.IMAT). */
    fun imatSubjectLabel(code: String): String = when (code.lowercase()) {
        "biology" -> "Biyoloji"
        "chemistry" -> "Kimya"
        "physics_math" -> "Fizik & Matematik"
        "logic" -> "Mantık & Okuma"
        else -> "IMAT"
    }

    fun mapSubject(s: String): Subject = when (s.lowercase()) {
        "math", "mat" -> Subject.MAT
        "tr", "turkce" -> Subject.TURKCE
        "en", "ing" -> Subject.ING
        "fen" -> Subject.FEN
        "sosyal" -> Subject.SOSYAL
        "hayat", "hayat bilgisi" -> Subject.HAYAT
        "inkilap" -> Subject.INKILAP
        "din" -> Subject.DIN
        else -> Subject.MAT
    }

    /** DB subject key for given Subject (for queries). */
    fun toDbSubject(s: Subject): String = when (s) {
        Subject.MAT -> "mat"
        Subject.TURKCE -> "turkce"
        Subject.FEN -> "fen"
        Subject.SOSYAL -> "sosyal"
        Subject.HAYAT -> "hayat"
        Subject.ING -> "ing"
        Subject.INKILAP -> "inkilap"
        Subject.DIN -> "din"
    }

    private fun parseLevelGroup(s: String): LevelGroup = try {
        LevelGroup.valueOf(s)
    } catch (_: Exception) {
        LevelGroup.GRADE_5_8
    }

    private fun parseFirstTag(tagsJson: String?): String? {
        if (tagsJson.isNullOrBlank()) return null
        return try {
            val arr = JSONArray(tagsJson)
            if (arr.length() > 0) arr.optString(0, "").takeIf { it.isNotBlank() } else null
        } catch (_: Exception) {
            null
        }
    }
}
