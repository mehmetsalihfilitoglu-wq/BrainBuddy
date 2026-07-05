package com.mioacademy.app.quiz

import org.json.JSONArray
import java.util.Locale

/**
 * Question quality gate: strict trivial rejection + classifier outputs.
 */
object QuestionQualityGate {

    data class Result(
        val isActive: Boolean,
        val deactivationReason: String?,
        val questionType: String,
        val skillsJson: String,
        val qualityTier: String,
        /** 0..3 — maps to EASY/BORDERLINE/MEDIUM/HARD. */
        val reasoningLevel: Int,
        val reasoningScore: Int,
        val distractorQualityScore: Int,
        val contextComplexityScore: Int,
        val qualityFlagsJson: String,
        val unservableReason: String?,
    )

    /**
     * True if the item is too shallow to serve under strict policy (see product rules).
     */
    fun isTrivial(
        subject: Subject,
        @Suppress("UNUSED_PARAMETER") grade: Int,
        questionText: String,
        options: List<String>,
        difficulty: Int = 1,
        answerIndex: Int? = null,
    ): Boolean {
        val stem = questionText.trim()
        val lower = stem.lowercase(Locale("tr"))
        val opts = options.map { it.trim() }.filter { it.isNotBlank() && it != "-" }

        if (answerIndex != null && answerIndex in options.indices) {
            val ans = options[answerIndex].trim()
            if (ans.length >= 2 && stem.contains(ans, ignoreCase = true)) return true
        }

        if (stem.length < 28) return true

        if (DistractorQualityEvaluator.obviousOutlierCount(opts, stem) >= 2) return true
        if (!DistractorQualityEvaluator.passesServeThreshold(opts, stem) &&
            DistractorQualityEvaluator.isAnswerLengthOutlier(opts)
        ) {
            return true
        }

        if (isPureMemorizationStem(subject, stem, lower)) return true
        if (isBasicGrammarShell(subject, lower, stem.length)) return true
        if (subject == Subject.MAT && isSingleStepMathOnly(stem)) return true
        if (subject == Subject.TURKCE && isTurkceDirectExtraction(lower, stem.length)) return true
        if (subject == Subject.FEN && isFenDefinitionRecall(lower, stem.length)) return true
        if (subject == Subject.SOSYAL && isSosyalBannedRecall(lower, stem)) return true
        if (subject == Subject.ING && isIngBasicFillIn(lower, stem.length)) return true

        if (difficulty >= 1 && isTrivialQuestion(subject, stem, lower)) return true

        return false
    }

    /** @param difficulty 0=EASY, 1=MEDIUM, 2=HARD (quiz JSON difficulty, not content tier). */
    fun evaluate(
        subject: Subject,
        grade: Int,
        questionText: String,
        options: List<String>,
        difficulty: Int = 1,
        answerIndex: Int? = null,
    ): Result {
        val stem = questionText.trim()
        val lower = stem.lowercase(Locale("tr"))

        val cls = QuestionQualityClassifier.classify(subject, grade, questionText, options, difficulty, answerIndex)

        val trivial = isTrivial(subject, grade, questionText, options, difficulty, answerIndex)

        val isTooShort = stem.length < 25
        val isMathDrill = subject == Subject.MAT && isSimpleMathExpression(stem)
        val isFactRecall = isFactRecallQuestion(subject, stem, lower)
        val isMathConversionOnly = subject == Subject.MAT && isMathOnlyConversion(stem, lower)
        val isShortAndSingleFact = isTooShort && isSingleFactLike(subject, lower)
        val isTrivialLegacy = difficulty >= 1 && isTrivialQuestion(subject, stem, lower)

        var isActive = true
        var reason: String? = null

        if (trivial) {
            isActive = false
            reason = "trivial_rejected"
        } else if (isTrivialLegacy) {
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

        val unservable: String? = if (!isActive && trivial) "TRIVIAL" else null

        return Result(
            isActive = isActive,
            deactivationReason = reason,
            questionType = questionType,
            skillsJson = skillsJson,
            qualityTier = cls.qualityTier,
            reasoningLevel = cls.reasoningLevel,
            reasoningScore = cls.reasoningScore,
            distractorQualityScore = cls.distractorQualityScore,
            contextComplexityScore = cls.contextComplexityScore,
            qualityFlagsJson = QuestionQualityClassifier.flagsToJson(cls.qualityFlags),
            unservableReason = unservable,
        )
    }

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

    private fun isPureMemorizationStem(subject: Subject, stem: String, lower: String): Boolean {
        val yearInStem = Regex("\\b(1[0-9]{3}|20[0-9]{2})\\b").containsMatchIn(stem)
        if (yearInStem && (subject == Subject.SOSYAL || subject == Subject.INKILAP)) return true

        val memorizationPhrases = listOf(
            "fotosentez", "oksijen gazı", "karbondioksit", "mitokondri", "ribozom",
            "avrupa ve asya", "iki kıta", "başkent", "kuruluş tarihi"
        )
        if (stem.length < 100 && memorizationPhrases.any { it in lower }) return true

        if (subject == Subject.FEN && stem.length < 90 &&
            Regex("\\b(nedir|hangi (organel|gaz|element)|tanımı)\\b").containsMatchIn(lower)
        ) {
            return true
        }
        return false
    }

    private fun isBasicGrammarShell(subject: Subject, lower: String, stemLen: Int): Boolean {
        if (subject != Subject.ING) return false
        if (stemLen > 100) return false
        return Regex("\\b(am|is|are|was|were)\\s+").containsMatchIn(lower) &&
            !Regex("paragraph|passage|according to|infer|because|although").containsMatchIn(lower)
    }

    private fun isSingleStepMathOnly(stem: String): Boolean {
        if (stem.length > 70) return false
        val compact = stem.replace("\\s+".toRegex(), "").replace("[=?]".toRegex(), "")
        if (compact.any { it.isLetter() }) return false
        val opCount = Regex("[+\\-×*/÷]").findAll(stem).count()
        return opCount <= 1 && Regex("\\d").containsMatchIn(stem)
    }

    private fun isTurkceDirectExtraction(lower: String, stemLen: Int): Boolean {
        if (stemLen >= 160 && (lower.contains("paragraf") || lower.contains("metne göre"))) return false
        return stemLen < 110 && !lower.contains("çıkarım") && !lower.contains("anlam") &&
            !lower.contains("yorum") && !lower.contains("özet")
    }

    private fun isFenDefinitionRecall(lower: String, stemLen: Int): Boolean {
        return stemLen < 85 && Regex("nedir\\?|tanım|doğrudan|tanımlayınız").containsMatchIn(lower) &&
            !Regex("deney|grafik|tablo|gözlem|neden|sonuç|hipotez").containsMatchIn(lower)
    }

    private fun isSosyalBannedRecall(lower: String, stem: String): Boolean {
        if (Regex("harita|tablo|grafik|yorum|karşılaştır|neden|sonuç|ilişki|çıkarım").containsMatchIn(lower)) {
            return false
        }
        return Regex("\\b(antlaşma|mondros|lozan|kimdir|başkent|hangi yıl|hangi tarih|nerededir)\\b").containsMatchIn(lower) ||
            (Regex("\\b(1[0-9]{3}|20[0-9]{2})\\b").containsMatchIn(stem) && stem.length < 120)
    }

    private fun isIngBasicFillIn(lower: String, stemLen: Int): Boolean {
        if (stemLen > 120) return false
        return Regex("\\b(am|is|are|was|were)\\b").containsMatchIn(lower) &&
            !Regex("paragraph|reading|according|infer|context|meaning").containsMatchIn(lower)
    }

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
        lower: String,
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
        lower: String,
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
        isFactRecall: Boolean,
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
