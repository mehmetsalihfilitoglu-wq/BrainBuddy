package com.brainbuddy.app.quiz

import com.brainbuddy.app.db.QuestionEntity
import com.brainbuddy.app.db.QuestionMapper
import org.json.JSONArray
import java.util.Locale

/**
 * Runtime quarantine for items that should not appear in normal serving.
 * Does not delete rows; prefer persisting [QuestionEntity.unservableReason] when possible.
 */
object LowQualityQuarantine {

    const val REASON_CODE = "LOW_QUALITY_QUARANTINED"

    fun optionsFromEntity(entity: QuestionEntity): List<String> = try {
        val arr = JSONArray(entity.optionsJson)
        List(arr.length()) { arr.getString(it).trim() }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * True if this row should be skipped for normal pool serving (grade or LGS).
     */
    /** Caller must skip rows with non-null [QuestionEntity.unservableReason] before calling this. */
    fun shouldQuarantine(entity: QuestionEntity): Boolean {
        val subj = QuestionMapper.mapSubject(entity.subject)
        val opts = optionsFromEntity(entity)
        val cls = QuestionQualityClassifier.classify(
            subj,
            entity.grade,
            entity.questionText,
            opts,
            entity.difficulty.coerceIn(0, 2),
            entity.answerIndex,
        )
        if (cls.qualityTier == QuestionQualityClassifier.TIER_EASY) return true
        if (cls.distractorQualityScore < QuizQualityPolicy.MIN_DISTRACTOR_SCORE_SERVE) return true
        if (cls.reasoningScore < QuizQualityPolicy.MIN_REASONING_SCORE_SERVE) return true
        if (QuestionQualityGate.isTrivial(subj, entity.grade, entity.questionText, opts, entity.difficulty, entity.answerIndex)) {
            return true
        }
        return subjectHardRules(subj, entity)
    }

    private fun subjectHardRules(subj: Subject, entity: QuestionEntity): Boolean {
        val stem = entity.questionText
        val lower = stem.lowercase(Locale("tr"))
        return when (subj) {
            Subject.MAT -> matTooShallow(stem, lower)
            Subject.TURKCE -> turkceTooShallow(lower, stem.length)
            Subject.FEN -> fenMemorizationOnly(lower, stem.length)
            Subject.SOSYAL -> sosyalRecallOnly(lower)
            Subject.ING -> ingTooShallow(lower, stem.length)
            else -> false
        }
    }

    private fun matTooShallow(stem: String, lower: String): Boolean {
        val compact = stem.replace("\\s+".toRegex(), "")
        val ops = Regex("[+\\-×*/÷]").findAll(compact).count()
        val oneStep = ops <= 1 && stem.length < 70 && Regex("\\d").containsMatchIn(stem)
        val banned = Regex("alan|çevre|dikdörtgen|kare|üçgen|genişlik|uzunluk|yükseklik").containsMatchIn(lower) &&
            ops <= 1 && stem.length < 100
        val avg = Regex("ortalama|aritmetik ortalama").containsMatchIn(lower) && stem.length < 90
        val expand = Regex("\\([^)]+\\)\\s*\\([^)]+\\)|çarpanlarına ayır").containsMatchIn(lower) && stem.length < 85
        return oneStep || banned || avg || expand
    }

    private fun turkceTooShallow(lower: String, len: Int): Boolean {
        val noInference = !Regex("çıkarım|örtük|ima|yorum|anlam|metne göre|parçaya göre|hangisi olamaz|karşılaştır").containsMatchIn(lower)
        return len in 1 until 120 && noInference && Regex("paragraf|metin|aşağıda").containsMatchIn(lower)
    }

    private fun fenMemorizationOnly(lower: String, len: Int): Boolean {
        val noContext = !Regex("deney|değişken|grafik|tablo|gözlem|neden|sonuç|hipotez|yorum|çıkarım").containsMatchIn(lower)
        return len < 95 && noContext && Regex("hangi gaz|nedir|tanım|fotosentez|hangi organel|mitokondri").containsMatchIn(lower)
    }

    private fun sosyalRecallOnly(lower: String): Boolean {
        return Regex("hangi yıl|hangi tarih|mondros|lozan|kimdir|başkent|hangi kıta|nerededir|antlaşma").containsMatchIn(lower)
    }

    private fun ingTooShallow(lower: String, len: Int): Boolean {
        val beVerb = Regex("\\b(am|is|are|was|were)\\b").containsMatchIn(lower)
        val noCtx = !Regex("paragraph|passage|according|infer|imply|context|meaning|dialogue|conversation").containsMatchIn(lower)
        return len < 100 && beVerb && noCtx
    }
}
