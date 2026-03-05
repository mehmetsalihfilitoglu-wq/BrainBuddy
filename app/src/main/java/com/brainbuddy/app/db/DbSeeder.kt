package com.brainbuddy.app.db

import android.content.Context
import android.util.Log
import com.brainbuddy.app.quiz.QuestionQualityGate
import com.brainbuddy.app.quiz.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset

/**
 * Seeds Room DB from JSON on first install.
 * Idempotent: app_meta["db_seeded"] == "true" skips.
 */
object DbSeeder {
    private const val TAG = "DbSeeder"
    private const val KEY_DB_SEEDED = "db_seeded"
    private const val TARGET_QUESTIONS_PER_SUBJECT = 500

    /** Desteklenen ders anahtarları (DB'ye bu kısa kodlarla yazılır). */
    private val SUBJECT_KEYS = listOf("mat", "turkce", "fen", "sosyal", "ing")

    suspend fun seedIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val meta = db.appMetaDao()
        if (meta.get(KEY_DB_SEEDED) == "true") {
            Log.d(TAG, "Already seeded, skip")
            return@withContext false
        }
        val questions = mutableListOf<QuestionEntity>()
        try {
            questions.addAll(loadFromAssets(context))
            val imported = loadFromImported(context)
            val existingIds = questions.map { it.id }.toSet()
            imported.filter { it.id !in existingIds }.forEach { questions.add(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Seed load error", e)
        }
        if (questions.isEmpty()) {
            questions.addAll(getFallbackEntities())
        }
        // İlk seed: INSERT IGNORE (id unique) - mevcut kayıtları ezmez.
        val questionDao = db.questionDao()
        questionDao.insertAllIgnore(questions)
        meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
        Log.i(TAG, "Seeded ${questions.size} questions (INSERT IGNORE by id)")

        // Import sonrası havuz doğrulama
        try {
            validatePoolCoverage(questionDao)
        } catch (e: Exception) {
            Log.w(TAG, "Pool validation failed: ${e.message}")
        }
        true
    }

    private fun loadFromAssets(context: Context): List<QuestionEntity> {
        val all = mutableListOf<QuestionEntity>()

        // 1) Ana gövde: mevcut birleşik havuz (geriyle uyumlu kalır).
        try {
            val json = context.assets.open("questions_tr.json").use { input ->
                input.readBytes().toString(Charset.forName("UTF-8"))
            }
            all += parseJsonArray(JSONArray(json))
        } catch (e: Exception) {
            Log.e(TAG, "questions_tr.json error", e)
        }

        // 2) Pilot grade 6 paketleri (ders bazlı).
        val packFiles = listOf(
            "packs/grade6_mat.json",
            "packs/grade6_turkce.json",
            "packs/grade6_fen.json",
            "packs/grade6_sosyal.json",
            "packs/grade6_ing.json"
        )
        packFiles.forEach { assetPath ->
            try {
                val json = context.assets.open(assetPath).use { input ->
                    input.readBytes().toString(Charset.forName("UTF-8"))
                }
                all += parseJsonArray(JSONArray(json))
                Log.i(TAG, "Loaded pack from $assetPath")
            } catch (e: Exception) {
                Log.w(TAG, "Pack load error for $assetPath: ${e.message}")
            }
        }

        return all
    }

    private fun loadFromImported(context: Context): List<QuestionEntity> {
        val file = java.io.File(context.filesDir, "imported_questions.json")
        if (!file.exists()) return emptyList()
        return try {
            val json = file.readText(Charsets.UTF_8)
            parseJsonArray(JSONArray(json))
        } catch (e: Exception) {
            Log.e(TAG, "imported load error", e)
            emptyList()
        }
    }

    private fun parseJsonArray(arr: JSONArray): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                out.add(parseQuestionObject(o, i))
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed index $i: ${e.message}")
            }
        }
        return out
    }

    private fun getFallbackEntities(): List<QuestionEntity> {
        return listOf(
            QuestionEntity(
                id = "fb1",
                grade = 6,
                subject = "mat",
                difficulty = 1,
                questionText = "12 × 15 işleminin sonucu kaçtır?",
                optionsJson = "[\"160\",\"170\",\"180\",\"190\"]",
                answerIndex = 2,
                explanation = "12×10=120, 12×5=60",
                isActive = true,
                version = 1,
                examType = "GENERAL",
                imageAsset = null
            ),
            QuestionEntity(
                id = "fb2",
                grade = 6,
                subject = "turkce",
                difficulty = 1,
                questionText = "Türkiye'nin başkenti neresidir?",
                optionsJson = "[\"İstanbul\",\"İzmir\",\"Ankara\",\"Bursa\"]",
                answerIndex = 2,
                explanation = "Mustafa Kemal Atatürk'ün kararıyla.",
                isActive = true,
                version = 1,
                examType = "GENERAL",
                imageAsset = null
            ),
            QuestionEntity(
                id = "fb3",
                grade = 6,
                subject = "fen",
                difficulty = 1,
                questionText = "Güneş sisteminde Dünya'dan sonra gelen gezegen hangisidir?",
                optionsJson = "[\"Venüs\",\"Mars\",\"Jüpiter\",\"Satürn\"]",
                answerIndex = 1,
                explanation = "Merkür, Venüs, Dünya, Mars...",
                isActive = true,
                version = 1,
                examType = "GENERAL",
                imageAsset = null
            ),
            QuestionEntity(
                id = "fb4",
                grade = 6,
                subject = "ing",
                difficulty = 1,
                questionText = "\"Hello\" kelimesinin Türkçe karşılığı nedir?",
                optionsJson = "[\"Hoşça kal\",\"Merhaba\",\"Teşekkürler\",\"Evet\"]",
                answerIndex = 1,
                explanation = "Selamlama sözcüğü.",
                isActive = true,
                version = 1,
                examType = "GENERAL",
                imageAsset = null
            )
        )
    }

    /**
     * JSON formatı (standart):
     * {
     *   id: String,
     *   grade: Int (2..8),
     *   subject: String ("mat","turkce","fen","sosyal","ing" kısa kodları),
     *   difficulty: Int (0=EASY, 1=MEDIUM, 2=HARD),
     *   stem: String (soru kökü),
     *   options: List<String> (tam olarak 4 şık),
     *   correctIndex: Int (0..3),
     *   explanation: String (yalnızca veli görünümü için, optional)
     * }
     *
     * Geriye dönük uyumluluk için eski alanları da okur:
     * - questionText yerine stem
     * - choices yerine options
     * - answerIndex yerine correctIndex
     * - hint yerine explanation
     */
    private fun parseQuestionObject(o: JSONObject, index: Int): QuestionEntity {
        // grade:
        // 1) JSON'da "grade" varsa ve 2..8 aralığındaysa doğrudan kullan
        // 2) Yoksa/Geçersizse gradeTag/grade_level gibi string alanlardan parse etmeyi dene
        // 3) Parse edilemezse soruyu discard etmek için exception fırlat (default 6 yok)
        val gradeFromJson = when {
            o.has("grade") -> o.optInt("grade", 0)
            o.has("grade_level") -> o.optInt("grade_level", 0)
            else -> 0
        }
        val grade = when {
            gradeFromJson in 2..8 -> gradeFromJson
            else -> {
                val gradeTagStr = o.optString("gradeTag", o.optString("grade_level", ""))
                val gradeTag = gradeTagStr.toIntOrNull()
                (gradeTag ?: 0).coerceIn(2, 8).takeIf { it in 2..8 }
                    ?: throw IllegalArgumentException("Invalid grade for question index=$index")
            }
        }

        // subject normalize -> mat/turkce/fen/sosyal/ing
        val rawSubject = o.optString("subject", "").ifBlank {
            throw IllegalArgumentException("Missing subject for question index=$index")
        }
        val subjectKey = when (rawSubject.trim().lowercase()) {
            "mat", "matematik", "math" -> "mat"
            "turkce", "türkçe", "tr" -> "turkce"
            "fen", "fen bilimleri" -> "fen"
            "sosyal", "sosyal bilgiler" -> "sosyal"
            "ing", "ingilizce", "ingilizce dersi", "english", "eng" -> "ing"
            else -> throw IllegalArgumentException("Unsupported subject '$rawSubject' at index=$index")
        }

        // question text: stem (yeni şema) veya questionText (eski)
        val questionText = o.optString("stem", "").ifBlank {
            o.optString("questionText", "")
        }.ifBlank {
            throw IllegalArgumentException("Missing questionText/stem at index=$index")
        }

        // options: options (yeni) veya choices (eski)
        val optionsArray = when {
            o.has("options") -> o.optJSONArray("options")
            else -> o.optJSONArray("choices")
        } ?: throw IllegalArgumentException("Missing options/choices array at index=$index")

        val rawOptions = (0 until optionsArray.length())
            .map { idx -> optionsArray.optString(idx, "").ifEmpty { optionsArray.opt(idx)?.toString() ?: "" } }
            .filter { it.isNotBlank() }
        if (rawOptions.size < 2) {
            throw IllegalArgumentException("Not enough options at index=$index")
        }
        val padded = if (rawOptions.size >= 4) rawOptions.take(4) else rawOptions + List(4 - rawOptions.size) { "-" }
        val optionsJson = JSONArray(padded).toString()

        // answer index: answerIndex (yeni) veya correctIndex (eski)
        val rawAnswerIndex = if (o.has("answerIndex")) {
            o.optInt("answerIndex", 0)
        } else {
            o.optInt("correctIndex", 0)
        }
        val answerIndex = rawAnswerIndex.coerceIn(0, padded.size - 1)

        // difficulty: int (0..2) veya eski string enum.
        // Eski verilerdeki 3 (VERY_HARD) değeri HARD (2) olarak normalize edilir.
        val difficulty = when {
            // Yeni format: doğrudan 0..2 int
            o.has("difficulty") && o.opt("difficulty") is Int -> {
                val raw = o.optInt("difficulty", 1)
                when {
                    raw <= 0 -> 0
                    raw == 1 -> 1
                    else -> 2 // 2 ve üzeri değerler HARD olarak toplanır
                }
            }
            else -> {
                // Eski string tabanlı format
                when (o.optString("difficulty", "MEDIUM")) {
                    "EASY" -> 0
                    "HARD", "VERY_HARD" -> 2
                    else -> 1
                }
            }
        }

        val explanation = when {
            o.has("explanation") -> o.optString("explanation", "").takeIf { it.isNotBlank() }
            else -> o.optString("hint", "").takeIf { it.isNotBlank() }
        }

        val examType = o.optString("examType", "GENERAL").takeIf { it.isNotBlank() }
        val imageAsset = o.optString("imageAsset", "").takeIf { it.isNotBlank() }

        // id: varsa kullan, yoksa grade+subject+index tabanlı üret
        val explicitId = o.optString("id", "").takeIf { it.isNotBlank() }
        val id = explicitId ?: "${grade}_${subjectKey}_${(index + 1).toString().padStart(6, '0')}"

        // Kalite gate: düşük kaliteli soruları pasifleştir, deactivationReason sakla.
        val subjectEnum = when (subjectKey) {
            "mat" -> Subject.MAT
            "turkce" -> Subject.TURKCE
            "fen" -> Subject.FEN
            "sosyal" -> Subject.SOSYAL
            "ing" -> Subject.ING
            else -> Subject.MAT
        }
        val gate = QuestionQualityGate.evaluate(
            subject = subjectEnum,
            grade = grade,
            questionText = questionText,
            options = padded
        )

        return QuestionEntity(
            id = id,
            grade = grade,
            subject = subjectKey,
            difficulty = difficulty,
            questionText = questionText,
            optionsJson = optionsJson,
            answerIndex = answerIndex,
            explanation = explanation,
            isActive = gate.isActive,
            questionType = gate.questionType,
            skillsJson = gate.skillsJson,
            deactivationReason = gate.deactivationReason,
            version = 1,
            examType = examType,
            imageAsset = imageAsset
        )
    }

    /**
     * Import sonrası doğrulama:
     * Her grade (2..8) × her subject için COUNT >= TARGET_QUESTIONS_PER_SUBJECT değilse
     * debug log + warning üretir.
     */
    private suspend fun validatePoolCoverage(questionDao: QuestionDao) {
        val counts = questionDao.getCountsByGradeSubject()
        val byKey = counts.associateBy { it.grade to it.subject.lowercase() }

        val shortages = mutableListOf<String>()
        for (grade in 2..8) {
            for (subject in SUBJECT_KEYS) {
                val entry = byKey[grade to subject]
                val count = entry?.count ?: 0
                Log.d(TAG, "Pool stat grade=$grade subject=$subject count=$count")
                if (count < TARGET_QUESTIONS_PER_SUBJECT) {
                    val msg = "Question pool below target: grade=$grade subject=$subject count=$count (<$TARGET_QUESTIONS_PER_SUBJECT)"
                    shortages.add(msg)
                    Log.w(TAG, msg)
                }
            }
        }
        if (shortages.isEmpty()) {
            Log.d(TAG, "Question pool OK for all grade+subject combinations (2..8)")
        } else {
            Log.w(TAG, "Question pool has shortages for ${shortages.size} grade+subject combinations. See warnings above for details.")
        }
    }
}
