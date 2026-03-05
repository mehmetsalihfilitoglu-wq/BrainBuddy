package com.brainbuddy.app.quiz

import android.content.Context
import android.util.Log
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.db.DbSeeder
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.QuestionEntity
import com.brainbuddy.app.db.QuestionMapper
import com.brainbuddy.app.db.RoomQuizDataStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

class QuestionRepository(private val context: Context) {

    private val roomStore = RoomQuizDataStore(context)
    private val wrongQuestionStore = WrongQuestionStore(context)

    companion object {
        private const val TAG = "QuestionRepository"
        /** Every test (gate, normal, remedial, boss) has exactly this many questions. */
        const val MIN_QUESTIONS_PER_TEST = 20

        /** G1: Normalize text for stable ID: trim, lowercase(TR), collapse whitespace. */
        fun normalize(text: String): String = text
            .trim()
            .lowercase(Locale("tr"))
            .replace(Regex("\\s+"), " ")

        /** G1: Deterministik id - sha1(normalize(questionText) + "|" + normalize(correctAnswer)) */
        fun deterministicId(questionText: String, correctAnswer: String): String {
            val input = (normalize(questionText) + "|" + normalize(correctAnswer)).toByteArray(Charset.forName("UTF-8"))
            val digest = MessageDigest.getInstance("SHA-1").digest(input)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    private val historyStore = QuestionHistoryStore(context)

    private val importedFile get() = java.io.File(context.filesDir, "imported_questions.json")

    /** @return Pair(questions, parseStats) - stats used for debug toast */
    fun loadAllQuestions(): List<Question> = loadAllQuestionsWithStats().first

    /** Returns pool size for levelGroup. Null-safe. Use before starting test for crash safety. */
    fun getPoolSizeForLevel(levelGroup: LevelGroup): Int {
        return try {
            val global = getGlobalPool()
            val pool = global.filter { it.levelGroup == levelGroup }.ifEmpty { global }
            pool.distinctBy { it.id }.size
        } catch (_: Exception) { 0 }
    }

    /** Sınıf bazlı havuz boyutu (2-8). Room'dan grade ile filtreler. */
    fun getPoolSizeForGrade(grade: Int): Int {
        if (grade !in 2..8) return 0
        return try {
            runBlocking { DbSeeder.seedIfNeeded(context) }
            roomStore.getQuestionsByGrade(grade).distinctBy { it.id }.size
        } catch (_: Exception) { 0 }
    }

    fun loadAllQuestionsWithStats(): Pair<List<Question>, LoadStats> {
        runBlocking { DbSeeder.seedIfNeeded(context) }
        val fromRoom = roomStore.getActiveQuestions()
        return if (fromRoom.isNotEmpty()) {
            Pair(fromRoom, LoadStats(fileFound = true, totalInJson = fromRoom.size, parsedTotal = fromRoom.size, parseFailed = 0))
        } else {
            val fromAssets = loadFromAssets()
            val fromImported = loadFromImported()
            val merged = (fromAssets + fromImported).distinctBy { it.id }
            val stats = LoadStats(
                fileFound = fromAssets.isNotEmpty() || fromImported.isNotEmpty(),
                totalInJson = fromAssets.size + fromImported.size,
                parsedTotal = merged.size,
                parseFailed = 0
            )
            Pair(if (merged.isEmpty()) getFallbackQuestions() else merged, stats)
        }
    }

    private fun loadFromAssets(): List<Question> {
        return try {
            val json = context.assets.open("questions_tr.json").use { input ->
                input.readBytes().toString(Charset.forName("UTF-8"))
            }
            val arr = JSONArray(json)
            val out = ArrayList<Question>(arr.length())
            var parseFailCount = 0
            for (i in 0 until arr.length()) {
                try {
                    val o = arr.getJSONObject(i)
                    out.add(parseQuestion(o))
                } catch (e: Exception) {
                    parseFailCount++
                    Log.w(TAG, "Parse failed for question index $i: ${e.message}", e)
                }
            }
            Log.i(TAG, "questions_tr.json: loaded=${out.size}")
            out
        } catch (e: Exception) {
            Log.e(TAG, "questions_tr.json: error=${e.message}", e)
            showFallbackToast()
            emptyList()
        }
    }

    private fun loadFromImported(): List<Question> {
        if (!importedFile.exists()) return emptyList()
        return try {
            val json = importedFile.readText(Charsets.UTF_8)
            val arr = JSONArray(json)
            val out = ArrayList<Question>()
            for (i in 0 until arr.length()) {
                try {
                    out.add(parseQuestion(arr.getJSONObject(i)))
                } catch (_: Exception) { }
            }
            Log.i(TAG, "imported_questions.json: loaded=${out.size}")
            out
        } catch (e: Exception) {
            Log.e(TAG, "imported load error", e)
            emptyList()
        }
    }

    /**
     * Merge and persist imported questions.
     *
     * Kalite gate:
     * - 6–8. sınıf Matematik sorularında, metin çok kısa olup neredeyse sadece sayı/işlem
     *   karakterlerinden oluşuyorsa "too_simple" olarak işaretlenir ve havuza eklenmez.
     * - Türkçe kısa tek cümleli sorular import edilir ama kalite raporunda ayrıca sayılır.
     *
     * @return Count of newly added questions (after filters).
     */
    fun mergeImportedQuestions(arr: JSONArray): Int {
        val current = loadFromImported()
        val existingIds = current.map { it.id }.toSet().toMutableSet()
        val toAdd = ArrayList<Question>()
        var deactivatedCount = 0
        for (i in 0 until arr.length()) {
            try {
                val q = parseQuestion(arr.getJSONObject(i))
                if (q.id !in existingIds) {
                    toAdd.add(q)
                    existingIds.add(q.id)
                }
            } catch (_: Exception) { }
        }
        if (deactivatedCount > 0) {
            Log.i(
                TAG,
                "mergeImportedQuestions: deactivated $deactivatedCount low-quality questions by quality gate."
            )
        }
        if (toAdd.isEmpty()) return 0
        val merged = current + toAdd
        val jsonArr = JSONArray()
        merged.forEach { q ->
            jsonArr.put(org.json.JSONObject().apply {
                put("id", q.id)
                put("grade", q.grade)
                put("levelGroup", q.levelGroup.name)
                put("subject", q.subject.name)
                put("gradeTag", q.gradeTag)
                put("stem", q.stem)
                put("choices", org.json.JSONArray(q.choices))
                put("correctIndex", q.correctIndex)
                put("difficulty", q.difficulty.name)
                put("examType", q.examType.name)
            })
        }
        importedFile.writeText(jsonArr.toString(), Charsets.UTF_8)
        val toAddEntities = toAdd.map { q ->
            var gate = QuestionQualityGate.evaluate(q.subject, q.grade, q.stem, q.choices)

            // Ek kalite kuralları: HARD gerçekten zor olsun.
            val stem = q.stem
            if (q.difficulty == QuizDifficulty.HARD) {
                when (q.subject) {
                    // Test amaçlı: "too_simple_computation" gate'ini devre dışı bırak.
                    // Diğer derslerdeki HARD kalite kuralları çalışmaya devam eder.
                    Subject.MAT -> {
                        // intentionally no-op for now
                    }
                    Subject.TURKCE, Subject.ING -> {
                        val isLongEnough = stem.length >= 250
                        val isInferenceLike = isReadingComprehensionLike(stem)
                        if (!isLongEnough || !isInferenceLike) {
                            gate = gate.copy(
                                isActive = false,
                                deactivationReason = "too_simple_language_hard"
                            )
                        }
                    }
                    Subject.FEN, Subject.SOSYAL -> {
                        val isContextual = isContextualProblemLike(stem)
                        val isLongEnough = stem.length >= 80
                        if (!isContextual || !isLongEnough) {
                            gate = gate.copy(
                                isActive = false,
                                deactivationReason = "too_simple_context_hard"
                            )
                        }
                    }
                }
            }

            if (!gate.isActive) {
                deactivatedCount++
            }
            val diffInt = when (q.difficulty) {
                QuizDifficulty.EASY -> 0
                QuizDifficulty.HARD -> 2
                else -> 1
            }
            QuestionEntity(
                id = q.id,
                grade = q.grade.coerceIn(1, 8),
                subject = when (q.subject) {
                    Subject.MAT -> "mat"
                    Subject.TURKCE -> "turkce"
                    Subject.FEN -> "fen"
                    Subject.SOSYAL -> "sosyal"
                    Subject.ING -> "ing"
                },
                difficulty = diffInt,
                questionText = q.stem,
                optionsJson = org.json.JSONArray(q.choices).toString(),
                answerIndex = q.correctIndex,
                explanation = q.hint?.takeIf { it.isNotBlank() },
                isActive = gate.isActive,
                questionType = gate.questionType,
                skillsJson = gate.skillsJson,
                deactivationReason = gate.deactivationReason,
                version = 1,
                examType = q.examType.name,
                imageAsset = q.imageAsset?.takeIf { it.isNotBlank() }
            )
        }
        roomStore.insertQuestions(toAddEntities)
        // Import sonrası DB havuz sayıları (toplam ve aktif) – teşhis için logla.
        try {
            val db = DatabaseProvider.get(context)
            val counts = kotlinx.coroutines.runBlocking {
                val dao = db.questionDao()
                val total = dao.countAll()
                val active = dao.countAllActive()
                total to active
            }
            Log.i(
                TAG,
                "mergeImportedQuestions: imported=${toAdd.size}, insertedEntities=${toAddEntities.size}, totalAfter=${counts.first}, activeAfter=${counts.second}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "mergeImportedQuestions: failed to log DB counts: ${e.message}")
        }
        return toAdd.size
    }

    data class LoadStats(
        val fileFound: Boolean,
        val totalInJson: Int,
        val parsedTotal: Int,
        val parseFailed: Int
    )

    /**
     * Soru kalite metrikleri – grade+subject bazında dağılım.
     *
     * - problemRatio: problem/yorum sorusu oranı
     * - paragraphRatio: paragraf sorusu oranı
     * - avgLength: ortalama soru kökü (stem) uzunluğu
     * - multiStepRatio: çok adımlı olduğu tahmin edilen soru oranı
     * - simpleMathCount: kalite gate'e takılabilecek kadar basit matematik soru sayısı
     * - shortTurkceSingleCount: kısa ve tek cümleli Türkçe soru sayısı
     */
    data class QualityStats(
        val grade: Int,
        val subject: Subject,
        val total: Int,
        val problemCount: Int,
        val paragraphCount: Int,
        val multiStepCount: Int,
        val simpleMathCount: Int,
        val shortTurkceSingleCount: Int,
        val avgLength: Double
    ) {
        val problemRatio: Double get() = if (total > 0) problemCount.toDouble() / total else 0.0
        val paragraphRatio: Double get() = if (total > 0) paragraphCount.toDouble() / total else 0.0
        val multiStepRatio: Double get() = if (total > 0) multiStepCount.toDouble() / total else 0.0
    }

    /** Full report for debug screens – grouped by (grade, subject). */
        fun computeQualityReport(): List<QualityStats> {
        val all = loadAllQuestions()
        if (all.isEmpty()) return emptyList()

        val grouped = all.groupBy { it.grade to it.subject }
        return grouped.map { (key, questions) ->
            val (grade, subject) = key
            var problem = 0
            var paragraph = 0
            var multiStep = 0
            var simpleMath = 0
            var shortTurkce = 0
            var totalLen = 0

            questions.forEach { q ->
                val stem = q.stem
                totalLen += stem.length

                val isParagraph = isParagraphQuestion(stem)
                if (isParagraph) paragraph++

                if (subject == Subject.MAT) {
                    val tooSimple = isTooSimpleMathQuestion(q)
                    if (tooSimple) {
                        simpleMath++
                    }
                    val isProblemLike = !tooSimple && isMathProblemLike(stem)
                    if (isProblemLike) problem++
                } else if (subject == Subject.TURKCE) {
                    if (isParagraph || isReadingComprehensionLike(stem)) {
                        problem++
                    }
                    if (isShortSingleSentenceTurkish(stem)) {
                        shortTurkce++
                    }
                } else {
                    if (isContextualProblemLike(stem)) {
                        problem++
                    }
                }

                if (isMultiStepQuestion(stem, subject)) {
                    multiStep++
                }
            }

            QualityStats(
                grade = grade,
                subject = subject,
                total = questions.size,
                problemCount = problem,
                paragraphCount = paragraph,
                multiStepCount = multiStep,
                simpleMathCount = simpleMath,
                shortTurkceSingleCount = shortTurkce,
                avgLength = totalLen.toDouble() / questions.size.coerceAtLeast(1)
            )
        }
    }

    /**
     * Quiz başlamadan önce havuz teşhisi için:
     * - TOTAL / TOTAL_ACTIVE
     * - grade bazında total/active
     * - grade+subject ve grade+subject+difficulty bazında total/active
     *
     * Debug metni döner ve ayrıca Logcat'e yazar.
     */
    fun buildPoolDebugStatsForGrade(
        grade: Int,
        difficulty: QuizDifficulty
    ): String {
        if (grade !in 2..8) {
            return "DB Debug: grade=$grade geçersiz (2..8 dışında)."
        }
        return try {
            val db = DatabaseProvider.get(context)
            val diffInt = when (difficulty) {
                QuizDifficulty.EASY -> 0
                QuizDifficulty.HARD -> 2
                else -> 1
            }
            val subjects = listOf("mat", "turkce", "fen", "sosyal", "ing")
            val sb = StringBuilder()
            val lineBreak = "\n"

            val snapshot = kotlinx.coroutines.runBlocking {
                val dao = db.questionDao()
                val total = dao.countAll()
                val active = dao.countAllActive()
                val gradeTotal = dao.countByGrade(grade)
                val gradeActive = dao.countActiveByGrade(grade)
                val perSubject = subjects.map { subj ->
                    val gsTotal = dao.countByGradeSubject(grade, subj)
                    val gsActive = dao.countActiveByGradeSubject(grade, subj)
                    val gsdTotal = dao.countByGradeSubjectDifficulty(grade, subj, diffInt)
                    val gsdActive = dao.countActiveByGradeSubjectDifficulty(grade, subj, diffInt)
                    subj to Quad(gsTotal, gsActive, gsdTotal, gsdActive)
                }.toMap()
                DbPoolSnapshot(
                    total = total,
                    active = active,
                    gradeTotal = gradeTotal,
                    gradeActive = gradeActive,
                    perSubject = perSubject
                )
            }

            sb.append("DB Debug:\n")
            sb.append("- TOTAL=${
                snapshot.total
            } (active=${snapshot.active})\n")
            sb.append("- selectedGrade=$grade, selectedDifficulty=${difficulty.name}\n")
            sb.append("- gradeTotal=${snapshot.gradeTotal}, gradeActive=${snapshot.gradeActive}\n")
            subjects.forEach { subj ->
                val q = snapshot.perSubject[subj]
                if (q != null) {
                    sb.append(
                        "- grade=$grade subject=$subj -> total=${q.total} active=${q.active}, " +
                            "diffInt=$diffInt totalDiff=${q.totalDiff} activeDiff=${q.activeDiff}$lineBreak"
                    )
                } else {
                    sb.append(
                        "- grade=$grade subject=$subj -> total=0 active=0, diffInt=$diffInt totalDiff=0 activeDiff=0$lineBreak"
                    )
                }
            }
            val debugText = sb.toString().trimEnd()
            Log.d(TAG, "[POOL_DEBUG] " + debugText.replace("\n", " | "))
            debugText
        } catch (e: Exception) {
            Log.w(TAG, "buildPoolDebugStatsForGrade: failed: ${e.message}", e)
            "DB Debug: hata=${e.message}"
        }
    }

    private data class DbPoolSnapshot(
        val total: Int,
        val active: Int,
        val gradeTotal: Int,
        val gradeActive: Int,
        val perSubject: Map<String, Quad>
    )

    private data class Quad(
        val total: Int,
        val active: Int,
        val totalDiff: Int,
        val activeDiff: Int
    )

    /**
     * İnsan okunabilir debug özeti.
     *
     * Her (grade, subject) kombinasyonu için:
     * - Problem %
     * - Paragraf %
     * - Ortalama uzunluk
     * - Çok adımlı % tahmini
     * - Basit matematik / kısa tek cümle Türkçe sayıları
     */
    fun buildQualityDebugSummary(): String {
        val db = DatabaseProvider.get(context)
        val entities = kotlinx.coroutines.runBlocking { db.questionDao().getAllQuestions() }
        if (entities.isEmpty()) {
            return "Toplam soru: 0\n(Havuz boş – Room içeriği bulunamadı.)"
        }

            val grouped = entities.groupBy { it.grade to it.subject }
        val totalActive = entities.count { it.isActive }

        val sb = StringBuilder()
        sb.append("Toplam aktif soru: $totalActive\n")
        sb.append("Grade + ders bazında kalite özeti:\n\n")

        grouped.toSortedMap(
            compareBy<Pair<Int, String>> { it.first }
                .thenBy { it.second }
        ).forEach { (key, list) ->
            val (grade, subjectRaw) = key
            val subjectEnum = QuestionMapper.mapSubject(subjectRaw)
            val subjectName = subjectEnum.tr

            val active = list.filter { it.isActive }
            val passive = list.filter { !it.isActive }

            val activeCount = active.size
            val passiveCount = passive.size
            val avgLen = if (active.isNotEmpty()) {
                active.map { it.questionText.length }.average()
            } else 0.0

            val typeCounts = active.groupBy { it.questionType ?: "unknown" }
                .mapValues { it.value.size }
            val typeSummary = typeCounts.entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${it.key}=${it.value}" }
                .ifEmpty { "n/a" }

            val reasonCounts = passive.groupBy { it.deactivationReason ?: "unknown" }
                .mapValues { it.value.size }
            val reasonSummary = if (passiveCount > 0) {
                reasonCounts.entries
                    .sortedByDescending { it.value }
                    .joinToString(", ") { "${it.key}=${it.value}" }
            } else {
                "-"
            }

            val diffCounts = active.groupBy { it.difficulty }
                .mapValues { it.value.size }
            val totalForDiff = activeCount.coerceAtLeast(1)
            val easyPct = ((diffCounts[0] ?: 0) * 100.0 / totalForDiff).toInt()
            val medPct = ((diffCounts[1] ?: 0) * 100.0 / totalForDiff).toInt()
            val hardCount = (diffCounts[2] ?: 0) + (diffCounts[3] ?: 0)
            val hardPct = (hardCount * 100.0 / totalForDiff).toInt()
            val diffSummary = "diff: K=$easyPct O=$medPct Z=$hardPct"

            val outOfTarget = medPct < 25 || hardPct < 30 || easyPct > 35
            val diffLine = if (activeCount > 0 && outOfTarget) {
                "$diffSummary ⚠ hedef dışı"
            } else {
                diffSummary
            }

            val target = QuestionImportRepository.TARGET_QUESTIONS_PER_SUBJECT
            val targetInfo = if (activeCount >= target) {
                "hedef_ok"
            } else {
                "hedef_eksik(${activeCount}/$target)"
            }

            sb.append("${grade}. sınıf $subjectName\n")
            sb.append("- aktif_soru=${activeCount} ($targetInfo)\n")
            sb.append("- pasif_soru=${passiveCount} [reason: $reasonSummary]\n")
            sb.append("- ortalama_kok_uzunluğu=${avgLen.toInt()} ch\n")
            sb.append("- questionType: $typeSummary\n")
            sb.append("- $diffLine\n")
            sb.append("\n")
        }

        return sb.toString()
    }

    private fun showFallbackToast() {
        Log.w(TAG, "Soru dosyası bulunamadı veya boş, varsayılan sorular kullanılıyor.")
    }

    /** Global pool: all questions from load + fallback, filtered by active exam packs. Never empty. */
    private fun getGlobalPool(): List<Question> {
        val (all, _) = loadAllQuestionsWithStats()
        val base = if (all.isNotEmpty()) all else getFallbackQuestions()
        val examStore = com.brainbuddy.app.core.ExamPackStore(context)
        val filtered = base.filter { examStore.isPackActive(it.examType) }
        return if (filtered.isEmpty()) base else filtered
    }

    /** In-code fallback so quiz never crashes when asset is missing or pool is empty. */
    private fun getFallbackQuestions(): List<Question> = listOf(
        Question(id = "fb1", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", grade = 6, stem = "12 × 15 işleminin sonucu kaçtır?", choices = listOf("160", "170", "180", "190"), correctIndex = 2, hint = "12×10=120, 12×5=60", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb2", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", grade = 6, stem = "Türkiye'nin başkenti neresidir?", choices = listOf("İstanbul", "İzmir", "Ankara", "Bursa"), correctIndex = 2, hint = "Mustafa Kemal Atatürk'ün kararıyla.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb3", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", grade = 6, stem = "Güneş sisteminde Dünya'dan sonra gelen gezegen hangisidir?", choices = listOf("Venüs", "Mars", "Jüpiter", "Satürn"), correctIndex = 1, hint = "Merkür, Venüs, Dünya, Mars...", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb4", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.ING, gradeTag = "6", grade = 6, stem = "\"Hello\" kelimesinin Türkçe karşılığı nedir?", choices = listOf("Hoşça kal", "Merhaba", "Teşekkürler", "Evet"), correctIndex = 1, hint = "Selamlama sözcüğü.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb5", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.SOSYAL, gradeTag = "7", grade = 7, stem = "Türkiye Cumhuriyeti hangi yıl kurulmuştur?", choices = listOf("1920", "1922", "1923", "1924"), correctIndex = 2, hint = "Lozan Antlaşması sonrası.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb6", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", grade = 7, stem = "2x + 5 = 15 denkleminde x kaçtır?", choices = listOf("3", "4", "5", "6"), correctIndex = 2, hint = "Önce 5'i karşı tarafa at.", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb7", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", grade = 7, stem = "\"Koşmak\" fiilinin geniş zaman 1. tekil şahıs çekimi hangisidir?", choices = listOf("koşarım", "koşuyorum", "koşar", "koşarsın"), correctIndex = 0, hint = "Geniş zaman -ar/-er eki alır.", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb8", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", grade = 7, stem = "Fotosentez olayında hangi gaz üretilir?", choices = listOf("Karbondioksit", "Azot", "Oksijen", "Hidrojen"), correctIndex = 2, hint = "Bitkiler ışıkta ne üretir?", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb9", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "8", grade = 8, stem = "√64 işleminin sonucu kaçtır?", choices = listOf("6", "7", "8", "9"), correctIndex = 2, hint = "8×8=64", imageAsset = null, difficulty = QuizDifficulty.HARD),
        Question(id = "fb10", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.SOSYAL, gradeTag = "8", grade = 8, stem = "TBMM'nin açılış tarihi nedir?", choices = listOf("19 Mayıs 1919", "23 Nisan 1920", "30 Ağustos 1922", "29 Ekim 1923"), correctIndex = 1, hint = "Ulusal Egemenlik ve Çocuk Bayramı.", imageAsset = null, difficulty = QuizDifficulty.HARD),
        // Seed questions for development (30+ across 3 topics)
        Question(id = "fb11", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", grade = 6, stem = "18 + 27 = ?", choices = listOf("43", "44", "45", "46"), correctIndex = 2, hint = "8+7=15", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb12", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", grade = 6, stem = "56 − 29 = ?", choices = listOf("25", "26", "27", "28"), correctIndex = 2, hint = "Borrow from tens", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb13", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", grade = 7, stem = "7 × 8 = ?", choices = listOf("54", "55", "56", "58"), correctIndex = 2, hint = "7×8=56", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb14", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", grade = 7, stem = "72 ÷ 9 = ?", choices = listOf("6", "7", "8", "9"), correctIndex = 2, hint = "9×8=72", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb15", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "8", grade = 8, stem = "Bir üçgenin iç açıları toplamı?", choices = listOf("90°", "180°", "270°", "360°"), correctIndex = 1, hint = "Tüm üçgenlerde geçerli", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb16", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", grade = 6, stem = "1 km kaç metredir?", choices = listOf("10", "100", "500", "1000"), correctIndex = 3, hint = "kilo=1000", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb17", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", grade = 7, stem = "25 × 4 = ?", choices = listOf("90", "95", "100", "105"), correctIndex = 2, hint = "25×4=100", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb18", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "8", grade = 8, stem = "15² kaçtır?", choices = listOf("200", "215", "225", "250"), correctIndex = 2, hint = "15×15", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb19", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", grade = 6, stem = "144 ÷ 12 = ?", choices = listOf("10", "11", "12", "13"), correctIndex = 2, hint = "12×12=144", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb20", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", grade = 7, stem = "0,5 kesir olarak?", choices = listOf("1/3", "1/4", "1/2", "2/3"), correctIndex = 2, hint = "5/10=1/2", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb21", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", grade = 6, stem = "Cümle sonuna hangi noktalama konur?", choices = listOf("Virgül", "Nokta", "Ünlem", "Soru işareti"), correctIndex = 1, hint = "Cümle biter", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb22", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", grade = 6, stem = "Ev kelimesinin çoğul hali?", choices = listOf("evler", "evlar", "evs", "evden"), correctIndex = 0, hint = "-ler/-lar eki", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb23", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", grade = 7, stem = "Özel isim örneği?", choices = listOf("ev", "Ankara", "büyük", "koşmak"), correctIndex = 1, hint = "Yer adları özel isimdir", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb24", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", grade = 7, stem = "Yüklem hangi soru ile bulunur?", choices = listOf("Kim?", "Ne yapıyor?", "Nerede?", "Nasıl?"), correctIndex = 1, hint = "Eylemi bildirir", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb25", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "8", grade = 8, stem = "Deyim nedir?", choices = listOf("Gerçek anlamlı söz", "Kalıplaşmış mecazlı söz", "Yabancı kelime", "Eski kelime"), correctIndex = 1, hint = "Göz açıp kapayıncaya kadar", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb26", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", grade = 6, stem = "Okul kelimesi kaç hecelidir?", choices = listOf("1", "2", "3", "4"), correctIndex = 1, hint = "O-kul", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb27", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", grade = 7, stem = "Güzel sıfatının zıt anlamlısı?", choices = listOf("İyi", "Çirkin", "Büyük", "Küçük"), correctIndex = 1, hint = "Görünüm", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb28", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "8", grade = 8, stem = "Yazım ve imla ilişkisi?", choices = listOf("Zıt", "Eş anlamlı", "Yakın", "Eş sesli"), correctIndex = 1, hint = "Aynı anlam", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb29", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", grade = 6, stem = "Büyük kelimesinin zıttı?", choices = listOf("Geniş", "Küçük", "Uzun", "Kısa"), correctIndex = 1, hint = "Boyut", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb30", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", grade = 7, stem = "Atatürk özel isim midir?", choices = listOf("Evet", "Hayır", "Bazen", "Belirsiz"), correctIndex = 0, hint = "Kişi adları özeldir", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb31", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", grade = 6, stem = "Hücrenin yönetim merkezi?", choices = listOf("Sitoplazma", "Çekirdek", "Hücre zarı", "Mitokondri"), correctIndex = 1, hint = "DNA burada", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb32", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", grade = 6, stem = "Canlıların temel yapı taşı?", choices = listOf("Organ", "Doku", "Hücre", "Sistem"), correctIndex = 2, hint = "En küçük birim", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb33", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", grade = 7, stem = "Fotosentezde üretilen gaz?", choices = listOf("CO2", "Azot", "Oksijen", "Hidrojen"), correctIndex = 2, hint = "Bitkiler ne üretir?", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb34", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", grade = 7, stem = "Güneş ne tür gök cismidir?", choices = listOf("Gezegen", "Uydu", "Yıldız", "Asteroid"), correctIndex = 2, hint = "Işık yayar", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb35", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "8", grade = 8, stem = "Suyun formülü?", choices = listOf("CO2", "NaCl", "H2O", "O2"), correctIndex = 2, hint = "Hidrojen ve oksijen", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb36", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", grade = 6, stem = "Dünya'nın uydusu?", choices = listOf("Mars", "Venüs", "Ay", "Güneş"), correctIndex = 2, hint = "Geceleri görünür", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb37", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", grade = 7, stem = "Kuvvetin birimi?", choices = listOf("Metre", "Newton", "Saniye", "Kilogram"), correctIndex = 1, hint = "N ile gösterilir", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb38", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "8", grade = 8, stem = "Saf suyun kaynama noktası (°C)?", choices = listOf("90", "95", "100", "105"), correctIndex = 2, hint = "Deniz seviyesi", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb39", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", grade = 6, stem = "Maddenin halleri?", choices = listOf("Katı, sıvı, gaz", "Ateş, su, toprak", "Kök, gövde, yaprak", "Hücre, doku, organ"), correctIndex = 0, hint = "Fiziksel haller", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb40", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", grade = 7, stem = "Mıknatıs hangi metali çeker?", choices = listOf("Bakır", "Demir", "Alüminyum", "Altın"), correctIndex = 1, hint = "Demir, nikel, kobalt", imageAsset = null, difficulty = QuizDifficulty.EASY)
    )

    /**
     * Matematik sorusu için "çok basit, salt işlem" heuristiği.
     * 6–8. sınıf MAT dışındaki soruları asla elemez.
     */
    private fun isTooSimpleMathQuestion(q: Question): Boolean {
        if (q.subject != Subject.MAT) return false
        if (q.grade !in 6..8) return false
        val stem = q.stem
        val compact = stem
            .replace("\\s+".toRegex(), "")
            .replace("[=?]".toRegex(), "")
        if (compact.length < 5) return true
        if (compact.length > 20) return false
        // Harf içeriyorsa, bağlamlı/problem olma ihtimali var, eleme.
        if (compact.any { it.isLetter() }) return false
        val allowed = "0123456789+-×xX*/:÷().,%"
        if (compact.any { it !in allowed }) return false
        return true
    }

    /** Uzun ve çok cümleli kökler için "paragraf sorusu" tahmini. */
    private fun isParagraphQuestion(stem: String): Boolean {
        if (stem.length < 200) return false
        val sentences = stem.split(Regex("[.!?]")).map { it.trim() }.filter { it.isNotEmpty() }
        return sentences.size >= 2
    }

    /** Matematikte problem-benzeri ifade: bağlam içeren, salt işlem olmayan sorular. */
    private fun isMathProblemLike(stem: String): Boolean {
        if (stem.length < 60) return false
        val lower = stem.lowercase(Locale("tr"))
        val keywords = listOf(
            "problemi", "problem", "oran", "yüzde", "grafik", "tablo", "şekilde", "aşağıdaki",
            "bir okulda", "bir çiftçi", "bir market", "bilet", "sınıfta", "öğrenci", "para", "metre",
            "dikdörtgen", "üçgen", "daire"
        )
        return keywords.any { it in lower }
    }

    /** Fen/Sosyal gibi derslerde günlük hayat/kavramsal problem tahmini. */
    private fun isContextualProblemLike(stem: String): Boolean {
        if (stem.length < 80) return false
        val lower = stem.lowercase(Locale("tr"))
        val keywords = listOf(
            "aşağıdaki", "grafik", "tablo", "deney", "düzeneği", "metne göre",
            "parçaya göre", "haritaya bakarak", "şekilde", "günlük hayat"
        )
        return keywords.any { it in lower }
    }

    /** Türkçe'de okuduğunu anlama/paragraf benzeri soru tahmini. */
    private fun isReadingComprehensionLike(stem: String): Boolean {
        if (stem.length < 150) return false
        val lower = stem.lowercase(Locale("tr"))
        val keywords = listOf(
            "bu parçaya göre", "bu paragrafa göre", "bu metne göre",
            "ana düşünce", "ana fikir", "yardımcı düşünce", "çıkarılamaz", "anlaşılmaktadır"
        )
        return keywords.any { it in lower } || isParagraphQuestion(stem)
    }

    /** Türkçe, kısa (<200) ve tek cümleli cümle kökü (paragraf olmayan). */
    private fun isShortSingleSentenceTurkish(stem: String): Boolean {
        if (stem.length >= 200) return false
        val sentences = stem.split(Regex("[.!?]")).map { it.trim() }.filter { it.isNotEmpty() }
        return sentences.size <= 1
    }

    /** Çok adımlı soru tahmini: birden fazla sayı veya işlem adımı/ifadeleri içeriyorsa. */
    private fun isMultiStepQuestion(stem: String, subject: Subject): Boolean {
        val lower = stem.lowercase(Locale("tr"))
        val numberGroups = Regex("\\d+").findAll(stem).count()
        val stepKeywords = listOf(
            "önce", "sonra", "ardından", "daha sonra", "hem", "hem de",
            "birinci adım", "ikinci adım", "i) ", "ii) ", "iii) "
        )
        if (numberGroups >= 3) return true
        if (stepKeywords.any { it in lower }) return true
        if (subject == Subject.MAT || subject == Subject.FEN) {
            val opCount = stem.count { it in "+-×xX*/:÷" }
            if (opCount >= 2 && numberGroups >= 2) return true
        }
        return false
    }

    private fun parseQuestion(o: JSONObject): Question {
        val choicesArr = o.optJSONArray("choices")
            ?: throw IllegalArgumentException("Missing 'choices' array")
        val raw = (0 until choicesArr.length()).map { idx ->
            choicesArr.optString(idx, "").ifEmpty { choicesArr.opt(idx)?.toString() ?: "-" }
        }.filter { it.isNotBlank() }
        if (raw.isEmpty()) throw IllegalArgumentException("Choices array empty")
        val choices = if (raw.size >= 4) raw.take(4) else raw + List(4 - raw.size) { "-" }
        val diffStr = o.optString("difficulty", "MEDIUM")
        val difficulty = try {
            when (diffStr) {
                // Eski JSON'larda kalan VERY_HARD değerlerini HARD'a eşitle
                "VERY_HARD" -> QuizDifficulty.HARD
                else -> QuizDifficulty.valueOf(diffStr)
            }
        } catch (_: Exception) {
            QuizDifficulty.MEDIUM
        }
        val levelStr = o.optString("levelGroup", "GRADE_5_8")
        val levelGroup = try {
            LevelGroup.valueOf(levelStr)
        } catch (_: Exception) {
            LevelGroup.GRADE_5_8
        }
        val subjStr = o.optString("subject", "MAT").let { s ->
            if (s == "INGILIZCE") "ING" else s
        }
        val subject = try {
            Subject.valueOf(subjStr)
        } catch (_: Exception) {
            Subject.MAT
        }
        val examStr = o.optString("examType", "GENERAL")
        val examType = try { com.brainbuddy.app.quiz.ExamType.valueOf(examStr) } catch (_: Exception) { com.brainbuddy.app.quiz.ExamType.GENERAL }
        val topic = o.optString("topic", "").takeIf { it.isNotEmpty() }
        val stem = o.optString("stem", "?")
        val correctIdx = o.optInt("correctIndex", 0).coerceIn(0, choices.size - 1)
        val correctAnswer = choices.getOrNull(correctIdx) ?: ""
        val gradeTag = o.optString("gradeTag", "")
        val grade = o.optInt("grade", 0).let { g ->
            if (g in 2..8) g else gradeTag.toIntOrNull()?.coerceIn(2, 8) ?: 6
        }
        val rawId = o.optString("id", "")
        val id = if (rawId.isNotBlank()) rawId else {
            "${grade}_${subjStr}_${deterministicId(stem, correctAnswer).take(6)}"
        }
        return Question(
            id = id,
            levelGroup = levelGroup,
            subject = subject,
            gradeTag = gradeTag.ifEmpty { grade.toString() },
            grade = grade,
            stem = stem,
            choices = choices.ifEmpty { listOf("A", "B", "C", "D") },
            correctIndex = o.optInt("correctIndex", 0).coerceIn(0, 3),
            hint = o.optString("hint", "").takeIf { it.isNotEmpty() },
            imageAsset = o.optString("imageAsset", "").takeIf { it.isNotEmpty() },
            difficulty = difficulty,
            examType = examType,
            topic = topic
        )
    }

    /** @param questionsMap Optional map of questionId->Question for wrong-question tracking (topic). */
    /** @param testId G4: Quiz bitince lastSeenInTestId güncellemesi için */
    fun recordAnswers(answers: List<AnswerRecord>, questionsMap: Map<String, Question>? = null, testId: String? = null) {
        val userId = com.brainbuddy.app.core.ActiveProfileManager.getActiveProfileId(context)
        roomStore.recordAnswers(userId, answers, testId)
        answers.forEach { a ->
            val q = questionsMap?.get(a.questionId)
            if (a.isCorrect) {
                wrongQuestionStore.markFixed(a.questionId)
            } else {
                wrongQuestionStore.recordWrong(a.questionId, q?.subject?.tr ?: "Diğer")
            }
        }
    }

    /** G5: Quiz tamamlanınca çağrılır. globalTestIndex++, her soru için lastSeenTestIndex günceller. */
    fun onQuizCompleted(questionIds: List<String>) {
        roomStore.onQuizCompleted(questionIds)
    }

    /**
     * Sınıf bazlı test: Kullanıcının seçtiği grade (2-8) için havuzdan seçim.
     * Dağılım: Mat 5, Tr 5, Fen 4, Sos 3, Eng 3 (hepsi aynı grade'den).
     * Spaced repetition kuralları uygulanır.
     */
        fun pickQuizQuestionsByGrade(
        grade: Int,
        count: Int = MIN_QUESTIONS_PER_TEST,
        testId: String? = null
        ): List<Question> {
        if (grade !in 2..8) return emptyList()
        runBlocking { DbSeeder.seedIfNeeded(context) }

        // 1) Zorluk tercihini DataStore'dan (QuizPrefs) oku
        val selectedDifficulty = try {
            QuizPrefs(context).difficulty()
        } catch (_: Exception) {
            QuizDifficulty.MEDIUM
        }

        val subjects = listOf("mat", "turkce", "fen", "sosyal", "ing")

        fun loadPoolForDifficulty(diffInt: Int): List<Question> {
            return subjects.flatMap { subj ->
                roomStore.getQuestionsByGradeSubjectDifficulty(grade, subj, diffInt)
            }.distinctBy { it.id }
        }

        val hardInt = 2
        val medInt = 1
        val easyInt = 0

        val profileId = ProfileStore(context).getCurrentProfileId()
        val effectiveTestId = testId ?: java.util.UUID.randomUUID().toString()
        val picker = SpacedRepetitionPicker(
            historyDao = com.brainbuddy.app.db.DatabaseProvider.get(context).historyDao(),
            roomStore = roomStore,
            getQuestionIdsFromLastNTests = { pid, n -> roomStore.getQuestionIdsFromLastNTests(pid, n) }
        )

        val maxFallbackFromLower = (count * 0.2).toInt().coerceAtLeast(0)

        val basePool: List<Question>
        val fallbackPool: List<Question>
        when (selectedDifficulty) {
            QuizDifficulty.HARD -> {
                val hardPool = loadPoolForDifficulty(hardInt)
                val primaryPickCount = minOf(count, hardPool.size)
                var questions = if (primaryPickCount > 0) {
                    picker.pick(hardPool, primaryPickCount, profileId)
                } else {
                    emptyList()
                }.toMutableList()

                val remaining = count - questions.size
                val mediumPool = loadPoolForDifficulty(medInt)
                    .filter { q -> questions.none { it.id == q.id } }

                if (remaining > 0 && mediumPool.isNotEmpty() && maxFallbackFromLower > 0) {
                    val fallbackCount = minOf(remaining, maxFallbackFromLower, mediumPool.size)
                    questions.addAll(mediumPool.shuffled().take(fallbackCount))
                }

                var finalQuestions = questions
                if (finalQuestions.isEmpty()) {
                    val unionPool = (hardPool + mediumPool).distinctBy { it.id }
                    finalQuestions = unionPool.shuffled().take(count).toMutableList()
                }

                if (finalQuestions.size < count && finalQuestions.isNotEmpty()) {
                    val allPool = (hardPool + mediumPool).distinctBy { it.id }
                    val usedIds = finalQuestions.map { it.id }.toMutableSet()
                    val extra = allPool.filter { it.id !in usedIds }
                    var idx = 0
                    while (finalQuestions.size < count && extra.isNotEmpty()) {
                        finalQuestions.add(extra[idx % extra.size])
                        usedIds.add(extra[idx % extra.size].id)
                        idx++
                        if (idx > extra.size * 2) break
                    }
                }

                basePool = hardPool
                fallbackPool = mediumPool

                val questionIds = finalQuestions.map { it.id }
                roomStore.recordTestCreated(profileId, effectiveTestId, questionIds)
                recordSeenForQuiz(profileId, questionIds)

                android.util.Log.d(
                    TAG,
                    "[GRADE_TEST] selectedGrade=$grade, selectedDifficulty=${selectedDifficulty.name}, hardPool=${hardPool.size}, mediumPool=${mediumPool.size}, picked=${finalQuestions.size}"
                )

                return finalQuestions.shuffled()
            }

            QuizDifficulty.MEDIUM -> {
                val mediumPool = loadPoolForDifficulty(medInt)
                val primaryPickCount = minOf(count, mediumPool.size)
                var questions = if (primaryPickCount > 0) {
                    picker.pick(mediumPool, primaryPickCount, profileId)
                } else {
                    emptyList()
                }.toMutableList()

                val remaining = count - questions.size
                val easyPool = loadPoolForDifficulty(easyInt)
                    .filter { q -> questions.none { it.id == q.id } }

                if (remaining > 0 && easyPool.isNotEmpty() && maxFallbackFromLower > 0) {
                    val fallbackCount = minOf(remaining, maxFallbackFromLower, easyPool.size)
                    questions.addAll(easyPool.shuffled().take(fallbackCount))
                }

                var finalQuestions = questions
                if (finalQuestions.isEmpty()) {
                    val unionPool = (mediumPool + easyPool).distinctBy { it.id }
                    finalQuestions = unionPool.shuffled().take(count).toMutableList()
                }

                if (finalQuestions.size < count && finalQuestions.isNotEmpty()) {
                    val allPool = (mediumPool + easyPool).distinctBy { it.id }
                    val usedIds = finalQuestions.map { it.id }.toMutableSet()
                    val extra = allPool.filter { it.id !in usedIds }
                    var idx = 0
                    while (finalQuestions.size < count && extra.isNotEmpty()) {
                        finalQuestions.add(extra[idx % extra.size])
                        usedIds.add(extra[idx % extra.size].id)
                        idx++
                        if (idx > extra.size * 2) break
                    }
                }

                basePool = mediumPool
                fallbackPool = easyPool

                val questionIds = finalQuestions.map { it.id }
                roomStore.recordTestCreated(profileId, effectiveTestId, questionIds)
                recordSeenForQuiz(profileId, questionIds)

                android.util.Log.d(
                    TAG,
                    "[GRADE_TEST] selectedGrade=$grade, selectedDifficulty=${selectedDifficulty.name}, mediumPool=${mediumPool.size}, easyPool=${easyPool.size}, picked=${finalQuestions.size}"
                )

                return finalQuestions.shuffled()
            }

            QuizDifficulty.EASY -> {
                val easyPool = loadPoolForDifficulty(easyInt)
                val pool = if (easyPool.isNotEmpty()) easyPool else roomStore.getQuestionsByGrade(grade)
                val basePoolEasy = if (pool.isNotEmpty()) pool else getFallbackQuestions().filter { it.grade == grade }.ifEmpty { getFallbackQuestions() }

                val pickCount = minOf(count, basePoolEasy.size).coerceAtLeast(1)
                var questions = picker.pick(basePoolEasy, pickCount, profileId)
                if (questions.isEmpty()) {
                    questions = basePoolEasy.shuffled().take(count)
                }
                if (questions.size < count && basePoolEasy.isNotEmpty()) {
                    val used = questions.map { it.id }.toSet()
                    val extra = basePoolEasy.filter { it.id !in used }
                    val qList = questions.toMutableList()
                    var idx = 0
                    while (qList.size < count && extra.isNotEmpty()) {
                        qList.add(extra[idx % extra.size])
                        idx++
                        if (idx > extra.size * 2) break
                    }
                    questions = qList.take(count).shuffled()
                }

                basePool = basePoolEasy
                fallbackPool = emptyList()

                val questionIds = questions.map { it.id }
                roomStore.recordTestCreated(profileId, effectiveTestId, questionIds)
                recordSeenForQuiz(profileId, questionIds)

                android.util.Log.d(
                    TAG,
                    "[GRADE_TEST] selectedGrade=$grade, selectedDifficulty=${selectedDifficulty.name}, easyPool=${basePoolEasy.size}, picked=${questions.size}"
                )

                return questions
            }
        }
    }

    /**
     * G2: Adaptif soru seçimi (AdaptiveQuestionPicker).
     * - %40 dueWrong, %60 fresh
     * - Aynı soru aynı testte tekrar gelmez
     * - Son 2 testte çıkanlar öncelik düşürülür
     * @param testId G4: Test oluşturulunca recordSeenInTest için (null ise üretilir)
     */
    fun pickQuizQuestions(
        levelGroup: LevelGroup,
        count: Int,
        difficulty: QuizDifficulty,
        categories: Set<String> = emptySet(),
        testId: String? = null
    ): List<Question> {
        val (all, _) = loadAllQuestionsWithStats()
        val allPool = if (all.isEmpty()) getFallbackQuestions() else all
        val (pool, _) = buildPoolWithFallback(allPool, levelGroup, difficulty, categories)
        val finalPool = if (pool.isEmpty()) {
            Log.w(TAG, "Pool empty after filters, using global/fallback questions")
            allPool.ifEmpty { getFallbackQuestions() }
        } else pool

        val profileId = ProfileStore(context).getCurrentProfileId()
        val effectiveTestId = testId ?: java.util.UUID.randomUUID().toString()

        val picker = SpacedRepetitionPicker(
            historyDao = com.brainbuddy.app.db.DatabaseProvider.get(context).historyDao(),
            roomStore = roomStore,
            getQuestionIdsFromLastNTests = { pid, n -> roomStore.getQuestionIdsFromLastNTests(pid, n) }
        )
        var questions = picker.pick(finalPool, count, profileId)

        if (questions.isEmpty()) {
            Log.i(TAG, "Adaptive picker returned empty, fallback to shuffled pool")
            questions = finalPool.shuffled().take(count)
        }
        if (questions.size < count && finalPool.isNotEmpty()) {
            val used = questions.map { it.id }.toSet()
            val extra = finalPool.filter { it.id !in used }
            val qList = questions.toMutableList()
            var idx = 0
            while (qList.size < count && extra.isNotEmpty()) {
                qList.add(extra[idx % extra.size])
                idx++
            }
            questions = qList.take(count).shuffled()
        }

        val questionIds = questions.map { it.id }
        roomStore.recordTestCreated(profileId, effectiveTestId, questionIds)
        recordSeenForQuiz(profileId, questionIds)
        return questions
    }

    data class FilterStats(
        val afterDifficulty: Int,
        val afterCategory: Int,
        val afterGrade: Int
    )

    /** Progressive fallback: relax category first, then difficulty, then try alternate level groups. */
    private fun buildPoolWithFallback(
        all: List<Question>,
        levelGroup: LevelGroup,
        difficulty: QuizDifficulty,
        categories: Set<String>
    ): Pair<List<Question>, FilterStats> {
        val afterDiff = all.filter { it.levelGroup == levelGroup && it.difficulty == difficulty }
        val afterCat = if (categories.isNotEmpty()) afterDiff.filter { it.subject.name in categories } else afterDiff

        // 1) Full filter: levelGroup + difficulty + category
        if (afterCat.isNotEmpty()) {
            Log.d(TAG, "Filter: afterDifficulty=${afterDiff.size}, afterCategory=${afterCat.size}, afterGrade=${afterCat.size}")
            return Pair(afterCat, FilterStats(afterDiff.size, afterCat.size, afterCat.size))
        }

        // 2) Relax category: levelGroup + difficulty only
        if (afterDiff.isNotEmpty()) {
            Log.d(TAG, "Filter (relaxed category): afterDifficulty=${afterDiff.size}")
            return Pair(afterDiff, FilterStats(afterDiff.size, 0, afterDiff.size))
        }

        // 3) Relax difficulty: levelGroup only
        val afterGrade = all.filter { it.levelGroup == levelGroup }
        if (afterGrade.isNotEmpty()) {
            Log.d(TAG, "Filter (relaxed difficulty): afterGrade=${afterGrade.size}")
            return Pair(afterGrade, FilterStats(0, 0, afterGrade.size))
        }

        // 4) For AGE_3_5 or GRADE_1_4, fallback to GRADE_5_8 questions (closest match)
        val fallbackGroups = when (levelGroup) {
            LevelGroup.AGE_3_5, LevelGroup.GRADE_1_4 -> listOf(LevelGroup.GRADE_5_8, LevelGroup.GRADE_9_12)
            LevelGroup.GRADE_9_12 -> listOf(LevelGroup.GRADE_5_8)
            LevelGroup.GRADE_5_8 -> listOf(LevelGroup.GRADE_9_12)
        }
        for (alt in fallbackGroups) {
            val pool = all.filter { it.levelGroup == alt }
            if (pool.isNotEmpty()) {
                Log.d(TAG, "Filter (fallback levelGroup=$alt): pool=${pool.size}")
                return Pair(pool, FilterStats(0, 0, pool.size))
            }
        }
        Log.w(TAG, "Filter: no questions after all fallbacks")
        return Pair(emptyList(), FilterStats(0, 0, 0))
    }

    /** Gate questions - sadece grade filtresi ile (grade 2-8). */
    fun pickGateQuestionsByGrade(grade: Int, count: Int = MIN_QUESTIONS_PER_TEST): List<Question> {
        if (grade !in 2..8) return emptyList()
        runBlocking { DbSeeder.seedIfNeeded(context) }
        var pool = roomStore.getQuestionsByGrade(grade)
        if (pool.isEmpty()) pool = getFallbackQuestions().filter { it.grade == grade }
        if (pool.isEmpty()) pool = getFallbackQuestions()
        val profileId = ProfileStore(context).getCurrentProfileId()
        val recentIds = roomStore.getRecentlySeenIdsForProfile(profileId, 50)
        val wrongIds = wrongQuestionStore.getUnfixedWrongIds(14)
        val preferWrong = pool.filter { it.id in wrongIds }.shuffled()
        val preferFresh = pool.filter { it.id !in recentIds && it.id !in wrongIds }.shuffled()
        val fillFrom = pool.filter { it.id in recentIds && it.id !in wrongIds }.shuffled()
        val result = mutableListOf<Question>()
        val used = mutableSetOf<String>()
        for (q in preferWrong + preferFresh + fillFrom) {
            if (result.size >= count) break
            if (q.id !in used) { result.add(q); used.add(q.id) }
        }
        var finalList = result.ifEmpty { pool.shuffled().take(count) }.toMutableList()
        if (finalList.size < count && pool.isNotEmpty()) {
            val usedIds = finalList.map { it.id }.toSet().toMutableSet()
            var idx = 0
            while (finalList.size < count) {
                val q = pool[idx % pool.size]
                if (q.id !in usedIds) { finalList.add(q); usedIds.add(q.id) }
                idx++
                if (idx > pool.size * 2) break
            }
        }
        val toReturn = finalList.shuffled()
        roomStore.recordSeenIdsForProfile(profileId, toReturn.map { it.id })
        return toReturn
    }

    /** Boss questions - sadece grade filtresi ile (grade 2-8). */
    fun pickBossQuestionsByGrade(grade: Int, count: Int = MIN_QUESTIONS_PER_TEST): List<Question> {
        if (grade !in 2..8) return emptyList()
        runBlocking { DbSeeder.seedIfNeeded(context) }
        var pool = roomStore.getQuestionsByGrade(grade)
        if (pool.isEmpty()) pool = getFallbackQuestions().filter { it.grade == grade }
        if (pool.isEmpty()) pool = getFallbackQuestions()
        val hardPool = pool.filter { it.difficulty == QuizDifficulty.HARD }
        val base = if (hardPool.isNotEmpty()) hardPool else pool
        val result = base.shuffled().take(count).toMutableList()
        if (result.size < count && pool.isNotEmpty()) {
            val shuffled = pool.shuffled()
            var idx = 0
            while (result.size < count) {
                result.add(shuffled[idx % shuffled.size])
                idx++
            }
        }
        return result.shuffled()
    }

    /** Remedial questions - sadece grade filtresi ile. */
    fun pickRemedialQuestionsByGrade(grade: Int, count: Int = MIN_QUESTIONS_PER_TEST, weakTopicIds: List<String> = emptyList()): Pair<List<Question>, Boolean> {
        if (grade !in 2..8) return Pair(emptyList(), true)
        runBlocking { DbSeeder.seedIfNeeded(context) }
        val all = roomStore.getQuestionsByGrade(grade).ifEmpty { getFallbackQuestions().filter { it.grade == grade } }
            .ifEmpty { getFallbackQuestions() }
        val allMap = all.associateBy { it.id }
        val userId = com.brainbuddy.app.core.ActiveProfileManager.getActiveProfileId(context)
        val wrongIds = weakTopicIds.ifEmpty { roomStore.getWrongQuestionIds(userId, 14).toList() }
        val weakTopics = wrongIds.mapNotNull { allMap[it]?.subject?.tr }.distinct()
        val byTopic = all.groupBy { it.subject.tr }
        var pool = mutableListOf<Question>()
        for (topic in weakTopics) {
            byTopic[topic]?.let { pool.addAll(it) }
        }
        if (pool.isEmpty()) pool = all.toMutableList()
        val profileId = ProfileStore(context).getCurrentProfileId()
        val recentIds = roomStore.getRecentlySeenIdsForProfile(profileId, 100)
        val sessionIds = mutableSetOf<String>()
        val result = mutableListOf<Question>()
        for (q in pool.shuffled()) {
            if (result.size >= count) break
            if (q.id in sessionIds) continue
            if (q.id in recentIds && pool.size > count * 2) continue
            result.add(q)
            sessionIds.add(q.id)
        }
        val questions = result.ifEmpty { pool.shuffled().take(count) }.ifEmpty { getFallbackQuestions().filter { it.grade == grade }.shuffled().take(count) }
            .ifEmpty { getFallbackQuestions().shuffled().take(count) }
        return Pair(questions, pool.isEmpty())
    }

    /** Retry wrong questions - sadece grade filtresi ile. */
    fun pickRetryWrongQuestionsByGrade(grade: Int): List<Question> {
        if (grade !in 2..8) return emptyList()
        val userId = com.brainbuddy.app.core.ActiveProfileManager.getActiveProfileId(context)
        val wrongIds = roomStore.getAllWrongIds(userId)
        if (wrongIds.isEmpty()) return emptyList()
        runBlocking { DbSeeder.seedIfNeeded(context) }
        val all = roomStore.getQuestionsByGrade(grade).associateBy { it.id }
        val fallback = getFallbackQuestions().filter { it.grade == grade }.associateBy { it.id }
        val allMap = if (all.isEmpty()) fallback else all
        return wrongIds.mapNotNull { allMap[it] }
    }

    /** Boss test: harder question pool. */
    fun pickBossQuestions(levelGroup: LevelGroup, count: Int = MIN_QUESTIONS_PER_TEST): List<Question> {
        val (all, _) = loadAllQuestionsWithStats()
        val allPool = if (all.isEmpty()) getFallbackQuestions() else all
        val hardPool = allPool.filter { it.levelGroup == levelGroup && it.difficulty == QuizDifficulty.HARD }
        val pool = if (hardPool.isNotEmpty()) hardPool else allPool.filter { it.levelGroup == levelGroup }
        val base = if (pool.isEmpty()) allPool else pool
        val result = base.shuffled().take(count).toMutableList()
        if (result.size < count && base.isNotEmpty()) {
            var idx = 0
            val shuffled = base.shuffled()
            while (result.size < count) {
                result.add(shuffled[idx % shuffled.size])
                idx++
            }
        }
        return result.shuffled()
    }

    /** Remedial mini-quiz: focused on weak topics. Prefer lastFailedWrongIds from ProtectionPrefs.
     * weakTopic pool -> if empty -> global pool -> fallback. Never returns empty.
     * @return Pair(questions, usedFallbackDueToEmptyPool) - when true, parent should be warned. */
    fun pickRemedialQuestions(levelGroup: LevelGroup, count: Int = MIN_QUESTIONS_PER_TEST, weakTopicIds: List<String> = emptyList()): Pair<List<Question>, Boolean> {
        val global = getGlobalPool()
        val all = global.associateBy { it.id }
        val userId = com.brainbuddy.app.core.ActiveProfileManager.getActiveProfileId(context)
        val wrongIds = weakTopicIds.ifEmpty { roomStore.getWrongQuestionIds(userId, 14).toList() }
        val weakTopics = wrongIds.mapNotNull { all[it]?.subject?.tr }.distinct()
        val byTopic = all.values.groupBy { it.subject.tr }
        var pool = mutableListOf<Question>()
        for (topic in weakTopics) {
            byTopic[topic]?.let { pool.addAll(it.filter { it.levelGroup == levelGroup }) }
        }
        if (pool.isEmpty()) {
            pool = all.values.filter { it.levelGroup == levelGroup }.toMutableList()
        }
        if (pool.isEmpty()) {
            pool = global.toMutableList()
        }
        val profileId = ProfileStore(context).getCurrentProfileId()
        val recentIds = roomStore.getRecentlySeenIdsForProfile(profileId, 100)
        val sessionIds = mutableSetOf<String>()
        val result = mutableListOf<Question>()
        for (q in pool.shuffled()) {
            if (result.size >= count) break
            if (q.id in sessionIds) continue
            if (q.id in recentIds && pool.size > count * 2) continue
            result.add(q)
            sessionIds.add(q.id)
        }
        var questions: List<Question> = result.ifEmpty { pool.shuffled().take(count) }.ifEmpty { getFallbackQuestions().shuffled().take(count) }
        if (questions.size < count && questions.isNotEmpty()) {
            val fill = questions.toMutableList()
            var idx = 0
            while (fill.size < count) {
                fill.add(questions[idx % questions.size])
                idx++
            }
            questions = fill.shuffled()
        } else if (questions.isEmpty()) {
            questions = getFallbackQuestions().shuffled().take(count)
        }
        val usedFallback = pool.isEmpty() || result.isEmpty()
        return Pair(questions, usedFallback)
    }

    fun pickRetryWrongQuestions(levelGroup: LevelGroup): List<Question> {
        val userId = com.brainbuddy.app.core.ActiveProfileManager.getActiveProfileId(context)
        val wrongIds = roomStore.getAllWrongIds(userId)
        if (wrongIds.isEmpty()) return emptyList()
        val allByLevel = loadAllQuestions().groupBy { it.levelGroup }
        val allMap = (allByLevel[levelGroup] ?: emptyList()).associateBy { it.id }
        var result = wrongIds.mapNotNull { allMap[it] }
        if (result.isEmpty()) {
            val fallbackGroups = when (levelGroup) {
                LevelGroup.AGE_3_5, LevelGroup.GRADE_1_4 -> listOf(LevelGroup.GRADE_5_8, LevelGroup.GRADE_9_12)
                LevelGroup.GRADE_9_12 -> listOf(LevelGroup.GRADE_5_8)
                LevelGroup.GRADE_5_8 -> listOf(LevelGroup.GRADE_9_12)
            }
            for (alt in fallbackGroups) {
                val altMap = (allByLevel[alt] ?: emptyList()).associateBy { it.id }
                result = wrongIds.mapNotNull { altMap[it] }
                if (result.isNotEmpty()) break
            }
        }
        return result
    }

    /** Gate quiz: new quiz each attempt. Shuffled pool + recent-question blacklist. Same question cannot repeat within test. */
    fun pickGateQuestions(levelGroup: LevelGroup, count: Int = MIN_QUESTIONS_PER_TEST): List<Question> {
        val profileId = ProfileStore(context).getCurrentProfileId()
        val global = getGlobalPool()
        val pool = global.filter { it.levelGroup == levelGroup }.ifEmpty { global }
        val recentIds = roomStore.getRecentlySeenIdsForProfile(profileId, 50)
        val wrongIds = wrongQuestionStore.getUnfixedWrongIds(14)
        val preferWrong = pool.filter { it.id in wrongIds }.shuffled()
        val preferFresh = pool.filter { it.id !in recentIds && it.id !in wrongIds }.shuffled()
        val fillFrom = pool.filter { it.id in recentIds && it.id !in wrongIds }.shuffled()
        val result = mutableListOf<Question>()
        val used = mutableSetOf<String>()
        for (q in preferWrong) {
            if (result.size >= count) break
            if (q.id !in used) {
                result.add(q)
                used.add(q.id)
            }
        }
        for (q in preferFresh) {
            if (result.size >= count) break
            if (q.id !in used) {
                result.add(q)
                used.add(q.id)
            }
        }
        for (q in fillFrom) {
            if (result.size >= count) break
            if (q.id !in used) {
                result.add(q)
                used.add(q.id)
            }
        }
        var finalList = result.ifEmpty { pool.shuffled().take(count) }.toMutableList()
        if (finalList.size < count && pool.isNotEmpty()) {
            val usedIds = finalList.map { it.id }.toSet().toMutableSet()
            val shuffled = pool.shuffled()
            var idx = 0
            while (finalList.size < count && shuffled.isNotEmpty()) {
                val q = shuffled[idx % shuffled.size]
                if (q.id !in usedIds) {
                    finalList.add(q)
                    usedIds.add(q.id)
                }
                idx++
                if (idx > shuffled.size * 2) break
            }
        }
        val toReturn = finalList.shuffled()
        roomStore.recordSeenIdsForProfile(profileId, toReturn.map { it.id })
        return toReturn
    }

    /** Records seen IDs for pickQuizQuestions per-user variety (avoids repeat across attempts). */
    fun recordSeenForQuiz(profileId: String, questionIds: List<String>) {
        roomStore.recordSeenIdsForProfile(profileId, questionIds)
    }

    /** Insert test snapshot for replay and history. Scoped by active profile. */
    fun insertSnapshot(testId: String, score: Int, total: Int, questionIds: List<String>, userAnswers: Map<String, Int>, wrongIds: List<String>, subjectBreakdown: String? = null) {
        val profileId = ActiveProfileManager.getActiveProfileId(context)
        roomStore.insertSnapshot(testId, score, total, questionIds, userAnswers, wrongIds, subjectBreakdown, profileId)
    }

    fun getLastSnapshots(profileId: String? = null, limit: Int = 20) =
        roomStore.getLastSnapshots(profileId ?: ActiveProfileManager.getActiveProfileId(context), limit)

    fun getLevelGroupFromPrefs(): LevelGroup {
        return when (ProtectionPrefs(context).studentLevel()) {
            com.brainbuddy.app.core.StudentLevel.AGE_3_5 -> LevelGroup.AGE_3_5
            com.brainbuddy.app.core.StudentLevel.GRADES_1_4 -> LevelGroup.GRADE_1_4
            com.brainbuddy.app.core.StudentLevel.GRADES_5_8 -> LevelGroup.GRADE_5_8
            com.brainbuddy.app.core.StudentLevel.GRADES_9_12 -> LevelGroup.GRADE_9_12
        }
    }
}
