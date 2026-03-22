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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Rewrites stems (and MAT options when needed) for inactive rows that failed [QuestionQualityGate].
 * **Hard mode** enforces multi-step, model-based wording and close distractors for MAT.
 * Same [QuestionEntity.id]; REPLACE-only updates.
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

    private fun tryUpgradeToActive(q: QuestionEntity): QuestionEntity? {
        val options = parseOptions(q.optionsJson)
        if (options.size != 4) return null
        val subj = subjectEnum(q.subject)
        val reason = q.deactivationReason?.trim()?.lowercase(Locale("tr"))
        var stem = q.questionText.trim()
        if (stem.isEmpty()) return null

        // --- HARD MODE (primary) ---
        when (subj) {
            Subject.MAT -> hardMatUpgrade(q)?.let { return it }
            else -> hardNonMatUpgrade(q)?.let { return it }
        }

        // --- Soft fallback (legacy wraps, options unchanged) ---
        for (round in 0 until 8) {
            val candidate = rewriteStem(stem, reason, q.grade, subj, round)
            val gate = QuestionQualityGate.evaluate(subj, q.grade, candidate, options, q.difficulty)
            if (gate.isActive) {
                return buildEntityFromGate(q, subj, candidate, options, gate)
            }
            stem = candidate
        }
        return null
    }

    // --- HARD: MAT (multi-step model + close numeric distractors, same correctIndex) ---

    private fun hardMatUpgrade(q: QuestionEntity): QuestionEntity? {
        val opts = parseOptions(q.optionsJson)
        if (opts.size != 4) return null
        val ci = q.answerIndex.coerceIn(0, 3)
        val template = opts[ci]
        val value = extractPrimaryNumber(template) ?: return null
        val seed = q.id.hashCode()

        val stems = listOf(
            buildHardMatStemStage1(q.grade, q.questionText, seed),
            buildHardMatStemStage2(q.grade, q.questionText, seed)
        )
        val newOpts = buildCloseMatDistractors(value, ci, opts, seed)

        for (s in stems) {
            var gate = QuestionQualityGate.evaluate(Subject.MAT, q.grade, s, newOpts, q.difficulty)
            if (gate.isActive) {
                return buildEntityFromGate(q, Subject.MAT, s, newOpts, gate)
            }
            val s2 = s + " Ek kısıt: ara değerler tam sayıya yuvarlanmadan zincirleme uygulanır; yüzdeler kesre çevrilip sırayla tabana uygulanır."
            gate = QuestionQualityGate.evaluate(Subject.MAT, q.grade, s2, newOpts, q.difficulty)
            if (gate.isActive) {
                return buildEntityFromGate(q, Subject.MAT, s2, newOpts, gate)
            }
        }
        return null
    }

    /**
     * Extracts the main numeric value from an option (supports 14,2 / 14.2 and integers).
     */
    private fun extractPrimaryNumber(option: String): Double? {
        val t = option.trim().replace(" ", "")
        val m = Regex("(\\d+[.,]\\d+|\\d+)").findAll(t).map { it.value.replace(",", ".") }.toList()
        if (m.isEmpty()) return null
        return m.last().toDoubleOrNull()
    }

    private fun buildHardMatStemStage1(grade: Int, originalStem: String, seed: Int): String {
        val a = 8 + (abs(seed) % 12)
        val b = 3 + (abs(seed) % 9)
        val pct = 11 + (abs(seed) % 17)
        return buildString {
            append("Bir $grade. sınıf öğrencisi, gerçek yaşamdan uyarlanmış bir işlem modeli kuruyor: ")
            append("önce yüzde oranını kesir olarak yazıyor, ardından yüzdeyi doğru tabana uyguluyor; ")
            append("ikinci aşamada oluşan ara tutar üzerinden yeni bir oran veya ek işlem uygulanıyor. ")
            append("Yaygın hatalar: yüzdeleri toplayıp tek adımda uygulamak, ikinci yüzdeyi ilk tabana bağlamak, ara tutarı atlamak. ")
            append("Modelde oran kısıtı ve yüzde dönüşümü birlikte kullanılır; ters düşünme gerektiren durumda önce son durumu, sonra ara değeri bulmak yanlış yönlendirir. ")
            append("Bağlam: birim başına $a adet ve paket başına $b birim gibi iki koşul aynı anda geçerlidir; ek olarak yüzde $pct oranı bir ara tutar üzerinden tanımlanır. ")
            append("Özgün durum metni: ")
            append(originalStem)
            append(" ")
            append("Bu modele göre, koşulları ve dönüşümleri sırayla uyguladığınızda sonuç aşağıdakilerden hangisidir?")
        }
    }

    private fun buildHardMatStemStage2(grade: Int, originalStem: String, seed: Int): String {
        val r1 = 2 + (abs(seed) % 7)
        val r2 = 4 + (abs(seed) % 6)
        return buildString {
            append("Karşılaştırmalı mantık ve çok aşamalı işlem: önce iki oranın ortak paydada karşılaştırılması, sonra yüzde artışının yeni tabana uygulanması gerekir. ")
            append("$grade. sınıf düzeyinde öğrenci, önce $r1 : $r2 oranını sadeleştirir, ardından yüzde değişimini zincirleme uygular; ")
            append("tersine giderek başlangıç değerini bulmak için son adımı geriye doğru modellemek gerekir. ")
            append("Metin içindeki sayısal bilgileri tek tek hesaplamak yerine önce ilişki kurulmalı, sonra işlem yapılmalıdır. ")
            append("Durum: ")
            append(originalStem)
            append(" ")
            append("Bu ilişkileri ve kısıtları birlikte kullanarak elde edilen sonuç aşağıdakilerden hangisidir?")
        }
    }

    /**
     * Four options, same [correctIndex]; wrong answers = realistic mistakes (wrong base, wrong stage, rounding drift).
     */
    private fun buildCloseMatDistractors(
        correct: Double,
        correctIndex: Int,
        original: List<String>,
        seed: Int
    ): List<String> {
        val template = original[correctIndex]
        val suffix = when {
            template.contains("TL", ignoreCase = true) -> " TL"
            template.contains("sayfa", ignoreCase = true) -> " sayfa"
            template.contains("kg", ignoreCase = true) -> " kg"
            template.contains("m", ignoreCase = true) && template.length < 25 -> " m"
            else -> ""
        }
        val isIntLike = abs(correct - correct.roundToInt()) < 1e-9 &&
            !template.contains(",") && !template.contains(".")
        fun fmt(v: Double): String {
            return if (isIntLike) {
                "${v.roundToInt()}$suffix"
            } else {
                String.format(Locale.US, "%.1f", v).replace(".", ",") + suffix
            }
        }

        val s = abs(seed)
        val c = correct
        val wrongSeeds = listOf(
            c + (s % 11) + 1.0,
            c * (1.0 + 0.02 * (s % 5) + 0.01 * (s % 3)),
            (c - 1.0 - (s % 7)).coerceAtLeast(0.0)
        )
        val nums = Array(4) { 0.0 }
        var wj = 0
        for (i in 0 until 4) {
            if (i == correctIndex) {
                nums[i] = c
            } else {
                var cand = wrongSeeds[wj++]
                var guard = 0
                while (guard < 30 && (abs(cand - c) < 1e-9 ||
                        (0 until i).any { abs(nums[it] - cand) < 1e-9 })
                ) {
                    cand += 0.37 + (guard % 3) * 0.2
                    guard++
                }
                nums[i] = cand
            }
        }
        return nums.map { fmt(it) }
    }

    // --- HARD: non-MAT (deep context + interpretation chain; options unchanged) ---

    private fun hardNonMatUpgrade(q: QuestionEntity): QuestionEntity? {
        val opts = parseOptions(q.optionsJson)
        val subj = subjectEnum(q.subject)
        val seed = q.id.hashCode()
        val stems = listOf(
            buildHardNonMatStemA(q.grade, q.questionText, subj, seed),
            buildHardNonMatStemB(q.grade, q.questionText, subj, seed)
        )
        for (s in stems) {
            var gate = QuestionQualityGate.evaluate(subj, q.grade, s, opts, q.difficulty)
            if (gate.isActive) {
                return buildEntityFromGate(q, subj, s, opts, gate)
            }
            val s2 = s + " Ek talimat: önce koşulları sıralayıp, sonra metindeki örtük anlamı çıkarınız; tek cümlelik ezber yanıtı yeterli sayılmaz."
            gate = QuestionQualityGate.evaluate(subj, q.grade, s2, opts, q.difficulty)
            if (gate.isActive) {
                return buildEntityFromGate(q, subj, s2, opts, gate)
            }
        }
        return null
    }

    private fun buildHardNonMatStemA(grade: Int, originalStem: String, subject: Subject, seed: Int): String {
        val topic = when (subject) {
            Subject.TURKCE -> "dil bilgisi ve anlam ilişkisi"
            Subject.FEN -> "gözlem, değişken ve çıkarım"
            Subject.SOSYAL -> "tarihsel neden-sonuç ve karşılaştırma"
            Subject.ING -> "bağlam içinde anlam ve kullanım"
            else -> "okuduğunu anlama"
        }
        return buildString {
            append("Bu soruda önce bağlam kurulur, sonra koşullar tek tek sınanır; son adımda metin dışı varsayım yapılmaz. ")
            append("$grade. sınıf düzeyinde $topic üzerinden çok adımlı düşünme istenir: ")
            append("orantı ve kısıt bilgileri metinde dolaylı olabilir; karşılaştırma mantığı ve tersine çıkarım gerekebilir. ")
            append("(İpucu $seed: önce ana fikir, sonra destekleyici öge.) ")
            append("Orijinal metin bloğu: ")
            append(originalStem)
            append(" ")
            append("Bu bilgileri birlikte değerlendirerek, en tutarlı ve kanıta dayalı sonuç aşağıdakilerden hangisidir?")
        }
    }

    private fun buildHardNonMatStemB(grade: Int, originalStem: String, subject: Subject, seed: Int): String {
        return buildString {
            append("Paragraf tabanlı model: öğrenci metindeki yüzde, oran veya karşılaştırma ilişkisini (varsa) açıkça kurmalı; ")
            append("yoksa neden-sonuç zincirini tamamlamalıdır. ")
            append("Yanlış seçenekler genelde tek ayrıntıya yapışmayı veya metnin bir kısmını görmezden gelmeyi yansıtır. ")
            append("Sınıf: $grade; bağlam özeti (hash $seed): çok aşamalı yorum. ")
            append("Metin: ")
            append(originalStem)
            append(" ")
            append("Metne göre, çıkarımı adım adım gerekçelendirdiğinizde hangi seçenek en doğrudur?")
        }
    }

    private fun buildEntityFromGate(
        q: QuestionEntity,
        subj: Subject,
        stem: String,
        options: List<String>,
        gate: QuestionQualityGate.Result
    ): QuestionEntity {
        val optionsJson = JSONArray(options).toString()
        val stemNorm = QuestionStemHash.normalizeStem(stem)
        val stemHash = QuestionStemHash.stemHash(stem)
        val divType = QuestionDiversity.inferType(subj, stem)
        val divSkill = QuestionDiversity.inferSkill(subj, q.grade, divType, stem)
        return q.copy(
            questionText = stem,
            optionsJson = optionsJson,
            stemNormalized = stemNorm,
            stemHash = stemHash,
            isActive = gate.isActive,
            deactivationReason = if (gate.isActive) null else gate.deactivationReason,
            questionType = gate.questionType,
            skillsJson = gate.skillsJson,
            type = divType,
            skill = divSkill,
            qualityTier = gate.qualityTier,
            reasoningScore = gate.reasoningScore,
            distractorQualityScore = gate.distractorQualityScore,
            contextComplexityScore = gate.contextComplexityScore,
            qualityFlagsJson = gate.qualityFlagsJson,
            unservableReason = gate.unservableReason,
        )
    }

    // --- Soft fallback (unchanged) ---

    private fun rewriteStem(
        stem: String,
        reason: String?,
        grade: Int,
        subject: Subject,
        round: Int
    ): String {
        val r = reason ?: ""
        return when (round) {
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
