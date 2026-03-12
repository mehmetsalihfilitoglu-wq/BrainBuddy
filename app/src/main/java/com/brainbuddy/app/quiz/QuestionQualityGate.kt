package com.brainbuddy.app.quiz

import org.json.JSONArray
import java.util.Locale

/**
 * Basit soru kalite gate'i ve tür/skill çıkarımı.
 *
 * - Çok kısa / salt işlem / ezber soruları pasifler.
 * - questionType ve skillsJson alanlarını doldurur.
 */
object QuestionQualityGate {

    data class Result(
        val isActive: Boolean,
        val deactivationReason: String?,
        val questionType: String,
        val skillsJson: String
    )

    /** @param difficulty 0=EASY, 1=MEDIUM, 2=HARD. EASY stays permissive; MEDIUM/HARD get trivial filter. */
    fun evaluate(
        subject: Subject,
        grade: Int,
        questionText: String,
        options: List<String>,
        difficulty: Int = 1
    ): Result {
        val stem = questionText.trim()
        val lower = stem.lowercase(Locale("tr"))

        val isTooShort = stem.length < 25
        val isMathDrill = subject == Subject.MAT && isSimpleMathExpression(stem)
        val isFactRecall = isFactRecallQuestion(subject, stem, lower)
        val isMathConversionOnly = subject == Subject.MAT && isMathOnlyConversion(stem, lower)
        val isShortAndSingleFact = isTooShort && isSingleFactLike(subject, lower)
        val isTrivial = difficulty >= 1 && isTrivialQuestion(subject, stem, lower)

        var isActive = true
        var reason: String? = null

        if (isTrivial) {
            isActive = false
            reason = "too_trivial"
        } else if (isMathDrill && grade in 1..7) {
            isActive = false
            reason = "too_simple_math"
        } else if (isMathConversionOnly && grade in 1..7) {
            isActive = false
            reason = "too_basic"
        } else if (isShortAndSingleFact) {
            isActive = false
            reason = "too_basic"
        } else if (isFactRecall) {
            isActive = false
            reason = "too_memorization"
        } else if (isTooShort) {
            isActive = false
            reason = "too_short"
        }

        val questionType = inferQuestionType(subject, stem, lower)
        val skillsJson = buildSkillsJson(subject, grade, questionType, isFactRecall)

        return Result(
            isActive = isActive,
            deactivationReason = reason,
            questionType = questionType,
            skillsJson = skillsJson
        )
    }

    /**
     * Extremely simple questions for MEDIUM/HARD. Examples: "1 metre kaç santimetredir?",
     * "Suyun donma noktası kaçtır?", "3+5 kaçtır?". Stronger filtering for MAT and FEN.
     * EASY questions are not checked (caller passes difficulty).
     */
    fun isTrivialQuestion(subject: Subject, stem: String, lower: String): Boolean {
        if (stem.length < 25) return true
        val trivialEndings = listOf("kaçtır?", "kaçtır", "nedir?", "nedir", "hangisidir?", "hangisidir")
        val stemTrim = lower.trimEnd()
        val endsTrivial = trivialEndings.any { stemTrim.endsWith(it) }
        if (!endsTrivial) return false
        val contextKeywords = listOf(
            "problemi", "probleme", "problem", "grafik", "tablo", "şekilde",
            "metne göre", "parçaya göre", "aşağıdaki", "paragraf", "yukarıdaki"
        )
        val hasContext = stem.length >= 80 || contextKeywords.any { it in lower }
        if (hasContext) return false
        if (subject == Subject.MAT || subject == Subject.FEN) return true
        return stem.length < 50
    }

    /** Math: sadece birim dönüşümü (örn: "1 km kaç metredir?") - too_basic. */
    private fun isMathOnlyConversion(stem: String, lower: String): Boolean {
        if (stem.length > 60) return false
        val conversionPatterns = listOf(
            "kaç metredir", "kaç metredir?", "kaç metre", "kaç gram", "kaç kilogram",
            "kaç litredir", "kaç litredir?", "kaç litre", "kaç saat", "kaç dakika",
            "kaç santimetredir", "kaç cm", "kaç mm", "kaç km", "kaç derece",
            "eşittir", "=? metre", "=? gram"
        )
        return conversionPatterns.any { it in lower } && !lower.contains("problemi") &&
            !lower.contains("probleme") && !lower.contains("problem")
    }

    /** Tek bilgi sorusu: kısa, ezber/tek cümle (başkent, tarih, formül vs). */
    private fun isSingleFactLike(subject: Subject, lower: String): Boolean {
        if (subject == Subject.ING) return false
        val factKeywords = listOf(
            "başkent", "tarihi nedir", "kaçtır", "hangisidir", "nerededir",
            "formülü", "formülü?", "birimi", "kimdir", "nedir?"
        )
        return factKeywords.any { it in lower }
    }

    private fun isSimpleMathExpression(stem: String): Boolean {
        val compact = stem
            .replace("\\s+".toRegex(), "")
            .replace("[=?]".toRegex(), "")
        if (compact.length < 5) return true
        if (compact.length > 20) return false
        if (compact.any { it.isLetter() }) return false
        val allowed = "0123456789+-×xX*/:÷().,%"
        if (compact.any { it !in allowed }) return false
        return true
    }

    private fun isFactRecallQuestion(
        subject: Subject,
        stem: String,
        lower: String
    ): Boolean {
        if (stem.length > 80) return false
        val keywords = listOf(
            "tarihi nedir", "kaçtır", "hangisidir", "başkent", "kurulmuştur",
            "kimdir", "nerededir"
        )
        val isShortTurkish = subject == Subject.TURKCE && stem.length < 80
        return when (subject) {
            Subject.FEN, Subject.SOSYAL, Subject.HAYAT, Subject.INKILAP, Subject.DIN -> keywords.any { it in lower }
            Subject.TURKCE -> isShortTurkish && !lower.contains("paragraf")
            Subject.MAT, Subject.ING -> false
        }
    }

    private fun inferQuestionType(
        subject: Subject,
        stem: String,
        lower: String
    ): String {
        val sentences = stem.split(Regex("[.!?]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val isParagraph = stem.length >= 200 && sentences.size >= 2
        if (subject == Subject.TURKCE && isParagraph) {
            return "paragraph"
        }

        val problemKeywords = listOf(
            "problemi", "problem", "oran", "yüzde", "grafik", "tablo",
            "şekilde", "aşağıdaki", "metne göre", "parçaya göre"
        )
        val isProblemLike = stem.length >= 80 && problemKeywords.any { it in lower }

        return when {
            isProblemLike && subject == Subject.MAT -> "math_problem"
            isProblemLike -> "context_problem"
            stem.length >= 120 -> "long_context"
            else -> "short_item"
        }
    }

    private fun buildSkillsJson(
        subject: Subject,
        grade: Int,
        questionType: String,
        isFactRecall: Boolean
    ): String {
        val skills = mutableListOf<String>()

        skills += when (subject) {
            Subject.MAT -> "math_general"
            Subject.TURKCE -> "turkish_language"
            Subject.FEN -> "science_general"
            Subject.SOSYAL -> "social_science"
            Subject.HAYAT -> "life_studies"
            Subject.ING -> "english_language"
            Subject.INKILAP -> "history_civics"
            Subject.DIN -> "religious_culture"
        }

        if (questionType == "math_problem" || questionType == "context_problem") {
            skills += "problem_solving"
        }
        if (questionType == "paragraph" || questionType == "long_context") {
            skills += "reading_comprehension"
        }
        if (isFactRecall) {
            skills += "fact_recall"
        }

        skills += "grade_$grade"

        return JSONArray(skills.distinct()).toString()
    }
}

