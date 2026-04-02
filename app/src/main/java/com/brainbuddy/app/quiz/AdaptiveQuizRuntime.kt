package com.brainbuddy.app.quiz

import android.util.Log
import com.brainbuddy.app.db.QuestionEntity
import com.brainbuddy.app.db.QuestionMapper
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Runtime-only enrichment: stem upgrades, distractor repair, tier ordering, quiz balance.
 * Does not persist changes to the database.
 */
object AdaptiveQuizRuntime {

    private const val TAG = "AdaptiveQuizRuntime"

    fun normalizeContentTier(raw: String?): String {
        val t = raw?.trim()?.uppercase(Locale.ROOT) ?: return QuizQualityPolicy.TIER_MEDIUM
        return when (t) {
            "VERY_HARD", "VHARD" -> QuizQualityPolicy.TIER_HARD
            QuizQualityPolicy.TIER_HARD,
            QuizQualityPolicy.TIER_MEDIUM,
            QuizQualityPolicy.TIER_BORDERLINE,
            QuizQualityPolicy.TIER_EASY,
            -> t
            else -> QuizQualityPolicy.TIER_MEDIUM
        }
    }

    fun tierSortKey(tier: String): Int = when (normalizeContentTier(tier)) {
        QuizQualityPolicy.TIER_HARD -> 0
        QuizQualityPolicy.TIER_MEDIUM -> 1
        QuizQualityPolicy.TIER_BORDERLINE -> 2
        QuizQualityPolicy.TIER_EASY -> 3
        else -> 4
    }

    /**
     * HARD first, then MEDIUM, BORDERLINE, EASY — shuffled within each tier band.
     */
    fun <T> sortByTierPriority(rows: List<T>, tierOf: (T) -> String): List<T> {
        return rows.groupBy { tierSortKey(tierOf(it)) }
            .toSortedMap()
            .values
            .flatMap { band -> band.shuffled(Random) }
    }

    private fun looksLikeRecallFen(stem: String): Boolean {
        val l = stem.lowercase(Locale("tr"))
        return Regex("fotosentez|oksijen|karbondioksit|hangi gaz|hangi organel").containsMatchIn(l) &&
            stem.length < 120
    }

    private fun looksLikeRecallSosyal(stem: String): Boolean {
        val l = stem.lowercase(Locale("tr"))
        return Regex("hangi yıl|hangi tarih|kimdir|başkent|nerededir").containsMatchIn(l) && stem.length < 100
    }

    /**
     * Before serving EASY / reasoningLevel 0 items: reframe as a short scenario (memory-only; DB unchanged).
     */
    fun scenarioRewriteForEasyServe(subject: Subject, grade: Int, stem: String): String? {
        val trimmed = stem.trim()
        if (trimmed.length >= 220) return null
        val l = trimmed.lowercase(Locale("tr"))
        if (subject == Subject.FEN && Regex("fotosentez|hangi gaz|oksijen|karbondioksit").containsMatchIn(l)) {
            return "Bir bitki ışık altında bırakılıyor; deney sonucu hangi gazın arttığı gözlemlenir? (Fotosentez bağlamında düşününüz.)"
        }
        if (subject == Subject.SOSYAL && Regex("mondros|lozan|hangi yıl|hangi tarih|kaç yılında").containsMatchIn(l)) {
            return "Bir kaynak parçasına göre aşağıdaki olayın hangi yılda gerçekleştiği sorulmaktadır: $trimmed"
        }
        if (subject == Subject.ING && Regex("\\b(am|is|are|was|were)\\b").containsMatchIn(l)) {
            return "Kısa bir metinde boş bırakılan yere hangi yardımcı fiil gelmelidir? $trimmed"
        }
        if (subject == Subject.MAT && trimmed.length < 95 && Regex("\\d").containsMatchIn(trimmed)) {
            return "Gerçek yaşam bağlamında (model): $trimmed"
        }
        return "$grade. sınıf düzeyinde bir sınav senaryosunda şu soru sorulur: $trimmed"
    }

    /**
     * Wraps recall into a short scenario (memory-only; DB unchanged).
     */
    fun upgradeStemIfWeak(subject: Subject, grade: Int, stem: String): String? {
        if (stem.length >= 160) return null
        val prefix = when (subject) {
            Subject.FEN -> if (looksLikeRecallFen(stem)) {
                "Bir sınıf deneyinde öğrenciler kontrollü koşullar altında gözlem yapıyor. "
            } else null
            Subject.SOSYAL -> if (looksLikeRecallSosyal(stem)) {
                "Bir kaynak parçasına göre yorum yapınız. "
            } else null
            Subject.TURKCE -> if (stem.length < 90 && !stem.contains("paragraf")) {
                "Aşağıdaki metne göre; "
            } else null
            Subject.MAT -> if (stem.length < 90 && Regex("\\d").containsMatchIn(stem)) {
                "Gerçek yaşam bağlamı: $grade. sınıf düzeyinde bir modelde "
            } else null
            Subject.ING -> if (stem.length < 90) {
                "In context: "
            } else null
            else -> null
        } ?: return null
        return prefix + stem.trim()
    }

    fun maybeUpgradeEntity(entity: QuestionEntity): Pair<String?, Boolean> {
        val subj = QuestionMapper.mapSubject(entity.subject)
        val tier = normalizeContentTier(entity.qualityTier)
        val forceEasyServe = tier == QuizQualityPolicy.TIER_EASY || entity.reasoningLevel == 0
        val upgraded = if (forceEasyServe) {
            scenarioRewriteForEasyServe(subj, entity.grade, entity.questionText)
                ?: upgradeStemIfWeak(subj, entity.grade, entity.questionText)
        } else {
            upgradeStemIfWeak(subj, entity.grade, entity.questionText)
        }
        return upgraded to (upgraded != null)
    }

    // ─── Distractor quality ────────────────────────────────────────────

    /** Regex to catch ANY parenthetical suffix pattern on options — catches all known and future variants. */
    private val SUFFIX_PATTERN = Regex("""\((?:yanlış|geçersiz|farklı|hatalı|eksik|ters|fazla)[^)]*\)""", RegexOption.IGNORE_CASE)

    /** Regex for options that are just a truncated correct answer + suffix, e.g. "16.6… (yanlış yön)" */
    private val TRUNCATED_WITH_SUFFIX = Regex("""^.{1,20}…?\s*\(""")

    /** Public check for suffix patterns — used by materializeSingleQuestion as final safety net. */
    fun containsSuffixPattern(text: String): Boolean {
        val t = text.trim()
        return SUFFIX_PATTERN.containsMatchIn(t) || TRUNCATED_WITH_SUFFIX.containsMatchIn(t)
    }

    /**
     * Returns true when the option looks like a real distractor (not a placeholder).
     * Catches ALL known suffix-based fake patterns with regex, not just hardcoded strings.
     */
    private fun isValidDistractor(opt: String): Boolean {
        val t = opt.trim()
        if (t.length < 2 || t == "-") return false
        // Reject any parenthetical suffix pattern
        if (SUFFIX_PATTERN.containsMatchIn(t)) {
            Log.d(TAG, "DISTRACTOR_SUFFIX_CAUGHT: '$t'")
            return false
        }
        // Reject truncated-answer-with-suffix patterns like "16.6… (..."
        if (TRUNCATED_WITH_SUFFIX.containsMatchIn(t)) {
            Log.d(TAG, "DISTRACTOR_TRUNCATED_CAUGHT: '$t'")
            return false
        }
        return true
    }

    /**
     * Extract the primary numeric value from an option string.
     * Supports: "18,2", "18.2", "18", "14,2 TL", "3/4", etc.
     */
    private fun extractNumber(text: String): Double? {
        val t = text.trim().replace(" ", "")
        // Try fraction first: 3/4
        Regex("(\\d+)/(\\d+)").find(t)?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull() ?: return@let
            val den = m.groupValues[2].toDoubleOrNull() ?: return@let
            if (den != 0.0) return num / den
        }
        // Decimal or integer
        val m = Regex("(-?\\d+[.,]\\d+|-?\\d+)").findAll(t).lastOrNull() ?: return null
        return m.value.replace(",", ".").toDoubleOrNull()
    }

    /**
     * Detect the unit suffix from a numeric option (e.g. " TL", " kg", " cm").
     */
    private fun detectSuffix(text: String): String {
        val t = text.trim()
        return when {
            t.endsWith("TL", ignoreCase = true) -> " TL"
            t.endsWith("kg", ignoreCase = true) -> " kg"
            t.endsWith("cm²", ignoreCase = true) || t.endsWith("cm2", ignoreCase = true) -> " cm²"
            t.endsWith("cm", ignoreCase = true) -> " cm"
            t.endsWith("m²", ignoreCase = true) || t.endsWith("m2", ignoreCase = true) -> " m²"
            t.endsWith("mm", ignoreCase = true) -> " mm"
            t.endsWith("km", ignoreCase = true) -> " km"
            t.endsWith("lt", ignoreCase = true) || t.endsWith("litre", ignoreCase = true) -> " lt"
            t.endsWith("sayfa", ignoreCase = true) -> " sayfa"
            t.endsWith("kişi", ignoreCase = true) -> " kişi"
            t.endsWith("adet", ignoreCase = true) -> " adet"
            Regex("\\d\\s*m$", RegexOption.IGNORE_CASE).containsMatchIn(t) -> " m"
            else -> ""
        }
    }

    /**
     * Format a numeric value to match the style of the template option.
     */
    private fun formatLike(value: Double, template: String): String {
        val suffix = detectSuffix(template)
        val isInteger = abs(value - value.roundToInt()) < 1e-9 &&
            !template.contains(",") && !template.contains(".")
        return if (isInteger) {
            "${value.roundToInt()}$suffix"
        } else {
            String.format(Locale.US, "%.1f", value).replace(".", ",") + suffix
        }
    }

    /**
     * Generate realistic numeric distractors based on common student mistakes:
     * - off-by-one / off-by-two
     * - wrong operation (add instead of subtract)
     * - missing a step (half the difference)
     * - ratio/percentage error
     *
     * Returns exactly 3 unique wrong values, all different from [correct].
     */
    private fun generateNumericDistractors(correct: Double, seed: Int): List<Double> {
        val s = abs(seed)
        val magnitude = abs(correct).coerceAtLeast(1.0)

        // Pool of candidate wrong values — we generate many and pick the best 3
        val candidates = mutableListOf<Double>()

        // Off-by-small-integer errors
        candidates.add(correct + 1.0)
        candidates.add(correct - 1.0)
        candidates.add(correct + 2.0)
        candidates.add(correct - 2.0)
        candidates.add(correct + 3.0)

        // Percentage/ratio errors
        candidates.add(correct * 1.1)    // 10% too high
        candidates.add(correct * 0.9)    // 10% too low
        candidates.add(correct * 1.25)   // quarter more
        candidates.add(correct * 0.75)   // quarter less
        candidates.add(correct * 2.0)    // doubled (forgot to divide)
        candidates.add(correct * 0.5)    // halved (forgot to multiply)

        // Seed-dependent variations
        candidates.add(correct + (s % 7) + 1.0)
        candidates.add(correct - (s % 5) - 1.0)
        candidates.add(correct * (1.0 + 0.02 * (s % 8)))

        // Common student arithmetic mistakes
        if (correct > 10) {
            candidates.add(correct + 10)
            candidates.add(correct - 10)
        }

        // Filter: remove negatives when correct is positive, remove duplicates of correct
        val filtered = candidates
            .filter { abs(it - correct) > 0.01 }
            .filter { if (correct >= 0) it >= 0 else true }
            .distinctBy { "%.2f".format(it) }
            .sortedBy { abs(it - correct) } // prefer values close to correct

        // Pick 3 that are all distinct from each other
        val result = mutableListOf<Double>()
        for (c in filtered) {
            if (result.size >= 3) break
            if (result.none { abs(it - c) < 0.01 }) {
                result.add(c)
            }
        }
        // Safety: fill remaining with offset values
        var offset = 4.0
        while (result.size < 3) {
            val v = correct + offset
            if (result.none { abs(it - v) < 0.01 } && abs(v - correct) > 0.01) {
                result.add(v)
            }
            offset += 3.0
        }
        return result.take(3)
    }

    /**
     * Validate and repair distractors. Returns null if the question is unrecoverable
     * (all 4 options would be the same value).
     *
     * Rules:
     * - NO suffix-based fake distractors ("(yanlış yön)" etc.)
     * - All 4 options must be genuinely different
     * - For numeric questions: generate plausible arithmetic-error distractors
     * - For text questions: keep original if valid; reject question otherwise
     */
    fun fixDistractors(options: List<String>, stem: String, answerIndex: Int): List<String>? {
        if (options.size < 2) return null

        val cleaned = options.map { it.trim() }.toMutableList()
        while (cleaned.size < 4) cleaned.add("")
        val ai = answerIndex.coerceIn(0, cleaned.size - 1)
        val correct = cleaned[ai]

        if (correct.isBlank() || correct.length < 2) return null

        Log.d(TAG, "DISTRACTOR_PATH_USED fixDistractors called, correct='${correct.take(30)}', " +
            "options=[${cleaned.take(4).joinToString("|") { it.take(25) }}]")

        // Count how many distractors are broken
        val brokenIndices = mutableListOf<Int>()
        for (i in cleaned.indices) {
            if (i == ai) continue
            if (!isValidDistractor(cleaned[i])) {
                brokenIndices.add(i)
                Log.d(TAG, "DISTRACTOR_BROKEN idx=$i val='${cleaned[i].take(40)}'")
            }
        }

        // Check for duplicate values (same text appearing in multiple options)
        val valueSet = mutableSetOf<String>()
        valueSet.add(correct.lowercase(Locale("tr")).trim())
        for (i in cleaned.indices) {
            if (i == ai) continue
            val normalized = cleaned[i].lowercase(Locale("tr")).trim()
            if (normalized in valueSet) {
                if (i !in brokenIndices) {
                    brokenIndices.add(i)
                    Log.d(TAG, "DISTRACTOR_DUPLICATE idx=$i val='${cleaned[i].take(40)}'")
                }
            } else {
                valueSet.add(normalized)
            }
        }

        // If no broken distractors, return as-is
        if (brokenIndices.isEmpty()) {
            Log.d(TAG, "DISTRACTOR_SOURCE=original (all valid)")
            return cleaned.take(4)
        }

        Log.d(TAG, "DISTRACTOR_REPAIR needed: ${brokenIndices.size} broken indices=$brokenIndices")

        // Try numeric distractor generation
        val correctNum = extractNumber(correct)
        if (correctNum != null) {
            val seed = stem.hashCode()
            val wrongValues = generateNumericDistractors(correctNum, seed)
            var wIdx = 0
            for (i in brokenIndices) {
                if (wIdx >= wrongValues.size) break
                val oldVal = cleaned[i]
                cleaned[i] = formatLike(wrongValues[wIdx], correct)
                Log.d(TAG, "DISTRACTOR_REPLACED idx=$i old='${oldVal.take(30)}' new='${cleaned[i]}'")
                wIdx++
            }

            // Final validation: ensure all 4 are truly distinct
            val finalValues = cleaned.take(4).map { extractNumber(it) ?: Double.NaN }
            val distinctCount = finalValues.map { "%.2f".format(it) }.toSet().size
            if (distinctCount < 4) {
                Log.w(TAG, "DISTRACTOR_REJECT: numeric dedup failed for correct=$correct, " +
                    "values=${cleaned.take(4)}")
                return null
            }
            Log.d(TAG, "DISTRACTOR_SOURCE=generated_numeric final=${cleaned.take(4)}")
            return cleaned.take(4)
        }

        // Text-based question: if too many distractors are broken, reject the question
        Log.w(TAG, "DISTRACTOR_REJECT: ${brokenIndices.size} broken text distractors, " +
            "stem=${stem.take(60)}, broken=${brokenIndices.map { cleaned[it].take(30) }}")
        return null
    }
}
