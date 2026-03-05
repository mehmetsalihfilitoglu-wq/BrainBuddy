package com.brainbuddy.app.db

import android.content.Context
import android.util.Log
import com.brainbuddy.app.quiz.QuestionQualityGate
import com.brainbuddy.app.quiz.QuestionDiversity
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
    private const val KEY_DB_SEED_VERSION = "db_seed_version"
    private const val CURRENT_DB_SEED_VERSION = 2
    private const val TARGET_QUESTIONS_PER_SUBJECT = 500

    /** Pack asset name pattern: grade{G}_{subject}.json under assets/packs. */
    private val PACK_FILE_REGEX = Regex(
        pattern = "^grade(2|3|4|5|6|7|8)_(mat|turkce|fen|sosyal|ing)\\.json$",
        option = RegexOption.IGNORE_CASE
    )

    /** Desteklenen ders anahtarları (DB'ye bu kısa kodlarla yazılır). */
    private val SUBJECT_KEYS = listOf("mat", "turkce", "fen", "sosyal", "ing")

    suspend fun seedIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val meta = db.appMetaDao()

        // Versioned seeding: allows safe re-import when packs/assets grow.
        val storedVersionStr = meta.get(KEY_DB_SEED_VERSION)
        val legacySeededFlag = meta.get(KEY_DB_SEEDED)
        val storedVersion = when {
            storedVersionStr != null -> storedVersionStr.toIntOrNull() ?: 0
            legacySeededFlag == "true" -> 1 // previous apps that only had boolean flag
            else -> 0
        }
        if (storedVersion >= CURRENT_DB_SEED_VERSION) {
            Log.d(TAG, "Seed already up to date (version=$storedVersion), skip")
            return@withContext false
        }

        performSeed(db, meta, context)
    }

    /**
     * DEBUG: Tüm soru tablosunu temizleyip, asset ve import edilmiş JSON'lardan
     * seeding işlemini baştan çalıştırır.
     *
     * Kullanım senaryosu:
     * - Yeni paketler eklendikten sonra uygulamayı yeniden yüklemeden havuzu tazelemek.
     */
    suspend fun forceReseed(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val meta = db.appMetaDao()
        val questionDao = db.questionDao()

        try {
            Log.w(TAG, "Force reseed requested – deleting all questions and reseeding from assets/imported JSON.")
            questionDao.deleteAll()
        } catch (e: Exception) {
            Log.e(TAG, "Force reseed deleteAll() failed", e)
        }

        // Version alanlarını güncel sürüme çek – böylece sonraki açılışlarda tekrar seedIfNeeded tetiklenmez.
        meta.set(AppMetaEntity(KEY_DB_SEEDED, "false"))
        meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, "0"))

        performSeed(db, meta, context)
    }

    /**
     * Ortak seeding uygulaması: assets + imported JSON + sentetik grade 6 paketleri.
     * Hem ilk kurulum hem de DEBUG force-resede tarafından kullanılır.
     */
    private suspend fun performSeed(
        db: BrainBuddyDatabase,
        meta: AppMetaDao,
        context: Context
    ): Boolean {
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

        val questionDao = db.questionDao()
        questionDao.insertAllIgnore(questions)
        meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
        meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
        Log.i(TAG, "Seeded ${questions.size} questions (INSERT IGNORE by id, version=$CURRENT_DB_SEED_VERSION)")

        // Import sonrası havuz doğrulama
        try {
            validatePoolCoverage(questionDao)
        } catch (e: Exception) {
            Log.w(TAG, "Pool validation failed: ${e.message}")
        }
        return true
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

        // 2) Grade 2..8 × subject bazlı JSON paketleri (assets/packs altında otomatik tarama).
        val packFiles = discoverPackAssetFiles(context)
        if (packFiles.isNotEmpty()) {
            Log.i(TAG, "Discovered ${packFiles.size} pack assets: $packFiles")
        } else {
            Log.w(TAG, "No pack assets discovered under assets/packs – only base pool will be used.")
        }
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

        // 3) Programmatically üretilen 6. sınıf genişletme paketleri.
        // Pack dosyalarında yeterli soru varsa (>= TARGET_QUESTIONS_PER_SUBJECT) atlanır.
        val existingIds = all.map { it.id }.toMutableSet()
        val g6Counts = all.filter { it.grade == 6 }.groupBy { it.subject }.mapValues { it.value.size }
        val needSynthetic = SUBJECT_KEYS.any { (g6Counts[it] ?: 0) < TARGET_QUESTIONS_PER_SUBJECT }
        if (needSynthetic) {
            all += generateGrade6SyntheticQuestions(existingIds)
        } else {
            Log.i(TAG, "Grade 6 packs have sufficient questions (>= $TARGET_QUESTIONS_PER_SUBJECT per subject), skip synthetic")
        }

        return all
    }

    /**
     * assets/packs altında bulunan tüm pack JSON dosyalarını otomatik keşfeder.
     *
     * İsim deseni:
     *   grade{G}_{subject}.json
     *   G ∈ 2..8, subject ∈ {mat,turkce,fen,sosyal,ing}
     */
    private fun discoverPackAssetFiles(context: Context): List<String> {
        return try {
            val files = context.assets.list("packs")?.toList().orEmpty()
            files
                .filter { PACK_FILE_REGEX.matches(it) }
                .sorted()
                .map { "packs/$it" }
        } catch (e: Exception) {
            Log.w(TAG, "Pack asset discovery failed: ${e.message}")
            emptyList()
        }
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

        // Diversity tags for picker: subject-specific type + sub-skill.
        val diversityType = QuestionDiversity.inferType(subjectEnum, questionText)
        val diversitySkill = QuestionDiversity.inferSkill(subjectEnum, grade, diversityType, questionText)

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
            imageAsset = imageAsset,
            type = diversityType,
            skill = diversitySkill
        )
    }

    // --- Synthetic Grade 6 packs (programmatic) ---

    private data class SyntheticQuestionSpec(
        val stem: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String?
    )

    private fun generateGrade6SyntheticQuestions(existingIds: MutableSet<String>): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        out += generateGrade6MatQuestions(existingIds)
        out += generateGrade6TurkceQuestions(existingIds)
        out += generateGrade6FenQuestions(existingIds)
        out += generateGrade6SosyalQuestions(existingIds)
        out += generateGrade6IngQuestions(existingIds)
        return out
    }

    private fun makeSyntheticQuestion(
        id: String,
        grade: Int,
        subjectKey: String,
        difficulty: Int,
        spec: SyntheticQuestionSpec
    ): QuestionEntity {
        val subjectEnum = when (subjectKey) {
            "mat" -> Subject.MAT
            "turkce" -> Subject.TURKCE
            "fen" -> Subject.FEN
            "sosyal" -> Subject.SOSYAL
            "ing" -> Subject.ING
            else -> Subject.MAT
        }
        val normalizedDifficulty = when {
            difficulty <= 0 -> 0
            difficulty == 1 -> 1
            else -> 2
        }
        val paddedOptions = if (spec.options.size >= 4) {
            spec.options.take(4)
        } else {
            spec.options + List(4 - spec.options.size) { "-" }
        }
        val answerIndex = spec.correctIndex.coerceIn(0, paddedOptions.size - 1)

        val gate = QuestionQualityGate.evaluate(
            subject = subjectEnum,
            grade = grade,
            questionText = spec.stem,
            options = paddedOptions
        )

        val diversityType = QuestionDiversity.inferType(subjectEnum, spec.stem)
        val diversitySkill = QuestionDiversity.inferSkill(subjectEnum, grade, diversityType, spec.stem)

        return QuestionEntity(
            id = id,
            grade = grade,
            subject = subjectKey,
            difficulty = normalizedDifficulty,
            questionText = spec.stem,
            optionsJson = JSONArray(paddedOptions).toString(),
            answerIndex = answerIndex,
            explanation = spec.explanation,
            isActive = gate.isActive,
            questionType = gate.questionType,
            skillsJson = gate.skillsJson,
            deactivationReason = gate.deactivationReason,
            version = 1,
            examType = "GENERAL",
            imageAsset = null,
            type = diversityType,
            skill = diversitySkill
        )
    }

    /**
     * 6. sınıf Matematik için sentetik havuz.
     * EASY ~40, MEDIUM ~70, HARD ~90 soru üretir.
     *
     * - Kolay: İki adımlı grup/raf problemleri
     * - Orta: Paylaştırma ve işlem adımı içeren problemler
     * - Zor: Yüzde ve oran içeren çok adımlı problemler
     */
    private fun generateGrade6MatQuestions(existingIds: MutableSet<String>): List<QuestionEntity> {
        val result = mutableListOf<QuestionEntity>()
        for (i in 1..600) {
            val difficulty = when {
                i <= 180 -> 0 // EASY (~30%)
                i <= 390 -> 1 // MEDIUM (~35%)
                else -> 2 // HARD (~35%)
            }
            val id = "g6_mat_" + i.toString().padStart(3, '0')
            if (!existingIds.add(id)) continue

            val spec = when (difficulty) {
                0 -> {
                    // Sıra ve öğrenci sayısı ile iki adımlı problem
                    val windowRows = 3 + (i % 3) // 3..5
                    val doorRows = 4 + (i % 4) // 4..7
                    val perRow = 3 + (i % 2) // 3..4
                    val totalRows = windowRows + doorRows
                    val totalStudents = totalRows * perRow
                    val stem = "Bir 6. sınıf sınıfında pencere kenarında $windowRows, kapı tarafında $doorRows sıra vardır. " +
                        "Her sırada $perRow öğrenci oturmaktadır. Bu sınıfta toplam kaç öğrenci vardır?"
                    val explanation = "Önce toplam sıra sayısını bul: $windowRows + $doorRows = $totalRows. " +
                        "Her sırada $perRow öğrenci olduğuna göre $totalRows × $perRow = $totalStudents öğrenci."
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            totalStudents.toString(),
                            (totalStudents - perRow).toString(),
                            (totalStudents + perRow).toString(),
                            (totalRows * (perRow - 1)).toString()
                        ),
                        correctIndex = 0,
                        explanation = explanation
                    )
                }
                1 -> {
                    // Paket, paylaştırma ve kalan kalem sayısı
                    val packCount = 3 + (i % 4) // 3..6
                    val perPack = 5 + (i % 3) // 5..7
                    val givenAway = 2 + (i % 2) // 2..3
                    val boughtTotal = packCount * perPack
                    val kept = boughtTotal - givenAway
                    val stem = "Bir kırtasiyede her pakette $perPack kalem bulunan kutulardan $packCount tane alan Defne, " +
                        "kalemlerin $givenAway tanesini arkadaşına hediye ediyor. Defne'nin elinde kaç kalem kalır?"
                    val explanation = "Önce toplam kalem sayısını bul: $packCount × $perPack = $boughtTotal. " +
                        "Ardından hediye edilen $givenAway kalemi çıkar: $boughtTotal − $givenAway = $kept."
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            kept.toString(),
                            boughtTotal.toString(),
                            (kept + 2).toString(),
                            (kept - 2).coerceAtLeast(1).toString()
                        ),
                        correctIndex = 0,
                        explanation = explanation
                    )
                }
                else -> {
                    // Yüzde ve oran içeren çok adımlı sınıf problemi
                    val students = 40 + (i % 4) * 20 // 40, 60, 80, 100
                    val boysPercent = 40 + (i % 3) * 10 // 40, 50, 60
                    val boys = students * boysPercent / 100
                    val girls = students - boys
                    val clubPercent = 25
                    val girlsInClub = girls * clubPercent / 100
                    val stem = "Bir 6. sınıfta toplam $students öğrenci vardır. Öğrencilerin %$boysPercent'i erkektir, geri kalanı kızdır. " +
                        "Kız öğrencilerin %$clubPercent'i drama kulübüne katılmaktadır. Drama kulübüne katılan kız öğrenci sayısı kaçtır?"
                    val explanation = "Önce erkek öğrenci sayısını bul: $students × $boysPercent / 100 = $boys. " +
                        "Kız sayısı: $students − $boys = $girls. " +
                        "Drama kulübüne giden kızlar: $girls × $clubPercent / 100 = $girlsInClub."
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            girlsInClub.toString(),
                            girls.toString(),
                            (girlsInClub + 4).toString(),
                            (girlsInClub - 2).coerceAtLeast(1).toString()
                        ),
                        correctIndex = 0,
                        explanation = explanation
                    )
                }
            }

            result += makeSyntheticQuestion(
                id = id,
                grade = 6,
                subjectKey = "mat",
                difficulty = difficulty,
                spec = spec
            )
        }
        return result
    }

    /**
     * 6. sınıf Türkçe için okuma-anlama ağırlıklı havuz.
     * EASY ~40, MEDIUM ~70, HARD ~90 soru.
     */
    private fun generateGrade6TurkceQuestions(existingIds: MutableSet<String>): List<QuestionEntity> {
        val result = mutableListOf<QuestionEntity>()
        val names = listOf("Ali", "Ayşe", "Deniz", "Ece", "Mert", "Zeynep")
        val days = listOf("pazartesi", "salı", "çarşamba", "perşembe", "cuma")

        for (i in 1..600) {
            val difficulty = when {
                i <= 180 -> 0
                i <= 390 -> 1
                else -> 2
            }
            val id = "g6_turkce_" + i.toString().padStart(3, '0')
            if (!existingIds.add(id)) continue

            val name = names[i % names.size]
            val day = days[i % days.size]
            val wakeHour = 7 + (i % 2) // 7,8
            val studyHours = 1 + (i % 2) // 1,2
            val hobby = if (i % 2 == 0) "resim yapar" else "kitap okur"

            val paragraph = "$name, her $day sabah saat $wakeHour'de uyanır. Kahvaltıdan sonra ödevlerini bitirir ve " +
                "günde yaklaşık $studyHours saat ders çalışır. Çalışmasını tamamladıktan sonra bir süre $hobby ve " +
                "ardından ailesiyle zaman geçirir. $name, gününü önceden planlayarak hem derslerine hem de dinlenmeye zaman ayırmaya çalışır."

            val spec = when (difficulty) {
                0 -> {
                    val stem = "$paragraph\n\nBu parçaya göre $name sabah kaçta uyanmaktadır?"
                    val correct = "$wakeHour'de"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            correct,
                            "${wakeHour + 1}'de",
                            "6'da",
                            "9'da"
                        ),
                        correctIndex = 0,
                        explanation = "Parçada \"$name, her $day sabah saat $wakeHour'de uyanır.\" cümlesi doğrudan verilmiştir."
                    )
                }
                1 -> {
                    val stem = "$paragraph\n\nBu parçaya göre aşağıdakilerden hangisi doğrudur?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            "$name hem ders çalışmaya hem de dinlenmeye zaman ayırmaktadır.",
                            "$name yalnızca hafta sonları ders çalışmaktadır.",
                            "$name gününü plansız bir şekilde geçirmektedir.",
                            "$name bütün gün boyunca sadece ders çalışmaktadır."
                        ),
                        correctIndex = 0,
                        explanation = "Parçada $name'in gününü planladığı ve hem ders hem dinlenmeye zaman ayırdığı vurgulanmaktadır."
                    )
                }
                else -> {
                    val stem = "$paragraph\n\nBu parçadan aşağıdakilerden hangisi çıkarılabilir?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            "$name, sorumluluk sahibi ve planlı bir öğrencidir.",
                            "$name, ders çalışmayı sevmeyen bir öğrencidir.",
                            "$name, ailesiyle hiç vakit geçirmemektedir.",
                            "$name sadece hobilerine zaman ayırmakta, dersleri aksatmaktadır."
                        ),
                        correctIndex = 0,
                        explanation = "Parçada $name'in hem ödevlerini bitirdiği hem de gününü planladığı, yani sorumluluk sahibi olduğu anlaşılır."
                    )
                }
            }

            result += makeSyntheticQuestion(
                id = id,
                grade = 6,
                subjectKey = "turkce",
                difficulty = difficulty,
                spec = spec
            )
        }
        return result
    }

    /**
     * 6. sınıf Fen Bilimleri için deney/yorum ağırlıklı havuz.
     */
    private fun generateGrade6FenQuestions(existingIds: MutableSet<String>): List<QuestionEntity> {
        val result = mutableListOf<QuestionEntity>()

        for (i in 1..600) {
            val difficulty = when {
                i <= 180 -> 0
                i <= 390 -> 1
                else -> 2
            }
            val id = "g6_fen_" + i.toString().padStart(3, '0')
            if (!existingIds.add(id)) continue

            val initialTemp = 20 + (i % 3) * 2 // 20,22,24
            val minutes = 6 + (i % 3) * 2 // 6,8,10
            val finalTemp = initialTemp + minutes * 2 // her dakikada 2°C artsın

            val baseExperiment = "Bir öğrenci, içindeki suyun sıcaklığını ölçmek için bir deney yapıyor. " +
                "Deneyin başında suyun sıcaklığı $initialTemp °C'dir. Isıtma işlemi boyunca her dakika sıcaklık 2 °C artmaktadır. " +
                "$minutes dakika sonunda suyun sıcaklığı ölçülüyor."

            val spec = when (difficulty) {
                0 -> {
                    val stem = "$baseExperiment\n\nBuna göre, deneyin sonunda suyun sıcaklığı kaç °C olur?"
                    val correct = finalTemp
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            correct.toString(),
                            (correct - 4).toString(),
                            (correct + 4).toString(),
                            (correct - 2).toString()
                        ),
                        correctIndex = 0,
                        explanation = "Her dakika sıcaklık 2 °C artıyor. $minutes dakika boyunca artış: $minutes × 2 = ${minutes * 2} °C. " +
                            "Son sıcaklık: $initialTemp + ${minutes * 2} = $finalTemp °C."
                    )
                }
                1 -> {
                    val stem = "$baseExperiment\n\nBu deneyle ilgili aşağıdaki yorumlardan hangisi doğrudur?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            "Sıcaklık artışı zamanla doğrusal bir ilişki göstermektedir.",
                            "Sıcaklık, ilk dakikada hızla artıp sonra azalmaktadır.",
                            "Sıcaklık bazı dakikalarda azalmakta, bazı dakikalarda artmaktadır.",
                            "Sıcaklık deney boyunca hiç değişmemektedir."
                        ),
                        correctIndex = 0,
                        explanation = "Her dakikada eşit miktarda artış olduğu için zaman-sıcaklık grafiği doğrusal olur."
                    )
                }
                else -> {
                    val halfwayMinutes = minutes / 2
                    val halfwayTemp = initialTemp + halfwayMinutes * 2
                    val stem = "$baseExperiment\n\nBu deneyin sonuçlarına göre aşağıdakilerden hangisi yanlıştır?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            "$halfwayMinutes. dakikada suyun sıcaklığı yaklaşık $halfwayTemp °C olur.",
                            "Sıcaklık, her dakikada eşit miktarda artmaktadır.",
                            "$minutes. dakikada suyun sıcaklığı $finalTemp °C olur.",
                            "Suyun sıcaklığı deney boyunca bazen artmış bazen azalmıştır."
                        ),
                        correctIndex = 3,
                        explanation = "Sıcaklık her dakikada 2 °C arttığı için hiç azalma olmaz; bu ifade yanlıştır."
                    )
                }
            }

            result += makeSyntheticQuestion(
                id = id,
                grade = 6,
                subjectKey = "fen",
                difficulty = difficulty,
                spec = spec
            )
        }

        return result
    }

    /**
     * 6. sınıf Sosyal Bilgiler için harita/zaman çizelgesi ve yorum soruları.
     */
    private fun generateGrade6SosyalQuestions(existingIds: MutableSet<String>): List<QuestionEntity> {
        val result = mutableListOf<QuestionEntity>()

        for (i in 1..600) {
            val difficulty = when {
                i <= 180 -> 0
                i <= 390 -> 1
                else -> 2
            }
            val id = "g6_sosyal_" + i.toString().padStart(3, '0')
            if (!existingIds.add(id)) continue

            val cityA = if (i % 2 == 0) "Ankara" else "İzmir"
            val cityB = if (i % 3 == 0) "İstanbul" else "Konya"
            val distanceAB = 300 + (i % 5) * 20 // km
            val speed = 60 + (i % 3) * 10 // km/saat
            val travelTime = distanceAB / speed

            val baseText = "Bir aile, Türkiye haritası üzerinde $cityA ile $cityB arasındaki kara yolculuğunu planlamaktadır. " +
                "Haritadaki ölçeğe göre iki şehir arası yaklaşık $distanceAB km'dir. Aile, ortalama saatte $speed km hızla " +
                "giden bir otobüsle yolculuk yapacaktır."

            val spec = when (difficulty) {
                0 -> {
                    val stem = "$baseText\n\nBuna göre yolculuk yaklaşık kaç saat sürer?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            travelTime.toString(),
                            (travelTime + 1).toString(),
                            (travelTime - 1).coerceAtLeast(1).toString(),
                            (travelTime + 2).toString()
                        ),
                        correctIndex = 0,
                        explanation = "Yaklaşık yolculuk süresi = mesafe / hız = $distanceAB ÷ $speed ≈ $travelTime saattir."
                    )
                }
                1 -> {
                    val stem = "$baseText\n\nBu durumla ilgili aşağıdaki yorumlardan hangisi doğrudur?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            "Harita ölçeği, gerçek mesafeyi tahmin etmemize yardımcı olur.",
                            "Harita üzerindeki uzaklıklar gerçek mesafeden her zaman daha uzundur.",
                            "Harita ölçeği yolculuk süresini değiştiren tek etkendir.",
                            "Gerçek mesafe sadece arabanın hızına göre değişir."
                        ),
                        correctIndex = 0,
                        explanation = "Harita ölçeği, küçük çizim üzerinden gerçek mesafeyi hesaplamamıza yardım eder; yolculuk süresini doğrudan değiştirmez."
                    )
                }
                else -> {
                    val stem = "$baseText\n\nBu metne göre aşağıdaki çıkarımlardan hangisi yapılabilir?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            "Haritadaki ölçek bilgisi, ulaşım planlamasında önemli bir araçtır.",
                            "Ulaşımda kullanılan aracın hızının planlamayla ilgisi yoktur.",
                            "Harita sadece şehirlerin isimlerini göstermek için kullanılır.",
                            "Gerçek mesafeler haritadan öğrenilemez."
                        ),
                        correctIndex = 0,
                        explanation = "Metinde ölçek ve hız bilgileri kullanılarak yolculuk süresi tahmin edildiği için haritaların planlama için önemli olduğu anlaşılır."
                    )
                }
            }

            result += makeSyntheticQuestion(
                id = id,
                grade = 6,
                subjectKey = "sosyal",
                difficulty = difficulty,
                spec = spec
            )
        }

        return result
    }

    /**
     * 6. sınıf İngilizce için okuma, diyalog ve cloze test soruları.
     */
    private fun generateGrade6IngQuestions(existingIds: MutableSet<String>): List<QuestionEntity> {
        val result = mutableListOf<QuestionEntity>()

        for (i in 1..600) {
            val difficulty = when {
                i <= 180 -> 0
                i <= 390 -> 1
                else -> 2
            }
            val id = "g6_ing_" + i.toString().padStart(3, '0')
            if (!existingIds.add(id)) continue

            val name = if (i % 2 == 0) "Tom" else "Lisa"
            val hobby = if (i % 3 == 0) "playing football" else "reading books"
            val time = if (i % 2 == 0) "after school" else "at the weekend"

            val paragraph = "$name is a 6th grade student. $name likes $hobby $time. " +
                "$name usually finishes homework first and then spends some time with friends or family. " +
                "$name thinks that having a good balance between study and free time is important."

            val spec = when (difficulty) {
                0 -> {
                    val stem = "$paragraph\n\nAccording to the text, when does $name usually enjoy $hobby?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            time,
                            "in the morning",
                            "at midnight",
                            "before school"
                        ),
                        correctIndex = 0,
                        explanation = "Metinde \"$name likes $hobby $time.\" cümlesi doğrudan verilmiştir."
                    )
                }
                1 -> {
                    val stem = "$paragraph\n\nWhich sentence is TRUE according to the text?"
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            "$name finishes homework before doing free-time activities.",
                            "$name never spends time with friends or family.",
                            "$name only thinks study is important.",
                            "$name doesn't like $hobby."
                        ),
                        correctIndex = 0,
                        explanation = "Parçada önce ödevlerini bitirdiği ve sonra boş zaman etkinliklerine geçtiği belirtilmiştir."
                    )
                }
                else -> {
                    val stem = "$paragraph\n\nChoose the best option to complete the sentence:\n\n\"$name thinks having a good balance between study and free time is important, because it makes him/her feel _____ .\""
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            "happier and more relaxed",
                            "tired all the time",
                            "bored at school",
                            "angry with friends"
                        ),
                        correctIndex = 0,
                        explanation = "Denge, öğrencinin kendini iyi ve dengeli hissetmesine yardımcı olur; diğer seçenekler metinle uyumlu değildir."
                    )
                }
            }

            result += makeSyntheticQuestion(
                id = id,
                grade = 6,
                subjectKey = "ing",
                difficulty = difficulty,
                spec = spec
            )
        }

        return result
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
