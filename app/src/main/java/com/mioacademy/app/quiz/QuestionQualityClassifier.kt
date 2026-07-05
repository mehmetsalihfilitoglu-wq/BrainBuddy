package com.mioacademy.app.quiz

import org.json.JSONArray
import java.util.Locale
import kotlin.math.abs

/**
 * Strict content tiering: HARD / MEDIUM / BORDERLINE / EASY.
 * Trivial or recall-heavy items are forced to EASY; BORDERLINE never carries plain recall.
 */
object QuestionQualityClassifier {

    const val TIER_EASY = "EASY"
    const val TIER_BORDERLINE = "BORDERLINE"
    const val TIER_MEDIUM = "MEDIUM"
    const val TIER_HARD = "HARD"

    data class Output(
        val qualityTier: String,
        /** 0=EASY, 1=BORDERLINE, 2=MEDIUM, 3=HARD */
        val reasoningLevel: Int,
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
        answerIndex: Int? = null,
    ): Output {
        val stem = questionText.trim()
        val lower = stem.lowercase(Locale("tr"))
        val opts = options.map { it.trim() }.filter { it.isNotBlank() && it != "-" }

        val flags = mutableListOf<String>()

        val contextScore = scoreContextComplexity(subject, stem, lower, opts)
        val distractorScore = DistractorQualityEvaluator.score(opts, stem)
        val reasoningScore = scoreReasoning(subject, grade, stem, lower, opts, difficultyInt, contextScore, distractorScore, flags)

        val tier = assignStrictTier(subject, stem, lower, opts, answerIndex, distractorScore, flags)

        var finalTier = tier
        if (distractorScore < QuizQualityPolicy.DISTRACTOR_SOFT_FLOOR) {
            flags.add("weak_distractors")
            finalTier = finalTier.coerceAtMost(TIER_MEDIUM)
            if (distractorScore < QuizQualityPolicy.DISTRACTOR_SOFT_FLOOR / 2) {
                finalTier = finalTier.coerceAtMost(TIER_BORDERLINE)
                flags.add("severe_distractor_failure")
            }
            if (distractorScore < 28) {
                finalTier = finalTier.coerceAtMost(TIER_EASY)
                flags.add("distractors_too_weak_to_serve_mid")
            }
        }

        if (stem.length < 22) flags.add("stem_too_short")
        if (DistractorQualityEvaluator.isAnswerLengthOutlier(opts)) flags.add("length_bias_reveals_answer")

        if (finalTier == TIER_BORDERLINE && flags.contains("recall_pattern")) {
            finalTier = TIER_EASY
            flags.add("borderline_downgrade_recall")
        }
        if (finalTier == TIER_MEDIUM && flags.contains("recall_pattern")) {
            finalTier = TIER_EASY
            flags.add("medium_downgrade_recall")
        }

        val reasoningLevel = levelFromTier(finalTier)
        val distinctFlags = flags.distinct()
        return Output(
            qualityTier = finalTier,
            reasoningLevel = reasoningLevel,
            reasoningScore = reasoningScore.coerceIn(0, 100),
            distractorQualityScore = distractorScore.coerceIn(0, 100),
            contextComplexityScore = contextScore.coerceIn(0, 100),
            qualityFlags = distinctFlags,
        )
    }

    fun levelFromTier(tier: String): Int = when (normalizeTier(tier)) {
        TIER_HARD -> 3
        TIER_MEDIUM -> 2
        TIER_BORDERLINE -> 1
        else -> 0
    }

    fun normalizeTier(t: String): String = when (t.uppercase(Locale.ROOT)) {
        TIER_HARD -> TIER_HARD
        TIER_MEDIUM -> TIER_MEDIUM
        TIER_BORDERLINE -> TIER_BORDERLINE
        TIER_EASY -> TIER_EASY
        else -> TIER_MEDIUM
    }

    fun tierFromReasoningLevel(level: Int): String = when (level.coerceIn(0, 3)) {
        3 -> TIER_HARD
        2 -> TIER_MEDIUM
        1 -> TIER_BORDERLINE
        else -> TIER_EASY
    }

    fun flagsToJson(flags: List<String>): String = JSONArray(flags).toString()

    private fun assignStrictTier(
        subject: Subject,
        stem: String,
        lower: String,
        options: List<String>,
        answerIndex: Int?,
        distractorScore: Int,
        flags: MutableList<String>,
    ): String {
        val interpretation = hasInterpretationCue(lower)
        val scenario = hasScenarioOrContext(stem, lower)
        val steps = estimateReasoningSteps(subject, stem, lower)
        val directRecall = isDirectRecall(subject, stem, lower, options)
        val singleStep = isSingleStepObvious(subject, stem, lower)
        val noContext = isNoContext(stem, lower, interpretation, scenario)
        val fakePara = isFakeParagraphAnswerInText(stem, options, answerIndex)

        if (directRecall) flags.add("recall_pattern")
        if (singleStep) flags.add("single_step")
        if (noContext) flags.add("no_context")
        if (fakePara) flags.add("fake_paragraph_answer_in_text")

        val forceEasy = directRecall || singleStep || noContext || fakePara ||
            isGrammarFillIn(lower, stem.length) ||
            isShortDefinitionRecall(subject, stem, lower)

        if (forceEasy) return TIER_EASY

        val hardEligible = (steps >= 2 || interpretation || scenario)
        if (hardEligible) {
            flags.add("hard_signal")
            return TIER_HARD
        }

        val mediumEligible = (steps >= 1 || interpretation) && !directRecall
        if (mediumEligible) {
            flags.add("medium_signal")
            return TIER_MEDIUM
        }

        val borderlineEligible = isBorderlineOnly(lower, stem, distractorScore, directRecall)
        if (borderlineEligible) {
            flags.add("borderline_signal")
            return TIER_BORDERLINE
        }

        return TIER_EASY
    }

    private fun hasInterpretationCue(lower: String): Boolean {
        val cues = listOf(
            "metne göre", "parçaya göre", "çıkarım", "yorumla", "yorum", "varsayım",
            "hangisi olamaz", "hangisi değildir", "yanlış", "en uygun olmayan",
            "infer", "imply", "according to", "except", "least", "most",
            "neden", "sonuç", "karşılaştır", "analiz"
        )
        return cues.any { it in lower }
    }

    /**
     * True context/scenario — not merely a long stem — so trivial items cannot reach HARD on length alone.
     */
    private fun hasScenarioOrContext(stem: String, lower: String): Boolean {
        val keywordContext = Regex(
            "deney|problem|parça|grafik|tablo|şekil|sınıf|model|örnek|bağlam|hikaye|durum|senaryo|metne göre|parçaya göre|aşağıdaki metin"
        ).containsMatchIn(lower)
        val multiSentence = stem.count { it == '.' } >= 2 || stem.count { it == '\n' } >= 1
        if (stem.length >= 150 && (multiSentence || keywordContext)) return true
        if (stem.length >= 90 && keywordContext) return true
        if (multiSentence && stem.length >= 75) return true
        return false
    }

    private fun estimateReasoningSteps(subject: Subject, stem: String, lower: String): Int {
        var s = 0
        if (Regex("ve sonra|ardından|ilk|ikinci|önce|sonra|ardışık|zincir|çok adım|iki işlem").containsMatchIn(lower)) s += 2
        if (subject == Subject.MAT) {
            val compact = stem.replace("\\s+".toRegex(), "")
            val ops = Regex("[+\\-×*/÷]").findAll(compact).count()
            s += ops.coerceAtMost(3)
            if (Regex("oran|yüzde|problem|koşul|gizli").containsMatchIn(lower)) s += 1
        } else {
            if (interpretationDepth(lower)) s += 1
        }
        return s.coerceIn(0, 5)
    }

    private fun interpretationDepth(lower: String): Boolean =
        listOf("çıkarım", "örtük", "ima", "anlam", "karşılaştır").any { it in lower }

    private fun isDirectRecall(subject: Subject, stem: String, lower: String, @Suppress("UNUSED_PARAMETER") options: List<String>): Boolean {
        if (Regex("\\b(1[0-9]{3}|20[0-9]{2})\\b").containsMatchIn(stem)) return true
        if (Regex("mondros|lozan|kurtuluş savaşı|hangi yıl|hangi tarih|kaç yılında").containsMatchIn(lower)) return true
        if (Regex("fotosentez|oksijen gazı|karbondioksit|hangi gaz|hangi organel|mitokondri|ribozom").containsMatchIn(lower) &&
            stem.length < 130
        ) {
            return true
        }
        if (Regex("başkent|kimdir|nerededir|tarihi nedir|kuruluş").containsMatchIn(lower) && stem.length < 100) return true
        if (subject == Subject.ING && Regex("\\b(am|is|are|was|were)\\b").containsMatchIn(lower) && stem.length < 95) return true
        if (listOf("nedir?", "nedir", "hangisidir?", "hangisidir").any { lower.trimEnd().endsWith(it) } && stem.length < 85) return true
        return false
    }

    private fun isSingleStepObvious(subject: Subject, stem: String, lower: String): Boolean {
        if (subject != Subject.MAT) return false
        val compact = stem.replace("\\s+".toRegex(), "")
        val opCount = Regex("[+\\-×*/÷]").findAll(compact).count()
        return opCount <= 1 && stem.length < 55 && Regex("\\d").containsMatchIn(stem)
    }

    private fun isNoContext(stem: String, lower: String, interpretation: Boolean, scenario: Boolean): Boolean {
        if (interpretation || scenario) return false
        return stem.length < 55
    }

    private fun isGrammarFillIn(lower: String, stemLen: Int): Boolean {
        return stemLen < 100 && Regex("\\b(am|is|are|was|were)\\b").containsMatchIn(lower) &&
            (lower.contains("___") || lower.contains("…") || Regex("\\s_\\s").containsMatchIn(lower))
    }

    private fun isShortDefinitionRecall(subject: Subject, stem: String, lower: String): Boolean {
        if (stem.length >= 100) return false
        return when (subject) {
            Subject.FEN -> Regex("\\bnedir\\b|tanımı|tanımıdır|hangi element").containsMatchIn(lower) &&
                !Regex("deney|grafik|tablo|gözlem|sonuç|hipotez").containsMatchIn(lower)
            Subject.TURKCE -> stem.length < 90 && !lower.contains("paragraf") && !lower.contains("metne göre")
            else -> false
        }
    }

    private fun isFakeParagraphAnswerInText(stem: String, options: List<String>, answerIndex: Int?): Boolean {
        if (answerIndex == null || answerIndex !in options.indices) return false
        val looksParagraph = stem.length >= 160 || stem.lowercase(Locale("tr")).contains("paragraf") ||
            stem.count { it == '.' } >= 3
        if (!looksParagraph) return false
        val ans = options[answerIndex].trim()
        if (ans.length < 5) return false
        val nStem = stem.lowercase(Locale("tr")).replace("\\s+".toRegex(), " ")
        val nAns = ans.lowercase(Locale("tr")).replace("\\s+".toRegex(), " ")
        if (nStem.contains(nAns)) return true
        if (ans.length >= 14) {
            val chunk = nAns.take((nAns.length * 2 / 3).coerceAtLeast(12))
            if (chunk.length >= 12 && nStem.contains(chunk)) return true
        }
        return false
    }

    private fun isBorderlineOnly(lower: String, stem: String, distractorScore: Int, directRecall: Boolean): Boolean {
        if (directRecall) return false
        val elim = Regex("hangisi|değildir|olamaz|yanlış|uygun olmayan|hariç|en uygun olmayan").containsMatchIn(lower)
        val lightReasoning = Regex("çıkarım|yorum|anlam|örtük|ima|karşılaştır").containsMatchIn(lower)
        val light = stem.length >= 68 && distractorScore >= 40 && (lightReasoning || elim)
        return (elim && distractorScore >= 34) || light
    }

    private fun scoreContextComplexity(subject: Subject, stem: String, lower: String, options: List<String>): Int {
        var s = 28
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
        if (subject == Subject.MAT && Regex("oran|yüzde|problem|çok adım|iki işlem|koşul|gizli").containsMatchIn(lower)) s += 12
        if (subject == Subject.FEN && Regex("deney|gözlem|grafik|tablo|hipotez|değişken|neden|sonuç").containsMatchIn(lower)) s += 12
        if (subject == Subject.SOSYAL && Regex("harita|kronoloji|neden|sonuç|karşılaştır|yorum|ilişki").containsMatchIn(lower)) s += 12
        if (subject == Subject.ING && Regex("paragraph|passage|according to|infer|imply|context").containsMatchIn(lower)) s += 12
        s += (options.sumOf { it.length } / 120).coerceAtMost(8)
        return s.coerceIn(0, 100)
    }

    private fun scoreReasoning(
        subject: Subject,
        grade: Int,
        stem: String,
        lower: String,
        @Suppress("UNUSED_PARAMETER") options: List<String>,
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
                r -= 24
                flags.add("single_step_math")
            }
        }
        if (listOf("kaçtır", "nedir", "hangisidir", "kimdir", "başkent").any { lower.endsWith(it) } && stem.length < 90) {
            r -= 20
            flags.add("trivial_recall_ending")
        }
        if (subject == Subject.TURKCE && stem.length < 100 && !lower.contains("paragraf") && !lower.contains("metne göre")) {
            r -= 14
            flags.add("turkce_short_non_paragraph")
        }
        if (subject == Subject.ING && stem.length < 80) {
            r -= 16
            flags.add("ing_too_short")
        }
        if (subject == Subject.FEN && stem.length < 70 && !Regex("deney|grafik|tablo|gözlem|neden|sonuç|hipotez").containsMatchIn(lower)) {
            r -= 14
            flags.add("fen_memorization_like")
        }
        if (subject == Subject.SOSYAL && stem.length < 85 && Regex("hangi tarih|kimdir|nerededir|başkent|kaç yıl").containsMatchIn(lower)) {
            r -= 18
            flags.add("sosyal_plain_recall")
        }
        r += (contextScore - 50) / 5
        r += (distractorScore - 50) / 6
        if (grade >= 5) r += 6 else r -= 4
        if (difficultyInt >= 2) r += 8
        if (difficultyInt <= 0) r -= 12
        return r.coerceIn(0, 100)
    }

    private val tierOrder = listOf(TIER_EASY, TIER_BORDERLINE, TIER_MEDIUM, TIER_HARD)

    private fun String.coerceAtMost(max: String): String {
        val ai = tierOrder.indexOf(normalizeTier(this))
        val bi = tierOrder.indexOf(normalizeTier(max))
        if (ai < 0 || bi < 0) return this
        return if (ai > bi) max else this
    }
}

/** Distractor plausibility (0 = broken, 100 = strong). */
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

        s += tokenSimilarityBonus(o)
        s -= categoryMismatchPenalty(o)
        s -= obviousOutlierCount(o, stem) * 14

        return s.coerceIn(0, 100)
    }

    fun passesServeThreshold(opts: List<String>, stem: String): Boolean =
        score(opts, stem) >= QuizQualityPolicy.DISTRACTOR_SOFT_FLOOR

    fun isAnswerLengthOutlier(opts: List<String>): Boolean {
        val o = opts.map { it.trim() }.filter { it.isNotBlank() && it != "-" }
        if (o.size < 2) return false
        val lens = o.map { it.length }
        val maxL = lens.maxOrNull() ?: 0
        val minL = lens.minOrNull() ?: 0
        if (maxL < 12) return false
        return maxL > minL * 2 + 12
    }

    fun obviousOutlierCount(options: List<String>, stem: String): Int {
        val o = options.map { it.trim() }.filter { it.isNotBlank() && it != "-" }
        if (o.size < 2) return o.size
        val stemHasDigit = Regex("\\d").containsMatchIn(stem)
        val stemIsMath = Regex("[+\\-×*/=]").containsMatchIn(stem)
        var bad = 0
        for (t in o) {
            val optDigit = Regex("\\d").containsMatchIn(t)
            if (stemIsMath) continue
            if (stemHasDigit != optDigit && t.length > 2) bad++
        }
        return bad
    }

    private fun tokenSimilarityBonus(opts: List<String>): Int {
        if (opts.size < 2) return 0
        val tokens = opts.map { tokenize(it) }
        if (tokens.any { it.isEmpty() }) return 0
        var sum = 0.0
        var pairs = 0
        for (i in tokens.indices) {
            for (j in i + 1 until tokens.size) {
                sum += jaccard(tokens[i], tokens[j])
                pairs++
            }
        }
        if (pairs == 0) return 0
        val avg = sum / pairs
        return when {
            avg >= 0.35 -> 0
            avg >= 0.2 -> -8
            else -> -18
        }
    }

    private fun categoryMismatchPenalty(opts: List<String>): Int {
        if (opts.size < 2) return 0
        val numeric = opts.count { Regex("^\\s*[+-]?\\d").containsMatchIn(it) }
        val allText = opts.count { !Regex("\\d").containsMatchIn(it) && it.length > 2 }
        if (numeric > 0 && allText > 0 && numeric + allText == opts.size) return 22
        return 0
    }

    private fun tokenize(s: String): Set<String> =
        s.lowercase(Locale("tr"))
            .replace(Regex("[^a-zA-ZğüşıöçĞÜŞİÖÇ0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
            .toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val inter = a.intersect(b).size
        val union = a.union(b).size
        return if (union == 0) 0.0 else inter.toDouble() / union
    }
}
