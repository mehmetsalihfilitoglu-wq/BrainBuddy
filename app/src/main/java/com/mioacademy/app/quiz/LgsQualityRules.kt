package com.mioacademy.app.quiz

import java.util.Locale

/**
 * Strict LGS question quality rules.
 * LGS packs must only accept high-quality, new-generation-style questions.
 *
 * Reject weak/trivial:
 * - one-step arithmetic only
 * - pure memorization with no reasoning
 * - single-word answer style
 * - extremely short stem with no context
 * - simple direct-definition questions (unless transformed into reasoning)
 *
 * Prefer/score highly:
 * - paragraph-based, scenario-based
 * - chart/table/infographic interpretation
 * - multi-step reasoning, comparison/inference
 * - daily-life context, visual-supported
 *
 * Rules are configurable for easy tightening later.
 */
object LgsQualityRules {

    /** Configurable thresholds – easy to tighten. */
    object Config {
        /** Minimum stem length (chars). Below = auto-deactivate. */
        var minStemLength: Int = 80
            private set

        /** Quality score (0..100) threshold. Below = deactivate. */
        var minQualityScore: Int = 40
            private set

        /** Paragraph-like threshold (chars). */
        var paragraphThreshold: Int = 200
            private set

        /** Context/story threshold (chars). */
        var contextThreshold: Int = 120
            private set

        /** Multi-sentence threshold (sentence count). */
        var minSentencesForParagraph: Int = 2
            private set

        fun tighten() {
            minStemLength = 100
            minQualityScore = 50
        }

        fun relax() {
            minStemLength = 60
            minQualityScore = 30
        }
    }

    data class Result(
        val isActive: Boolean,
        val deactivationReason: String?,
        val qualityScore: Int,
        val isNewGenerationLike: Boolean,
        val questionType: String
    ) {
        companion object {
            const val DEACTIVATION_LOW_LGS_QUALITY = "low_lgs_quality"
        }
    }

    private val comparisonKeywords = listOf(
        "karşılaştır", "farklı", "benzer", "farkı", "fark", "kıyasla",
        "hangisi daha", "hangisi farklı", "hangisinde farklı",
        "compare", "different", "similar", "difference"
    ).map { it.lowercase() }

    private val inferenceKeywords = listOf(
        "çıkarılabilir", "çıkarım", "yorumlanabilir", "sonuç", "buna göre",
        "buna dayanarak", "bu bilgiye göre", "metne göre", "parçaya göre",
        "grafiğe göre", "tabloya göre", "şekle göre", "yukarıdaki bilgilere göre",
        "inference", "conclude", "implies"
    ).map { it.lowercase() }

    private val visualTableChartKeywords = listOf(
        "grafik", "tablo", "şekil", "şekilde", "görsel", "görselde",
        "grafikte", "tabloda", "diyagram", "harita", "infografik",
        "chart", "table", "figure", "diagram", "graph"
    ).map { it.lowercase() }

    private val reasoningKeywords = listOf(
        "problemi", "probleme", "problem", "çözüm", "hesapla", "bul",
        "neden", "nasıl", "hangisi doğrudur", "hangisi yanlıştır",
        "açıkla", "yorumla", "değerlendir", "analiz"
    ).map { it.lowercase() }

    private val dailyLifeKeywords = listOf(
        "günlük", "hayatta", "yaşamda", "evde", "okulda", "market",
        "alışveriş", "yolculuk", "tatil", "spor", "beslenme"
    ).map { it.lowercase() }

    private val trivialEndings = listOf(
        "kaçtır?", "kaçtır", "nedir?", "nedir", "hangisidir?", "hangisidir",
        "kimdir?", "nerededir?"
    )

    private val oneStepArithmeticPattern = Regex("""\d+\s*[+\-×xX*/\:÷]\s*\d+""")

    /** Evaluate LGS question quality. Returns Result with qualityScore, isNewGenerationLike, isActive. */
    fun evaluate(
        stem: String,
        options: List<String>,
        subjectKey: String,
        hasImageAsset: Boolean,
        difficulty: Int
    ): Result {
        val s = stem.trim()
        val lower = s.lowercase(Locale("tr"))

        var score = 50 // baseline
        val reasons = mutableListOf<String>()

        // ---- Reject signals (deduct) ----
        if (s.length < Config.minStemLength) {
            score -= 30
            reasons.add("stem_too_short")
        }
        if (isOneStepArithmeticOnly(lower, subjectKey)) {
            score -= 40
            reasons.add("one_step_arithmetic")
        }
        if (isPureMemorization(lower, subjectKey)) {
            score -= 35
            reasons.add("pure_memorization")
        }
        if (isSingleWordAnswerStyle(lower, options)) {
            score -= 25
            reasons.add("single_word_style")
        }
        if (isSimpleDirectDefinition(lower, subjectKey)) {
            score -= 30
            reasons.add("direct_definition")
        }
        if (trivialEndings.any { lower.trimEnd().endsWith(it) } && s.length < 80) {
            score -= 20
            reasons.add("trivial_ending")
        }

        // ---- Prefer signals (add) ----
        val stemLen = s.length
        if (stemLen >= Config.paragraphThreshold) {
            score += 20
            reasons.add("paragraph_based")
        } else if (stemLen >= Config.contextThreshold) {
            score += 10
            reasons.add("context_based")
        }

        val sentenceCount = s.split(Regex("[.!?]")).map { it.trim() }.count { it.isNotEmpty() }
        if (sentenceCount >= Config.minSentencesForParagraph && stemLen >= 100) {
            score += 10
            reasons.add("multi_sentence")
        }

        if (visualTableChartKeywords.any { it in lower }) {
            score += 20
            reasons.add("visual_table_chart")
        } else if (hasImageAsset) {
            score += 15
            reasons.add("has_image")
        }

        val reasoningCount = reasoningKeywords.count { it in lower }
        if (reasoningCount >= 2) {
            score += 15
            reasons.add("multi_reasoning")
        } else if (reasoningCount >= 1) {
            score += 5
        }

        if (comparisonKeywords.any { it in lower }) {
            score += 15
            reasons.add("comparison")
        }
        if (inferenceKeywords.any { it in lower }) {
            score += 15
            reasons.add("inference")
        }
        if (dailyLifeKeywords.any { it in lower }) {
            score += 10
            reasons.add("daily_life")
        }

        val questionType = inferLgsType(s, lower)
        if (questionType == "paragraph" || questionType == "context_problem" || questionType == "interpretation") {
            score += 10
        }

        val qualityScore = score.coerceIn(0, 100)
        val isNewGenerationLike = qualityScore >= 55 && (
            visualTableChartKeywords.any { it in lower } ||
            inferenceKeywords.any { it in lower } ||
            comparisonKeywords.any { it in lower } ||
            stemLen >= Config.paragraphThreshold ||
            (reasoningCount >= 2 && stemLen >= Config.contextThreshold)
        )

        val belowThreshold = qualityScore < Config.minQualityScore ||
            s.length < Config.minStemLength ||
            reasons.any { it in listOf("one_step_arithmetic", "pure_memorization", "direct_definition") }

        val isActive = !belowThreshold
        val deactivationReason = if (!isActive) Result.DEACTIVATION_LOW_LGS_QUALITY else null

        return Result(
            isActive = isActive,
            deactivationReason = deactivationReason,
            qualityScore = qualityScore,
            isNewGenerationLike = isNewGenerationLike,
            questionType = questionType
        )
    }

    private fun isOneStepArithmeticOnly(lower: String, subjectKey: String): Boolean {
        if (subjectKey != "mat") return false
        if (lower.length > 100) return false
        if (lower.contains("problemi") || lower.contains("probleme") || lower.contains("grafik") || lower.contains("tablo")) return false
        return oneStepArithmeticPattern.containsMatchIn(lower) &&
            !lower.contains("oran") && !lower.contains("yüzde") && !lower.contains("problem")
    }

    private fun isPureMemorization(lower: String, subjectKey: String): Boolean {
        val factKeywords = listOf(
            "başkent", "tarihi nedir", "kimdir", "formülü", "formülü?",
            "birimi", "kaç derece", "donma noktası", "kaynama noktası"
        )
        if (!factKeywords.any { it in lower }) return false
        if (lower.length > 120) return false
        val hasReasoning = inferenceKeywords.any { it in lower } || comparisonKeywords.any { it in lower }
        return !hasReasoning
    }

    private fun isSingleWordAnswerStyle(lower: String, options: List<String>): Boolean {
        val avgOptLen = options.map { it.length }.average()
        return avgOptLen < 8 && lower.length < 60
    }

    private fun isSimpleDirectDefinition(lower: String, subjectKey: String): Boolean {
        if (lower.length > 120) return false
        val defPatterns = listOf("nedir?", "ne demektir?", "tanımı", "anlamı")
        if (!defPatterns.any { it in lower }) return false
        val hasContext = visualTableChartKeywords.any { it in lower } || inferenceKeywords.any { it in lower }
        return !hasContext
    }

    private fun inferLgsType(stem: String, lower: String): String {
        if (stem.length >= Config.paragraphThreshold) return "paragraph"
        if (visualTableChartKeywords.any { it in lower }) return "interpretation"
        if (lower.contains("problemi") || lower.contains("probleme") || lower.contains("problem")) return "context_problem"
        if (inferenceKeywords.any { it in lower } || comparisonKeywords.any { it in lower }) return "reasoning"
        if (stem.length >= Config.contextThreshold) return "long_context"
        return "short_item"
    }
}
