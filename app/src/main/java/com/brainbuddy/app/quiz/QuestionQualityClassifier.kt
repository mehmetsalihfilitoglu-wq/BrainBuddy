package com.brainbuddy.app.quiz

import org.json.JSONArray
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Global content-quality classifier for all grades and core subjects.
 * Produces tier (EASY/MEDIUM/HARD), scores 0..100, and structured flags.
 */
object QuestionQualityClassifier {

    const val TIER_EASY = "EASY"
    const val TIER_MEDIUM = "MEDIUM"
    const val TIER_HARD = "HARD"

    data class Output(
        val qualityTier: String,
        val reasoningScore: Int,
        val distractorQualityScore: Int,
        val contextComplexityScore: Int,
        val qualityFlags: List<String>,
    )

    fun classify(
        subject: Subject,
        grade: Int,
        questionText: String,
        options: List<String>,
        difficultyInt: Int,
    ): Output {
        val stem = questionText.trim()
        val lower = stem.lowercase(Locale("tr"))
        val opts = options.map { it.trim() }.filter { it.isNotBlank() && it != "-" }

        val flags = mutableListOf<String>()

        val contextScore = scoreContextComplexity(subject, stem, lower, opts)
        val distractorScore = DistractorQualityEvaluator.score(opts, stem)
        val reasoningScore = scoreReasoning(subject, grade, stem, lower, opts, difficultyInt, contextScore, distractorScore, flags)

        var tier = mapScoresToTier(reasoningScore, contextScore, distractorScore, difficultyInt)

        tier = applySubjectRules(subject, stem, lower, opts, grade, tier, flags)

        if (distractorScore < 35) {
            flags.add("weak_distractors")
            if (tier == TIER_HARD) tier = TIER_MEDIUM
            if (distractorScore < 18) {
                tier = TIER_EASY
                flags.add("severe_distractor_failure")
            }
        }

        if (stem.length < 22) flags.add("stem_too_short")
        if (isObviousAnswerLengthBias(opts)) flags.add("length_bias_reveals_answer")

        val distinctFlags = flags.distinct()
        return Output(
            qualityTier = tier,
            reasoningScore = reasoningScore.coerceIn(0, 100),
            distractorQualityScore = distractorScore.coerceIn(0, 100),
            contextComplexityScore = contextScore.coerceIn(0, 100),
            qualityFlags = distinctFlags,
        )
    }

    fun flagsToJson(flags: List<String>): String = JSONArray(flags).toString()

    private fun mapScoresToTier(
        reasoning: Int,
        context: Int,
        distractor: Int,
        difficultyInt: Int,
    ): String {
        val composite = (reasoning * 5 + context * 3 + distractor * 2) / 10
        val boosted = when {
            difficultyInt >= 2 -> composite + 8
            difficultyInt <= 0 -> composite - 10
            else -> composite
        }.coerceIn(0, 100)
        return when {
            boosted >= 68 -> TIER_HARD
            boosted >= 42 -> TIER_MEDIUM
            else -> TIER_EASY
        }
    }

    private fun scoreContextComplexity(subject: Subject, stem: String, lower: String, options: List<String>): Int {
        var s = 30
        if (stem.length >= 120) s += 18
        if (stem.length >= 220) s += 12
        if (stem.count { it == '\n' } >= 1) s += 6
        val ctxWords = listOf(
            "grafik", "tablo", "şekil", "parça", "paragraf", "metne göre", "yorumla",
            "çıkarım", "karşılaştır", "analiz", "experiment", "gözlem", "variable",
            "cause", "effect", "harita", "timeline", "chart", "reading", "context"
        )
        s += 4 * ctxWords.count { it in lower }
        if (subject == Subject.TURKCE && stem.length >= 180) s += 10
        if (subject == Subject.MAT && (Regex("oran|yüzde|problem|çok adım|iki işlem").containsMatchIn(lower))) s += 12
        if (subject == Subject.FEN && Regex("deney|gözlem|grafik|tablo|hipotez|değişken").containsMatchIn(lower)) s += 12
        if (subject == Subject.SOSYAL && Regex("harita|kronoloji|neden|sonuç|karşılaştır|yorum").containsMatchIn(lower)) s += 12
        if (subject == Subject.ING && Regex("paragraph|passage|according to|infer|imply").containsMatchIn(lower)) s += 12
        s += (options.sumOf { it.length } / 120).coerceAtMost(8)
        return s.coerceIn(0, 100)
    }

    private fun scoreReasoning(
        subject: Subject,
        grade: Int,
        stem: String,
        lower: String,
        options: List<String>,
        difficultyInt: Int,
        contextScore: Int,
        distractorScore: Int,
        flags: MutableList<String>,
    ): Int {
        var r = 38
        val reasoningHints = listOf(
            "karşılaştır", "çıkarım", "yorum", "hangisi olamaz", "en uygun", "en doğru",
            "metne göre", "parçaya göre", "varsayım", "çıkarılabilir", "neden", "sonuç",
            "compare", "infer", "imply", "except", "least", "most", "if ", "eğer ",
            "çok adım", "iki işlem", "gizli", "koşul", "oran", "yüzde"
        )
        r += 5 * reasoningHints.count { it in lower }
        if (Regex("\\d+\\s*[+\\-×*/÷]").containsMatchIn(stem.replace(" ", ""))) {
            val opCount = Regex("[+\\-×*/÷]").findAll(stem).count()
            if (opCount <= 1 && subject == Subject.MAT) {
                r -= 22
                flags.add("single_step_math")
            }
        }
        if (listOf("kaçtır", "nedir", "hangisidir", "kimdir", "başkent").any { lower.endsWith(it) } && stem.length < 90) {
            r -= 18
            flags.add("trivial_recall_ending")
        }
        if (subject == Subject.TURKCE && stem.length < 100 && !lower.contains("paragraf")) {
            r -= 12
            flags.add("turkce_short_non_paragraph")
        }
        if (subject == Subject.ING && stem.length < 80) {
            r -= 14
            flags.add("ing_too_short")
        }
        if (subject == Subject.FEN && stem.length < 70 && !Regex("deney|grafik|tablo|gözlem").containsMatchIn(lower)) {
            r -= 12
            flags.add("fen_memorization_like")
        }
        if (subject == Subject.SOSYAL && stem.length < 75 && Regex("tarih|kim|nerede|kaç").containsMatchIn(lower)) {
            r -= 10
            flags.add("sosyal_plain_recall")
        }
        r += (contextScore - 50) / 5
        r += (distractorScore - 50) / 6
        if (grade >= 5) r += 6 else r -= 4
        if (difficultyInt >= 2) r += 8
        if (difficultyInt <= 0) r -= 12
        return r.coerceIn(0, 100)
    }

    private fun applySubjectRules(
        subject: Subject,
        stem: String,
        lower: String,
        options: List<String>,
        @Suppress("UNUSED_PARAMETER") grade: Int,
        tier: String,
        flags: MutableList<String>,
    ): String {
        var t = tier
        when (subject) {
            Subject.MAT -> {
                if (Regex("^\\s*\\d+\\s*[+\\-×*/]\\s*\\d+").containsMatchIn(stem.replace(" ", "")) &&
                    stem.length < 55
                ) {
                    t = TIER_EASY
                    flags.add("mat_one_step_drill")
                }
                if (!Regex("problem|oran|yüzde|grafik|tablo|şekil|çok|adım|koşul|gizli").containsMatchIn(lower) &&
                    stem.length < 70
                ) {
                    t = TIER_EASY
                    flags.add("mat_lacks_reasoning_context")
                }
            }
            Subject.TURKCE -> {
                if (stem.length < 140 && !lower.contains("paragraf") && !lower.contains("metne göre")) {
                    t = t.coerceAtMost(TIER_MEDIUM)
                    flags.add("turkce_insufficient_context")
                }
            }
            Subject.FEN -> {
                if (!Regex("deney|gözlem|yorum|grafik|tablo|değişken|hipotez|sonuç|neden").containsMatchIn(lower) &&
                    stem.length < 95
                ) {
                    t = t.coerceAtMost(TIER_MEDIUM)
                    flags.add("fen_lacks_interpretation")
                }
            }
            Subject.SOSYAL -> {
                if (stem.length < 85 && Regex("hangi tarih|kimdir|nerededir|başkent").containsMatchIn(lower)) {
                    t = TIER_EASY
                    flags.add("sosyal_plain_fact")
                }
            }
            Subject.ING -> {
                if (Regex("\\b(am|is|are|was|were)\\b").containsMatchIn(lower) &&
                    stem.length < 90 && options.size <= 4
                ) {
                    t = TIER_EASY
                    flags.add("ing_basic_be_verb")
                }
            }
            else -> {}
        }
        return t
    }

    private fun String.coerceAtMost(max: String): String {
        val order = listOf(TIER_EASY, TIER_MEDIUM, TIER_HARD)
        val ai = order.indexOf(this)
        val bi = order.indexOf(max)
        if (ai < 0 || bi < 0) return this
        return if (ai > bi) max else this
    }

    private fun isObviousAnswerLengthBias(opts: List<String>): Boolean {
        if (opts.size < 2) return false
        val lens = opts.map { it.length }
        val maxL = lens.maxOrNull() ?: 0
        val minL = lens.minOrNull() ?: 0
        if (maxL < 12) return false
        return maxL > minL * 2 + 12
    }
}

/** Heuristic distractor plausibility (0 = broken, 100 = strong). */
object DistractorQualityEvaluator {

    fun score(options: List<String>, stem: String): Int {
        if (options.size < 2) return 10
        val o = options.map { it.trim() }.filter { it.isNotBlank() && it != "-" }
        if (o.size < 2) return 12
        var s = 55
        val lengths = o.map { it.length }
        val mean = lengths.average()
        val varLen = lengths.map { abs(it - mean) }.average()
        if (varLen > mean * 0.85 && mean > 15) s -= 25
        val maxL = lengths.maxOrNull() ?: 0
        val minL = lengths.minOrNull() ?: 0
        if (maxL > minL * 2.5 + 10) s -= 22
        val norm = o.map { it.lowercase(Locale("tr")).replace(Regex("\\s+"), " ") }
        val distinct = norm.distinct().size
        if (distinct < o.size) s -= 30
        val nonsense = o.count { it.length < 2 || it == "." || it == "-" }
        s -= nonsense * 12
        val gramHint = o.count { Regex("^[a-zA-ZğüşıöçĞÜŞİÖÇ]+$").matches(it) } == 1 && o.size >= 3
        if (gramHint) s -= 18
        if (o.any { it.contains(stem.take(20), ignoreCase = true) }) s -= 15
        return s.coerceIn(0, 100)
    }
}
