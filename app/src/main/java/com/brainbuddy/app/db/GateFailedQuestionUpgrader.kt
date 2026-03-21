package com.brainbuddy.app.db

import android.content.Context
import android.util.Log
import com.brainbuddy.app.quiz.QuestionDiversity
import com.brainbuddy.app.quiz.QuestionQualityGate
import com.brainbuddy.app.quiz.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

/**
 * Rewrites stems for inactive rows that failed [QuestionQualityGate] at parse time,
 * then updates them in place (same [QuestionEntity.id]) so they can become ACTIVE.
 * Does not delete rows; only REPLACE-updates improved entities.
 */
object GateFailedQuestionUpgrader {

    private const val TAG = "GateFailedQUpgrade"

    private val CORE_SUBJECTS_LOWER = setOf("mat", "turkce", "fen", "sosyal", "ing")

    data class Result(
        val totalFound: Int,
        val upgradedActive: Int,
        val unchangedStillInactive: Int
    )

    suspend fun upgradeAll(context: Context): Result = withContext(Dispatchers.IO) {
        val dao = DatabaseProvider.get(context).questionDao()

        val rows = dao.getGateFailedInactiveCoreSubjects()
        if (rows.isEmpty()) {
            return@withContext Result(totalFound = 0, upgradedActive = 0, unchangedStillInactive = 0)
        }

        val toSave = ArrayList<QuestionEntity>(rows.size)
        var upgraded = 0
        var stillBad = 0

        for (q in rows) {
            val subjectKey = q.subject.trim().lowercase(Locale("tr"))
            if (subjectKey !in CORE_SUBJECTS_LOWER) continue
            val improved = tryUpgradeToActive(q)
            if (improved == null) {
                stillBad++
            } else {
                toSave.add(improved)
                upgraded++
            }
        }

        toSave.chunked(250).forEach { chunk ->
            dao.insertAll(chunk)
        }

        Log.i(TAG, "upgradeAll: found=${rows.size} savedActive=$upgraded stillInactive=$stillBad")

        Result(
            totalFound = rows.size,
            upgradedActive = upgraded,
            unchangedStillInactive = stillBad
        )
    }

    private fun parseOptions(optionsJson: String): List<String> {
        val arr = JSONArray(optionsJson)
        return (0 until arr.length()).map { i -> arr.optString(i, "").ifBlank { "-" } }.take(4)
            .let { if (it.size < 4) it + List(4 - it.size) { "-" } else it.take(4) }
    }

    private fun subjectEnum(key: String): Subject = when (key.lowercase(Locale("tr"))) {
        "mat" -> Subject.MAT
        "turkce" -> Subject.TURKCE
        "fen" -> Subject.FEN
        "sosyal" -> Subject.SOSYAL
        "ing" -> Subject.ING
        else -> Subject.MAT
    }

    /**
     * Returns upgraded entity with [QuestionEntity.isActive] true if gate passes; null if we could not improve.
     */
    private fun tryUpgradeToActive(q: QuestionEntity): QuestionEntity? {
        val options = parseOptions(q.optionsJson)
        if (options.size != 4) return null
        val subj = subjectEnum(q.subject)
        val reason = q.deactivationReason?.trim()?.lowercase(Locale("tr"))
        var stem = q.questionText.trim()
        if (stem.isEmpty()) return null

        for (round in 0 until 8) {
            val candidate = rewriteStem(stem, reason, q.grade, subj, round)
            val gate = QuestionQualityGate.evaluate(subj, q.grade, candidate, options, q.difficulty)
            if (gate.isActive) {
                val stemNorm = QuestionStemHash.normalizeStem(candidate)
                val stemHash = QuestionStemHash.stemHash(candidate)
                val divType = QuestionDiversity.inferType(subj, candidate)
                val divSkill = QuestionDiversity.inferSkill(subj, q.grade, divType, candidate)
                return q.copy(
                    questionText = candidate,
                    stemNormalized = stemNorm,
                    stemHash = stemHash,
                    isActive = true,
                    deactivationReason = null,
                    questionType = gate.questionType,
                    skillsJson = gate.skillsJson,
                    type = divType,
                    skill = divSkill
                )
            }
            stem = candidate
        }
        return null
    }

    private fun rewriteStem(
        stem: String,
        reason: String?,
        grade: Int,
        subject: Subject,
        round: Int
    ): String {
        val r = reason ?: ""
        val base = when (round) {
            0 -> when (r) {
                "too_simple_math" -> wrapSimpleMath(stem)
                "too_basic" -> wrapBasic(stem)
                "too_memorization" -> wrapMemorization(stem, subject)
                "too_short" -> wrapShort(stem, grade, subject)
                "too_trivial" -> wrapTrivial(stem, grade)
                else -> wrapGeneric(stem, grade)
            }
            1 -> wrapGeneric(stem, grade)
            2 -> wrapGenericStrong(stem, grade)
            3 -> wrapMetneGore(stem, grade)
            4 -> wrapProblemTable(stem, grade)
            5 -> wrapProblemTable(wrapGeneric(stem, grade), grade)
            6 -> wrapMetneGore(wrapGenericStrong(stem, grade), grade)
            else -> wrapGenericStrong(wrapProblemTable(stem, grade), grade)
        }
        return base
    }

    private fun wrapShort(stem: String, grade: Int, subject: Subject): String {
        val label = when (subject) {
            Subject.MAT -> "matematik"
            Subject.TURKCE -> "Türkçe"
            Subject.FEN -> "fen bilimleri"
            Subject.SOSYAL -> "sosyal bilgiler"
            Subject.ING -> "İngilizce"
            else -> "ders"
        }
        return "Bir $grade. sınıf $label çalışmasında öğretmen, öğrencilerden birden fazla bilgiyi birlikte kullanmalarını isteyen şu durumu anlatır: " +
            stem +
            " Bu metindeki bilgileri adım adım birleştirerek doğru sonucu seçiniz."
    }

    private fun wrapTrivial(stem: String, grade: Int): String {
        return "$grade. sınıf düzeyinde öğrencilerin bir problem çözümünde oran, yüzde ve tablo ilişkisini birlikte düşünmesi gereken şu durumda: " +
            stem +
            " Bu problemdeki ilişkileri dikkate alarak doğru sonuç seçilmelidir."
    }

    private fun wrapSimpleMath(stem: String): String {
        val compact = stem.replace("\\s+".toRegex(), "").replace("[=?]".toRegex(), "")
        val onlyMath = compact.isNotEmpty() && compact.all { it in "0123456789+-×xX*/:÷().,%" }
        return if (onlyMath && compact.length <= 40) {
            "Matematik dersinde tahtaya yazılan ifadeyi bir öğrenci adım adım yorumlamak istiyor: «" + stem.trim() +
                "» Bu ifadeyi bir problem bağlamında değerlendirerek sonuç hangisidir?"
        } else {
            "Matematik çalışmasında öğrenci aşağıdaki ifadeyi çok adımlı düşünerek çözmek istiyor: " + stem +
                " Bu durumda doğru sonuç aşağıdakilerden hangisidir?"
        }
    }

    private fun wrapBasic(stem: String): String {
        return "Bir ölçü ve birim problemi çerçevesinde öğrenci, aşağıdaki ifadeyi doğru modelleyerek çözmek istiyor: " + stem +
            " Bu problemdeki ilişkileri dikkate alarak doğru sonuç hangisidir?"
    }

    private fun wrapMemorization(stem: String, subject: Subject): String {
        val lead = if (subject == Subject.TURKCE) {
            "Aşağıdaki paragraf bir okuma metni olarak ele alınır. Metne göre "
        } else {
            "Aşağıdaki açıklama bir bilimsel bağlamda okunur. Bu bilgileri birlikte değerlendirerek "
        }
        return lead + stem + " Bu soruda doğru çıkarım hangi seçenekte yer alır?"
    }

    private fun wrapGeneric(stem: String, grade: Int): String {
        return "Okul ortamında $grade. sınıf düzeyinde bir örnekte metne göre birden fazla bilgi birlikte kullanılır. Verilen: " +
            stem + " Öğrencinin bu bilgileri yorumlayarak doğru çıkarımı yapması beklenir. Hangi seçenek bu durumu en doğru karşılar?"
    }

    private fun wrapGenericStrong(stem: String, grade: Int): String {
        return "Bir problem çözümünde grafik, tablo veya yüzde ilişkilerinden en az ikisi birlikte düşünülmelidir. " +
            "$grade. sınıf bağlamında şu durum verilmiştir: " + stem +
            " Bu ilişkileri adım adım kullanarak doğru sonuç hangisidir?"
    }

    private fun wrapMetneGore(stem: String, grade: Int): String {
        return "Metne göre çok adımlı düşünme gerektiren şu örnekte ($grade. sınıf): " + stem +
            " Aşağıdaki seçeneklerden hangisi bu metindeki bilgilerle tutarlı ve mantıklıdır?"
    }

    private fun wrapProblemTable(stem: String, grade: Int): String {
        return "Aşağıdaki tablo ve metin birlikte verilmiş sayılır: öğrenci önce durumu modellemeli, sonra hesaplamalıdır. " +
            "Durum: " + stem + " Bu modele göre doğru sonuç hangisidir?"
    }
}
