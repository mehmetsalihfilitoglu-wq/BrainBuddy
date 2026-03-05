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

    fun evaluate(
        subject: Subject,
        grade: Int,
        questionText: String,
        options: List<String>
    ): Result {
        val stem = questionText.trim()
        val lower = stem.lowercase(Locale("tr"))

        val isTooShort = stem.length < 25
        val isMathDrill = subject == Subject.MAT && isSimpleMathExpression(stem)
        val isFactRecall = isFactRecallQuestion(subject, stem, lower)
        val isMathConversionOnly = subject == Subject.MAT && isMathOnlyConversion(stem, lower)
        val isShortAndSingleFact = isTooShort && isSingleFactLike(subject, lower)

        var isActive = true
        var reason: String? = null

        // MAT: 2–8. sınıflar için bağlamsız, tek adımlı işlem sorularını pasifleştir.
        if (isMathDrill && grade in 2..8) {
            isActive = false
            reason = "too_simple_math"
        } else if (isMathConversionOnly && grade in 2..8) {
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
            Subject.FEN, Subject.SOSYAL -> keywords.any { it in lower }
            Subject.TURKCE -> isShortTurkish && !lower.contains("paragraf")
            else -> false
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
            Subject.ING -> "english_language"
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

