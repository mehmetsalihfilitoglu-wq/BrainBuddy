package com.brainbuddy.app.quiz

import com.brainbuddy.app.db.QuestionEntity
import com.brainbuddy.app.db.QuestionMapper
import java.util.Locale
import kotlin.random.Random

/**
 * Runtime-only enrichment: stem upgrades, distractor repair, tier ordering, quiz balance.
 * Does not persist changes to the database.
 */
object AdaptiveQuizRuntime {

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
        val upgraded = upgradeStemIfWeak(subj, entity.grade, entity.questionText)
        return upgraded to (upgraded != null)
    }

    /**
     * Normalize option lengths and add plausible distractors when clearly broken.
     */
    fun fixDistractors(options: List<String>, @Suppress("UNUSED_PARAMETER") stem: String, answerIndex: Int): List<String> {
        if (options.size < 2) return options
        val o = options.map { it.trim() }.filter { it.isNotBlank() && it != "-" }.toMutableList()
        while (o.size < 4) o.add("-")
        val ai = answerIndex.coerceIn(0, o.size - 1)
        val correct = o[ai]
        if (correct.length < 2) return options
        val targetLen = o.maxOf { it.length }.coerceAtLeast(12)
        for (i in o.indices) {
            if (i == ai) continue
            if (o[i].length < 3 || o[i] == "-") {
                val hint = correct.take(20)
                o[i] = "${hint.take(8)}… (yanlış yön)" + " ".repeat((targetLen - o[i].length).coerceAtLeast(0) % 4)
            }
        }
        val mean = o.map { it.length }.average()
        if (o.any { kotlin.math.abs(it.length - mean) > mean * 0.9 && mean > 10 }) {
            val pad = mean.toInt().coerceIn(12, 48)
            o.indices.forEach { j ->
                if (o[j].length < pad / 2) o[j] = o[j] + " ".repeat((pad - o[j].length).coerceIn(0, 8))
            }
        }
        return o.take(4)
    }
}
