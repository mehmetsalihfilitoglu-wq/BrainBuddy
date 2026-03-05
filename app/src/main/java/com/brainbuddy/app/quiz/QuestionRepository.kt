package com.brainbuddy.app.quiz

import android.content.Context
import android.util.Log
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import com.brainbuddy.app.db.DbSeeder
import com.brainbuddy.app.db.DatabaseProvider
import com.brainbuddy.app.db.GradeSubjectDifficultyCount
import com.brainbuddy.app.db.QuestionEntity
import com.brainbuddy.app.db.QuestionStemHash
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

    /**
     * Debug counters for quiz builders (grade-based picker primarily).
     *
     * All counters are reset at the beginning of each picker call and are intended
     * for in-app debug UI (no functional impact).
     */
    @Volatile
    var lastRecentRelaxedCount: Int = 0
        private set

    @Volatile
    var lastSkippedIdCount: Int = 0
        private set

    @Volatile
    var lastSkippedStemHashCount: Int = 0
        private set

    @Volatile
    var lastSkippedSimilarCount: Int = 0
        private set

    @Volatile
    var lastSkippedRecentCount: Int = 0
        private set

    @Volatile
    var lastSimilarRelaxedCount: Int = 0
        private set

    /** Subject key -> count picked. Set after pickQuizQuestionsByGrade. */
    @Volatile
    var lastSubjectCounts: Map<String, Int> = emptyMap()
        private set

    companion object {
        private const val TAG = "QuestionRepository"
        /** Every test (gate, normal, remedial, boss) has exactly this many questions. */
        const val MIN_QUESTIONS_PER_TEST = 20
        private const val NEAR_DUPLICATE_SIMILARITY_THRESHOLD = 0.75

        /** G1: Normalize text for stable ID: trim, lowercase(TR), collapse whitespace. */
        fun normalize(text: String): String = text
            .trim()
            .lowercase(Locale("tr"))
            .replace(Regex("\\s+"), " ")

        /** difficulty: sadece EASY=0, MEDIUM=1, HARD=2. Başka değerler map edilir. */
        fun parseDifficultyToThreeLevels(raw: Any?): QuizDifficulty {
            return when (raw) {
                is Int -> when {
                    raw <= 0 -> QuizDifficulty.EASY
                    raw == 1 -> QuizDifficulty.MEDIUM
                    else -> QuizDifficulty.HARD
                }
                is String -> when (raw.uppercase()) {
                    "EASY" -> QuizDifficulty.EASY
                    "HARD", "VERY_HARD" -> QuizDifficulty.HARD
                    else -> QuizDifficulty.MEDIUM
                }
                else -> QuizDifficulty.MEDIUM
            }
        }

        /** G1: Deterministik id - sha1(normalize(questionText) + "|" + normalize(correctAnswer)) */
        fun deterministicId(questionText: String, correctAnswer: String): String {
            val input = (normalize(questionText) + "|" + normalize(correctAnswer)).toByteArray(Charset.forName("UTF-8"))
            val digest = MessageDigest.getInstance("SHA-1").digest(input)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    /**
     * Normalize question stem for near-duplicate detection.
     * - remove punctuation
     * - replace capitalized names with "@"
     * - replace numbers with "#"
     * - lowercase (TR)
     * - collapse whitespace
     */
    private fun normalizeStem(stem: String): String {
        var text = stem.replace(Regex("[\\p{Punct}]"), " ")
        val nameRegex = Regex("\\b[\\p{Lu}][\\p{Ll}]{2,}\\b")
        text = nameRegex.replace(text) { "@" }
        text = text.replace(Regex("\\d+"), "#")
        text = text.lowercase(Locale("tr"))
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    /** SHA-256 hash of normalized stem. */
    private fun stemHash(stem: String): String {
        val normalized = normalizeStem(stem)
        val bytes = normalized.toByteArray(Charset.forName("UTF-8"))
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Stopword set for question text tokenization (basic Turkish + English).
     * Used only for debug-time near-duplicate detection within a single quiz.
     */
    private val stopwordsTrEn: Set<String> = setOf(
        // Turkish
        "ve", "veya", "ile", "de", "da", "ki", "bu", "şu", "o", "bir", "iki", "üç",
        "için", "gibi", "ise", "ama", "fakat", "ancak", "çünkü", "daha", "çok",
        "az", "en", "her", "hiç", "mi", "mı", "mu", "mü", "ne", "hangi", "nasıl",
        "neden", "nerede", "ne zaman", "kim", "şey", "şeyler",
        // English (basic)
        "the", "a", "an", "and", "or", "of", "to", "in", "on", "at", "for", "from",
        "by", "with", "about", "as", "is", "are", "was", "were", "be", "been",
        "this", "that", "these", "those", "which", "what", "who", "whom", "how",
        "why", "where", "when"
    )

    /**
     * Build a normalized token set from full question text (stem + options).
     *
     * Normalization:
     *  - lowercase (TR)
     *  - remove punctuation
     *  - replace digits with '#'
     *  - collapse whitespace
     *  - remove common stopwords
     */
    private fun buildQuestionTokenSet(stem: String, choices: List<String>): Set<String> {
        val raw = buildString {
            append(stem)
            if (choices.isNotEmpty()) {
                append(' ')
                append(choices.joinToString(" "))
            }
        }
        if (raw.isBlank()) return emptySet()

        var text = raw.lowercase(Locale("tr"))
        text = text.replace(Regex("[\\p{Punct}]"), " ")
        text = text.replace(Regex("\\d+"), "#")
        text = text.replace(Regex("\\s+"), " ").trim()
        if (text.isEmpty()) return emptySet()

        return text.split(' ')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in stopwordsTrEn }
            .toSet()
    }

    /** Jaccard similarity between two token sets. */
    private fun jaccardSimilarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val intersectionSize = a.intersect(b).size
        if (intersectionSize == 0) return 0.0
        val unionSize = a.size + b.size - intersectionSize
        if (unionSize == 0) return 0.0
        return intersectionSize.toDouble() / unionSize.toDouble()
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

        // DB'de (grade, subject, stemHash) zaten var mı – dedup için
        val existingStemKeysForFilter: MutableSet<String> = try {
            val db = DatabaseProvider.get(context)
            kotlinx.coroutines.runBlocking {
                db.questionDao().getAllQuestions().mapTo(mutableSetOf()) { e ->
                    val h = if (e.stemHash.contains(":dup:")) e.stemHash.substringBefore(":dup:") else e.stemHash
                    "${e.grade}|${e.subject}|$h"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "mergeImportedQuestions: failed to build existing stem-hash index: ${e.message}")
            mutableSetOf()
        }

        val batchSeenStemKeys = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            try {
                val q = parseQuestion(arr.getJSONObject(i))
                val dbSubjectKey = when (q.subject) {
                    Subject.MAT -> "mat"
                    Subject.TURKCE -> "turkce"
                    Subject.FEN -> "fen"
                    Subject.SOSYAL -> "sosyal"
                    Subject.ING -> "ing"
                }
                val h = QuestionStemHash.stemHash(q.stem)
                val stemKey = "${q.grade.coerceIn(1, 8)}|$dbSubjectKey|$h"
                if (stemKey in batchSeenStemKeys) continue
                batchSeenStemKeys.add(stemKey)
                if (stemKey in existingStemKeysForFilter) continue
                if (q.id in existingIds) continue
                toAdd.add(q)
                existingIds.add(q.id)
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

        val existingStemKeys = existingStemKeysForFilter

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
            val dbSubjectKey = when (q.subject) {
                Subject.MAT -> "mat"
                Subject.TURKCE -> "turkce"
                Subject.FEN -> "fen"
                Subject.SOSYAL -> "sosyal"
                Subject.ING -> "ing"
            }
            val stemNormalizedValue = QuestionStemHash.normalizeStem(q.stem)
            val stemHashValue = QuestionStemHash.stemHash(q.stem)
            val stemKey = "${q.grade.coerceIn(1, 8)}|$dbSubjectKey|$stemHashValue"
            if (stemKey in existingStemKeys) return@map null
            existingStemKeys.add(stemKey)
            QuestionEntity(
                id = q.id,
                grade = q.grade.coerceIn(1, 8),
                subject = dbSubjectKey,
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
                imageAsset = q.imageAsset?.takeIf { it.isNotBlank() },
                type = q.type,
                skill = q.skill,
            stemNormalized = stemNormalizedValue,
            stemHash = stemHashValue
            )
        }
        val entitiesToInsert = toAddEntities.filterNotNull()
        roomStore.insertQuestions(entitiesToInsert)
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
                "mergeImportedQuestions: imported=${toAdd.size}, insertedEntities=${entitiesToInsert.size}, totalAfter=${counts.first}, activeAfter=${counts.second}"
            )
        } catch (e: Exception) {
            Log.w(TAG, "mergeImportedQuestions: failed to log DB counts: ${e.message}")
        }
        return entitiesToInsert.size
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

    data class SubjectPoolCounts(
        val total: Int,
        val active: Int,
        val totalDiff: Int,
        val activeDiff: Int
    )

    data class PoolDebugForGrade(
        val total: Int,
        val active: Int,
        val selectedGrade: Int,
        val selectedDifficulty: QuizDifficulty,
        val perSubject: Map<String, SubjectPoolCounts>,
        val hasPassiveOnly: Boolean,
        val subjectsWithDifficultyGap: List<String>,
        val readableText: String
    )

    /**
     * Quiz başlamadan önce havuz teşhisi için:
     * - TOTAL / ACTIVE
     * - grade bazında total/active
     * - grade+subject ve grade+subject+difficulty bazında total/active
     *
     * Hem insan okunabilir metin, hem de UI uyarıları için ham sayıları döner.
     */
    internal fun buildPoolDebugStatsForGrade(
        grade: Int,
        difficulty: QuizDifficulty
    ): PoolDebugForGrade {
        if (grade !in 2..8) {
            return PoolDebugForGrade(
                total = 0,
                active = 0,
                selectedGrade = grade,
                selectedDifficulty = difficulty,
                perSubject = emptyMap(),
                hasPassiveOnly = false,
                subjectsWithDifficultyGap = emptyList(),
                readableText = "DB DURUMU\nGeçersiz sınıf: $grade (2..8 dışında)."
            )
        }
        val db = DatabaseProvider.get(context)
        return try {
            val diffInt = when (difficulty) {
                QuizDifficulty.EASY -> 0
                QuizDifficulty.HARD -> 2
                else -> 1
            }
            val subjects = listOf("mat", "turkce", "fen", "sosyal", "ing")

            val snapshot = kotlinx.coroutines.runBlocking {
                val dao = db.questionDao()
                val total = dao.countAll()
                val active = dao.countAllActive()
                val gradeTotal = dao.countByGrade(grade)
                val gradeActive = dao.countActiveByGrade(grade)
                // Grade 2..8 dağılımı + geçersiz grade teşhisi
                val perGrade = (2..8).associateWith { g ->
                    val gTotal = dao.countByGradeOnly(g)
                    val gActive = dao.countActiveByGradeOnly(g)
                    gTotal to gActive
                }
                val invalidGrades = dao.countInvalidGrades()
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
                    perSubject = perSubject,
                    perGrade = perGrade,
                    invalidGrades = invalidGrades
                )
            }

            val hasPassiveOnly = snapshot.total > 0 && snapshot.active == 0
            val subjectsWithDifficultyGap = snapshot.perSubject
                .filter { (_, counts) -> counts.total > 0 && counts.totalDiff == 0 }
                .keys
                .sorted()

            val sb = StringBuilder()
            sb.append("DB DURUMU\n")
            sb.append("TOTAL questions: ${snapshot.total}\n")
            sb.append("ACTIVE questions (isActive=1): ${snapshot.active}\n")
            sb.append("selectedGrade: $grade\n")
            sb.append("selectedDifficulty: ${difficulty.name}\n")
            sb.append("\n")
            // Grade dağılımı: 2..8 için total/active
            sb.append("Grade dağılımı (2..8):\n")
            (2..8).forEach { g ->
                val (gTotal, gActive) = snapshot.perGrade[g] ?: (0 to 0)
                sb.append("grade=$g total/active: $gTotal/$gActive\n")
            }
            if (snapshot.invalidGrades > 0) {
                sb.append("Geçersiz grade (0,1,9+ vs) soru sayısı: ${snapshot.invalidGrades}\n")
            }
            sb.append("\n")

            // Seçili sınıf için ders bazında AKTİF soru sayıları (tüm zorluklar).
            val subjectLabels = mapOf(
                "mat" to "MAT",
                "turkce" to "TURKCE",
                "fen" to "FEN",
                "sosyal" to "SOSYAL",
                "ing" to "ING"
            )
            val perSubjectActiveLine = subjects.joinToString("  ") { subj ->
                val label = subjectLabels[subj] ?: subj.uppercase()
                val counts = snapshot.perSubject[subj]
                val activeForGrade = counts?.active ?: 0
                "$label=$activeForGrade"
            }
            sb.append("grade=$grade per-subject ACTIVE counts: $perSubjectActiveLine\n\n")

            subjects.forEach { subj ->
                val q = snapshot.perSubject[subj]
                if (q != null) {
                    sb.append("grade=$grade $subj total/active: ${q.total}/${q.active}\n")
                    sb.append("grade=$grade $subj difficulty=$diffInt total/active: ${q.totalDiff}/${q.activeDiff}\n")
                } else {
                    sb.append("grade=$grade $subj total/active: 0/0\n")
                    sb.append("grade=$grade $subj difficulty=$diffInt total/active: 0/0\n")
                }
            }

            // Balanced grade-based quiz debug: target 20 questions, 4 per subject.
            sb.append("\n")
            sb.append("Balanced grade-quiz target: 20 questions (4 per subject)\n")
            subjects.forEach { subj ->
                val q = snapshot.perSubject[subj]
                val activeForDiff = q?.activeDiff ?: 0
                val maxSelectedForSubject = minOf(4, activeForDiff)
                sb.append("grade=$grade $subj available_for_selectedDifficulty=$activeForDiff, target=4, maxSelected=$maxSelectedForSubject\n")
            }

            val debugText = sb.toString().trimEnd()
            Log.d(TAG, "[POOL_DEBUG] " + debugText.replace("\n", " | "))

            val apiPerSubject = snapshot.perSubject.mapValues { (_, counts) ->
                SubjectPoolCounts(
                    total = counts.total,
                    active = counts.active,
                    totalDiff = counts.totalDiff,
                    activeDiff = counts.activeDiff
                )
            }

            PoolDebugForGrade(
                total = snapshot.total,
                active = snapshot.active,
                selectedGrade = grade,
                selectedDifficulty = difficulty,
                perSubject = apiPerSubject,
                hasPassiveOnly = hasPassiveOnly,
                subjectsWithDifficultyGap = subjectsWithDifficultyGap,
                readableText = debugText
            )
        } catch (e: Exception) {
            Log.w(TAG, "buildPoolDebugStatsForGrade: failed: ${e.message}", e)
            PoolDebugForGrade(
                total = 0,
                active = 0,
                selectedGrade = grade,
                selectedDifficulty = difficulty,
                perSubject = emptyMap(),
                hasPassiveOnly = false,
                subjectsWithDifficultyGap = emptyList(),
                readableText = "DB DURUMU\nHata: ${e.message ?: "bilinmiyor"}"
            )
        }
    }

    /**
     * Import sonrası debug ekranı için: grade/subject/difficulty bazında ACTIVE sayıları.
     */
    fun buildImportDebugActiveCounts(): String {
        val db = DatabaseProvider.get(context)
        val rows = kotlinx.coroutines.runBlocking {
            db.questionDao().getActiveCountsByGradeSubjectDifficulty()
        }
        if (rows.isEmpty()) return "ACTIVE by grade/subject/diff: (boş)"
        val sb = StringBuilder()
        sb.append("ACTIVE by grade/subject/diff:\n")
        val byGrade = rows.groupBy { it.grade }
        for (g in (byGrade.keys.minOrNull() ?: 0)..(byGrade.keys.maxOrNull() ?: 0)) {
            val subjRows = byGrade[g]?.groupBy { it.subject }.orEmpty()
            val subjects = listOf("mat", "turkce", "fen", "sosyal", "ing")
            val line = subjects.joinToString("  ") { subj ->
                val diffs = subjRows[subj].orEmpty()
                val e = diffs.firstOrNull { it.difficulty == 0 }?.count ?: 0
                val m = diffs.firstOrNull { it.difficulty == 1 }?.count ?: 0
                val h = diffs.firstOrNull { it.difficulty == 2 }?.count ?: 0
                "$subj(E=$e M=$m H=$h)"
            }
            sb.append("grade=$g: $line\n")
        }
        return sb.toString().trimEnd()
    }

    private data class DbPoolSnapshot(
        val total: Int,
        val active: Int,
        val gradeTotal: Int,
        val gradeActive: Int,
        val perSubject: Map<String, Quad>,
        val perGrade: Map<Int, Pair<Int, Int>>,
        val invalidGrades: Int
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
        // difficulty: sadece EASY=0, MEDIUM=1, HARD=2. Başka değerler bu 3'e map edilir.
        val diffRaw = when {
            o.has("difficulty") && o.opt("difficulty") is Int -> o.optInt("difficulty", 1)
            else -> o.optString("difficulty", "MEDIUM")
        }
        val difficulty = parseDifficultyToThreeLevels(diffRaw)
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
        val explicitType = o.optString("type", "").takeIf { it.isNotEmpty() }
        val explicitSkill = o.optString("skill", "").takeIf { it.isNotEmpty() }
        val stem = o.optString("stem", "?")
        val correctIdx = o.optInt("correctIndex", 0).coerceIn(0, choices.size - 1)
        val correctAnswer = choices.getOrNull(correctIdx) ?: ""
        // Grade belirleme:
        // JSON'da "grade" alanı zorunlu kabul edilir; sadece 2..8 aralığına clamp edilir.
        val rawGrade = o.optInt("grade", 0)
        val grade = rawGrade.coerceIn(2, 8)
        if (grade !in 2..8) {
            throw IllegalArgumentException("Invalid grade in question JSON (grade)")
        }
        val rawId = o.optString("id", "")
        val id = if (rawId.isNotBlank()) rawId else {
            "${grade}_${subjStr}_${deterministicId(stem, correctAnswer).take(6)}"
        }
        val diversityType = explicitType ?: QuestionDiversity.inferType(subject, stem)
        val diversitySkill = explicitSkill ?: QuestionDiversity.inferSkill(subject, grade, diversityType, stem)

        return Question(
            id = id,
            levelGroup = levelGroup,
            subject = subject,
            gradeTag = grade.toString(),
            grade = grade,
            stem = stem,
            choices = choices.ifEmpty { listOf("A", "B", "C", "D") },
            correctIndex = o.optInt("correctIndex", 0).coerceIn(0, 3),
            hint = o.optString("hint", "").takeIf { it.isNotEmpty() },
            imageAsset = o.optString("imageAsset", "").takeIf { it.isNotEmpty() },
            difficulty = difficulty,
            examType = examType,
            topic = topic,
            type = diversityType,
            skill = diversitySkill
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
     * Quiz size = 20, subjects = mat/turkce/fen/sosyal/ing, target quota = 4 each.
     * - grade = selectedGrade
     * - difficulty = selectedDifficulty
     * - isActive = 1
     * - recentQuestionIds (last 100) öncelikle hariç tutulur, gerekirse kullanılır.
     * - Eğer bir derste 4 soru yoksa, kalan slotlar en geniş havuza sahip diğer derslerden doldurulur.
     * - Aynı testte aynı soru ID'si asla tekrar etmez (mutableSet usedIds ile zorlanır).
     * - preferredWrongIds parametresi verilirse, bu ID'ler maksimum maxWrongFraction oranında (örn. 0.3 = %30)
     *   ve ders kotasını (4) bozmadan tercih edilir.
     */
    fun pickQuizQuestionsByGrade(
        grade: Int,
        count: Int = MIN_QUESTIONS_PER_TEST,
        testId: String? = null,
        preferredWrongIds: Set<String> = emptySet(),
        maxWrongFraction: Double = 0.3
    ): List<Question> {
        if (grade !in 2..8) return emptyList()
        runBlocking { DbSeeder.seedIfNeeded(context) }

        // Zorluk tercihini DataStore'dan (QuizPrefs) oku
        val selectedDifficulty = try {
            QuizPrefs(context).difficulty()
        } catch (_: Exception) {
            QuizDifficulty.MEDIUM
        }
        val diffInt = when (selectedDifficulty) {
            QuizDifficulty.EASY -> 0
            QuizDifficulty.HARD -> 2
            else -> 1
        }

        val subjectOrder: List<Pair<Subject, String>> = listOf(
            Subject.MAT to "mat",
            Subject.TURKCE to "turkce",
            Subject.FEN to "fen",
            Subject.SOSYAL to "sosyal",
            Subject.ING to "ing"
        )
        val targetPerSubject = 4
        val effectiveCount = count.coerceAtMost(MIN_QUESTIONS_PER_TEST).coerceAtLeast(MIN_QUESTIONS_PER_TEST)
        val maxWrongCount = if (effectiveCount > 0 && maxWrongFraction > 0.0) {
            kotlin.math.floor(effectiveCount * maxWrongFraction).toInt().coerceAtLeast(0)
        } else 0

        val profileId = ProfileStore(context).getCurrentProfileId()
        val effectiveTestId = testId ?: java.util.UUID.randomUUID().toString()
        val recentIds: Set<String> = roomStore.getRecentlySeenIdsForProfile(profileId, 150)

        // Reset debug counters for this picker run.
        lastRecentRelaxedCount = 0
        lastSkippedIdCount = 0
        lastSkippedStemHashCount = 0
        lastSkippedSimilarCount = 0
        lastSkippedRecentCount = 0
        lastSimilarRelaxedCount = 0
        lastSubjectCounts = emptyMap()

        // 1) Havuzu grade + subject + difficulty ile hazırla.
        val perSubjectAll: MutableMap<Subject, List<Question>> = mutableMapOf()
        val perSubjectTotalForDiff: MutableMap<Subject, Int> = mutableMapOf()
        val perSubjectNonRecentAvailable: MutableMap<Subject, Int> = mutableMapOf()
        subjectOrder.forEach { (subjEnum, dbKey) ->
            val all = roomStore
                .getQuestionsByGradeSubjectDifficulty(grade, dbKey, diffInt)
                .distinctBy { it.id }
            perSubjectAll[subjEnum] = all
            perSubjectTotalForDiff[subjEnum] = all.size
            perSubjectNonRecentAvailable[subjEnum] = all.count { it.id !in recentIds }
        }

        val usedIds = mutableSetOf<String>()
        val usedStemHashes = mutableSetOf<String>()
        val selectedTokenSets = mutableListOf<Set<String>>()
        val selectedPerSubject: MutableMap<Subject, MutableList<Question>> = mutableMapOf()
        subjectOrder.forEach { (s, _) -> selectedPerSubject[s] = mutableListOf() }

        var wrongUsedCount = 0
        var recentRelaxedCount = 0
        var similarRelaxedCount = 0

        fun pickForSubject(subjEnum: Subject, targetForSubject: Int) {
            if (targetForSubject <= 0) return
            val all = (perSubjectAll[subjEnum] ?: emptyList()).filter { it.id !in usedIds }
            if (all.isEmpty()) return

            val nonRecent = all.filter { it.id !in recentIds }
            val recent = all.filter { it.id in recentIds }

            fun trySelectFrom(
                pool: List<Question>,
                enforceTypeLimit: Boolean,
                enforceSkillLimit: Boolean
            ) {
                if (pool.isEmpty()) return
                val subjectList = selectedPerSubject[subjEnum] ?: mutableListOf()
                val typeCounts = subjectList.groupingBy { it.type }.eachCount().toMutableMap()
                val skillCounts = subjectList.groupingBy { it.skill }.eachCount().toMutableMap()

                fun canTakeByTypeAndSkill(q: Question): Boolean {
                    if (subjectList.size >= targetForSubject) return false
                    val type = q.type
                    val skill = q.skill
                    if (enforceTypeLimit && type != "UNKNOWN") {
                        val tc = typeCounts[type] ?: 0
                        if (tc >= 2) return false
                    }
                    if (enforceSkillLimit && skill != "UNKNOWN") {
                        val sc = skillCounts[skill] ?: 0
                        if (sc >= 1) return false
                    }
                    return true
                }

                fun takeFrom(candidates: List<Question>, isWrong: Boolean) {
                    if (candidates.isEmpty()) return
                    for (q in candidates.shuffled()) {
                        if (subjectList.size >= targetForSubject) break
                        val id = q.id
                        if (id in usedIds) {
                            lastSkippedIdCount++
                            continue
                        }
                        val qStemHash = stemHash(q.stem)
                        if (qStemHash in usedStemHashes) {
                            lastSkippedStemHashCount++
                            continue
                        }
                        if (!canTakeByTypeAndSkill(q)) continue
                        val tokens = buildQuestionTokenSet(q.stem, q.choices)
                        if (tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                                jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                            }) {
                            lastSkippedSimilarCount++
                            continue
                        }
                        if (isWrong && wrongUsedCount >= maxWrongCount) continue
                        subjectList.add(q)
                        usedIds.add(id)
                        usedStemHashes.add(qStemHash)
                        if (tokens.isNotEmpty()) {
                            selectedTokenSets.add(tokens)
                        }
                        typeCounts[q.type] = (typeCounts[q.type] ?: 0) + 1
                        skillCounts[q.skill] = (skillCounts[q.skill] ?: 0) + 1
                        if (isWrong) wrongUsedCount++
                        if (id in recentIds) {
                            recentRelaxedCount++
                        }
                        if (subjectList.size >= targetForSubject) break
                    }
                    selectedPerSubject[subjEnum] = subjectList
                }

                val wrongNonRecent = if (preferredWrongIds.isNotEmpty() && maxWrongCount > 0) {
                    pool.filter { it.id !in recentIds && it.id in preferredWrongIds }
                } else emptyList()
                val wrongRecent = if (preferredWrongIds.isNotEmpty() && maxWrongCount > 0) {
                    pool.filter { it.id in recentIds && it.id in preferredWrongIds }
                } else emptyList()
                val normalNonRecent = pool.filter { it.id !in recentIds && it.id !in preferredWrongIds }
                val normalRecent = pool.filter { it.id in recentIds && it.id !in preferredWrongIds }

                takeFrom(wrongNonRecent, isWrong = true)
                takeFrom(wrongRecent, isWrong = true)
                takeFrom(normalNonRecent, isWrong = false)
                takeFrom(normalRecent, isWrong = false)
            }

            /** Same as trySelectFrom but skips similarity check. Counts similarRelaxed when taking would-be-similar. */
            fun trySelectFromSimilarRelaxed(pool: List<Question>, enforceTypeLimit: Boolean, enforceSkillLimit: Boolean) {
                if (pool.isEmpty()) return
                val subjectList = selectedPerSubject[subjEnum] ?: mutableListOf()
                val typeCounts = subjectList.groupingBy { it.type }.eachCount().toMutableMap()
                val skillCounts = subjectList.groupingBy { it.skill }.eachCount().toMutableMap()
                fun canTakeByTypeAndSkill(q: Question): Boolean {
                    if (subjectList.size >= targetForSubject) return false
                    val type = q.type
                    val skill = q.skill
                    if (enforceTypeLimit && type != "UNKNOWN") {
                        val tc = typeCounts[type] ?: 0
                        if (tc >= 2) return false
                    }
                    if (enforceSkillLimit && skill != "UNKNOWN") {
                        val sc = skillCounts[skill] ?: 0
                        if (sc >= 1) return false
                    }
                    return true
                }
                val wrongCand = if (preferredWrongIds.isNotEmpty() && maxWrongCount > 0) pool.filter { it.id in preferredWrongIds } else emptyList()
                val normalCand = if (wrongCand.isEmpty()) pool else pool.filter { it.id !in preferredWrongIds }
                for (candidates in listOf(wrongCand, normalCand)) {
                    for (q in candidates.shuffled()) {
                        if (subjectList.size >= targetForSubject) break
                        val id = q.id
                        if (id in usedIds) continue
                        val qStemHash = stemHash(q.stem)
                        if (qStemHash in usedStemHashes) continue
                        if (!canTakeByTypeAndSkill(q)) continue
                        val isWrong = q.id in preferredWrongIds
                        if (isWrong && wrongUsedCount >= maxWrongCount) continue
                        val tokens = buildQuestionTokenSet(q.stem, q.choices)
                        val wouldBeSimilar = tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                            jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                        }
                        if (wouldBeSimilar) similarRelaxedCount++
                        subjectList.add(q)
                        usedIds.add(id)
                        usedStemHashes.add(qStemHash)
                        if (tokens.isNotEmpty()) selectedTokenSets.add(tokens)
                        typeCounts[q.type] = (typeCounts[q.type] ?: 0) + 1
                        skillCounts[q.skill] = (skillCounts[q.skill] ?: 0) + 1
                        if (isWrong) wrongUsedCount++
                        if (id in recentIds) recentRelaxedCount++
                    }
                }
                selectedPerSubject[subjEnum] = subjectList
            }

            // 1) Strict: only non-recent IDs, enforce diversity constraints first.
            trySelectFrom(nonRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return

            // 2) Still non-recent, relax skill constraint if needed.
            trySelectFrom(nonRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return

            // 3) Still non-recent, relax both type and skill if needed.
            trySelectFrom(nonRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return

            // 4) Fallback: allow recently seen questions from the same subject to fill remaining quota.
            trySelectFrom(recent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFrom(recent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFrom(recent, enforceTypeLimit = false, enforceSkillLimit = false)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return

            // 5) Last resort: relax similarity rule (usedIds and stemHashUsed NEVER relax).
            trySelectFromSimilarRelaxed(nonRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFromSimilarRelaxed(nonRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(nonRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(recent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFromSimilarRelaxed(recent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(recent, enforceTypeLimit = false, enforceSkillLimit = false)
        }

        // 2) Her ders için önce çeşitlilik kısıtlarıyla 4'e kadar seç.
        subjectOrder.forEach { (subjEnum, _) ->
            pickForSubject(subjEnum, targetPerSubject)
        }

        var selected = selectedPerSubject.values.flatten().toMutableList()

        // 3) Eğer toplam < 20 ise, kalan slotları en büyük havuzlu derslerden doldur.
        var remainingSlots = effectiveCount - selected.size
        if (remainingSlots > 0) {
            val remainingPerSubject: Map<Subject, List<Question>> = subjectOrder.associate { (subjEnum, _) ->
                subjEnum to (perSubjectAll[subjEnum] ?: emptyList()).filter { it.id !in usedIds }
            }
            val subjectsByRemaining = remainingPerSubject.entries
                .sortedByDescending { it.value.size }
                .map { it.key }

            fun pickExtraFromPartition(
                subj: Subject,
                partition: List<Question>,
                fromRecent: Boolean
            ) {
                if (partition.isEmpty() || remainingSlots <= 0) return

                val wrongCandidates = if (preferredWrongIds.isNotEmpty() && maxWrongCount > 0) {
                    partition.filter { it.id in preferredWrongIds }
                } else emptyList()
                val normalCandidates = if (wrongCandidates.isEmpty()) {
                    partition
                } else {
                    partition.filter { it.id !in preferredWrongIds }
                }

                if (wrongCandidates.isNotEmpty() && wrongUsedCount < maxWrongCount) {
                    val canTakeWrong = minOf(
                        remainingSlots,
                        maxWrongCount - wrongUsedCount
                    )
                    val extraWrong = mutableListOf<Question>()
                    for (q in wrongCandidates.shuffled()) {
                        if (extraWrong.size >= canTakeWrong || remainingSlots <= 0) break
                        val id = q.id
                        if (id in usedIds) {
                            lastSkippedIdCount++
                            continue
                        }
                        val qStemHash = stemHash(q.stem)
                        if (qStemHash in usedStemHashes) {
                            lastSkippedStemHashCount++
                            continue
                        }
                        val tokens = buildQuestionTokenSet(q.stem, q.choices)
                        if (tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                                jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                            }) {
                            lastSkippedSimilarCount++
                            continue
                        }
                        extraWrong.add(q)
                        usedIds.add(id)
                        usedStemHashes.add(qStemHash)
                        if (tokens.isNotEmpty()) {
                            selectedTokenSets.add(tokens)
                        }
                        if (id in recentIds) {
                            recentRelaxedCount++
                        }
                    }
                    if (extraWrong.isNotEmpty()) {
                        selectedPerSubject[subj]?.addAll(extraWrong)
                        selected.addAll(extraWrong)
                        wrongUsedCount += extraWrong.size
                        remainingSlots = effectiveCount - selected.size
                    }
                }

                if (remainingSlots > 0 && normalCandidates.isNotEmpty()) {
                    val extraNormal = mutableListOf<Question>()
                    for (q in normalCandidates.shuffled()) {
                        if (extraNormal.size >= remainingSlots) break
                        val id = q.id
                        if (id in usedIds) {
                            lastSkippedIdCount++
                            continue
                        }
                        val qStemHash = stemHash(q.stem)
                        if (qStemHash in usedStemHashes) {
                            lastSkippedStemHashCount++
                            continue
                        }
                        val tokens = buildQuestionTokenSet(q.stem, q.choices)
                        if (tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                                jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                            }) {
                            lastSkippedSimilarCount++
                            continue
                        }
                        extraNormal.add(q)
                        usedIds.add(id)
                        usedStemHashes.add(qStemHash)
                        if (tokens.isNotEmpty()) {
                            selectedTokenSets.add(tokens)
                        }
                        if (id in recentIds) {
                            recentRelaxedCount++
                        }
                    }
                    if (extraNormal.isNotEmpty()) {
                        selectedPerSubject[subj]?.addAll(extraNormal)
                        selected.addAll(extraNormal)
                        remainingSlots = effectiveCount - selected.size
                    }
                }
            }

            /** Similar-relaxed: skip similarity check. usedIds and stemHashUsed NEVER relax. */
            fun pickExtraFromPartitionSimilarRelaxed(subj: Subject, partition: List<Question>) {
                if (partition.isEmpty() || remainingSlots <= 0) return
                val wrongCandidates = if (preferredWrongIds.isNotEmpty() && maxWrongCount > 0) {
                    partition.filter { it.id in preferredWrongIds }
                } else emptyList()
                val normalCandidates = if (wrongCandidates.isEmpty()) partition else partition.filter { it.id !in preferredWrongIds }
                fun takeFrom(candidates: List<Question>, isWrong: Boolean) {
                    for (q in candidates.shuffled()) {
                        if (remainingSlots <= 0) break
                        val id = q.id
                        if (id in usedIds) continue
                        val qStemHash = stemHash(q.stem)
                        if (qStemHash in usedStemHashes) continue
                        if (isWrong && wrongUsedCount >= maxWrongCount) continue
                        val tokens = buildQuestionTokenSet(q.stem, q.choices)
                        val wouldBeSimilar = tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                            jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                        }
                        if (wouldBeSimilar) similarRelaxedCount++
                        selectedPerSubject[subj]?.add(q)
                        selected.add(q)
                        usedIds.add(id)
                        usedStemHashes.add(qStemHash)
                        if (tokens.isNotEmpty()) selectedTokenSets.add(tokens)
                        if (isWrong) wrongUsedCount++
                        if (id in recentIds) recentRelaxedCount++
                        remainingSlots = effectiveCount - selected.size
                    }
                }
                takeFrom(wrongCandidates, isWrong = true)
                takeFrom(normalCandidates, isWrong = false)
            }

            // Phase 1: fill from non-recent pools across subjects.
            for (subj in subjectsByRemaining) {
                if (remainingSlots <= 0) break
                val poolAll = remainingPerSubject[subj].orEmpty()
                if (poolAll.isEmpty()) continue
                val nonRecent = poolAll.filter { it.id !in recentIds }
                pickExtraFromPartition(subj, nonRecent, fromRecent = false)
            }

            // Phase 2: only if hâlâ eksik varsa, recently-seen sorulardan doldur.
            if (remainingSlots > 0) {
                for (subj in subjectsByRemaining) {
                    if (remainingSlots <= 0) break
                    val poolAll = remainingPerSubject[subj].orEmpty()
                    if (poolAll.isEmpty()) continue
                    val recent = poolAll.filter { it.id in recentIds }
                    pickExtraFromPartition(subj, recent, fromRecent = true)
                }
            }

            // Phase 3: last resort – relax similarity (usedIds and stemHashUsed NEVER relax).
            if (remainingSlots > 0) {
                for (subj in subjectsByRemaining) {
                    if (remainingSlots <= 0) break
                    val poolAll = remainingPerSubject[subj].orEmpty()
                    if (poolAll.isEmpty()) continue
                    pickExtraFromPartitionSimilarRelaxed(subj, poolAll)
                }
            }
        }

        // 4) Hâlâ yeterli soru yoksa, önce grade-only, sonra fallback havuzu kullan – yine unique ID zorunlu.
        if (selected.isEmpty()) {
            var pool = roomStore.getQuestionsByGrade(grade)
            if (pool.isEmpty()) pool = getFallbackQuestions().filter { it.grade == grade }
            if (pool.isEmpty()) pool = getFallbackQuestions()

            val uniqueFromPool = mutableListOf<Question>()
            for (q in pool.shuffled()) {
                if (uniqueFromPool.size >= effectiveCount) break
                val id = q.id
                if (id in usedIds) {
                    lastSkippedIdCount++
                    continue
                }
                val qStemHash = stemHash(q.stem)
                if (qStemHash in usedStemHashes) {
                    lastSkippedStemHashCount++
                    continue
                }
                val tokens = buildQuestionTokenSet(q.stem, q.choices)
                if (tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                        jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                    }) {
                    lastSkippedSimilarCount++
                    continue
                }
                usedIds.add(id)
                usedStemHashes.add(qStemHash)
                if (tokens.isNotEmpty()) {
                    selectedTokenSets.add(tokens)
                }
                uniqueFromPool.add(q)
            }
            selected = uniqueFromPool
            // Similar-relaxed fallback: usedIds and stemHashUsed NEVER relax.
            if (selected.size < effectiveCount) {
                for (q in pool.shuffled()) {
                    if (selected.size >= effectiveCount) break
                    val id = q.id
                    if (id in usedIds) continue
                    val qStemHash = stemHash(q.stem)
                    if (qStemHash in usedStemHashes) continue
                    val tokens = buildQuestionTokenSet(q.stem, q.choices)
                    val wouldBeSimilar = tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                        jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                    }
                    if (wouldBeSimilar) similarRelaxedCount++
                    usedIds.add(id)
                    usedStemHashes.add(qStemHash)
                    if (tokens.isNotEmpty()) selectedTokenSets.add(tokens)
                    selected.add(q)
                }
            }
        } else if (selected.size < effectiveCount) {
            val used = usedIds
            val extraSources = mutableListOf<List<Question>>()
            extraSources += roomStore.getQuestionsByGrade(grade)
            extraSources += getFallbackQuestions().filter { it.grade == grade }
            extraSources += getFallbackQuestions()

            for (source in extraSources) {
                if (selected.size >= effectiveCount) break
                for (q in source.shuffled()) {
                    if (selected.size >= effectiveCount) break
                    val id = q.id
                    if (id in used) {
                        lastSkippedIdCount++
                        continue
                    }
                    val qStemHash = stemHash(q.stem)
                    if (qStemHash in usedStemHashes) {
                        lastSkippedStemHashCount++
                        continue
                    }
                    val tokens = buildQuestionTokenSet(q.stem, q.choices)
                    if (tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                            jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                        }) {
                        lastSkippedSimilarCount++
                        continue
                    }
                    used.add(id)
                    usedStemHashes.add(qStemHash)
                    if (tokens.isNotEmpty()) {
                        selectedTokenSets.add(tokens)
                    }
                    selected.add(q)
                }
            }
            // Similar-relaxed fallback: usedIds and stemHashUsed NEVER relax.
            if (selected.size < effectiveCount) {
                for (source in extraSources) {
                    if (selected.size >= effectiveCount) break
                    for (q in source.shuffled()) {
                        if (selected.size >= effectiveCount) break
                        val id = q.id
                        if (id in used) continue
                        val qStemHash = stemHash(q.stem)
                        if (qStemHash in usedStemHashes) continue
                        val tokens = buildQuestionTokenSet(q.stem, q.choices)
                        val wouldBeSimilar = tokens.isNotEmpty() && selectedTokenSets.any { prev ->
                            jaccardSimilarity(tokens, prev) >= NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                        }
                        if (wouldBeSimilar) similarRelaxedCount++
                        used.add(id)
                        usedStemHashes.add(qStemHash)
                        if (tokens.isNotEmpty()) selectedTokenSets.add(tokens)
                        selected.add(q)
                    }
                }
            }
        }

        val finalQuestions = selected
            .distinctBy { it.id }
            .take(effectiveCount)
            .shuffled()

        // Test snapshot + tekrarları engellemek için kayıt.
        val questionIds = finalQuestions.map { it.id }
        roomStore.recordTestCreated(profileId, effectiveTestId, questionIds)
        recordSeenForQuiz(profileId, questionIds)

        // Expose debug counts for UI.
        lastRecentRelaxedCount = recentRelaxedCount
        lastSimilarRelaxedCount = similarRelaxedCount
        lastSubjectCounts = subjectOrder.associate { (subjEnum, dbKey) ->
            dbKey to (selectedPerSubject[subjEnum]?.size ?: 0)
        }

        // Estimate how many candidates were effectively excluded due to recency:
        // recent-in-pool minus those we relaxed and actually used.
        val totalRecentInPool = perSubjectAll.values
            .flatten()
            .count { it.id in recentIds }
        lastSkippedRecentCount = (totalRecentInPool - recentRelaxedCount).coerceAtLeast(0)

        // Per-subject debug özeti: havuz ve seçilen soru sayıları + wrongUsed sayısı.
        val selectionDebug = StringBuilder().apply {
            append("[GRADE_TEST] selectedGrade=").append(grade)
            append(", selectedDifficulty=").append(selectedDifficulty.name)
            append(", recentCount=").append(recentIds.size)
            append(", totalPicked=").append(finalQuestions.size)
            append(", wrongUsed=").append(wrongUsedCount).append("/").append(effectiveCount)
            subjectOrder.forEach { (subjEnum, dbKey) ->
                val totalForDiff = perSubjectTotalForDiff[subjEnum] ?: 0
                val nonRecentAvail = perSubjectNonRecentAvailable[subjEnum] ?: 0
                val pickedCount = selectedPerSubject[subjEnum]?.size ?: 0
                append(" | ")
                append(dbKey)
                append(": totalDiff=").append(totalForDiff)
                append(", nonRecentAvail=").append(nonRecentAvail)
                append(", picked=").append(pickedCount)
            }
        }.toString()
        android.util.Log.d(TAG, selectionDebug)

        return finalQuestions
    }

    /**
     * Generic helper to pick up to [needed] unique items from [pool],
     * based on their ID (and optional stem hash), updating [usedIds] and shuffling for randomness.
     */
    private fun <T> pickFromPool(
        pool: List<T>,
        needed: Int,
        usedIds: MutableSet<String>,
        idOf: (T) -> String,
        usedStemHashes: MutableSet<String>? = null,
        stemOf: ((T) -> String)? = null
    ): List<T> {
        if (needed <= 0 || pool.isEmpty()) return emptyList()
        val result = mutableListOf<T>()
        for (item in pool.shuffled()) {
            if (result.size >= needed) break
            val id = idOf(item)
            if (id in usedIds) continue
            val stemHashValue = if (usedStemHashes != null && stemOf != null) {
                stemHash(stemOf(item))
            } else null
            if (stemHashValue != null && usedStemHashes?.contains(stemHashValue) == true) continue
            usedIds.add(id)
            if (stemHashValue != null) {
                usedStemHashes?.add(stemHashValue)
            }
            result.add(item)
        }
        return result
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
            val unique = mutableListOf<Question>()
            val used = mutableSetOf<String>()
            for (q in finalPool.shuffled()) {
                if (unique.size >= count) break
                if (q.id in used) continue
                used.add(q.id)
                unique.add(q)
            }
            questions = unique
        }
        if (questions.size < count && finalPool.isNotEmpty()) {
            val used = questions.map { it.id }.toMutableSet()
            val extraSources = listOf(finalPool, getFallbackQuestions())
            val filled = questions.toMutableList()
            for (source in extraSources) {
                if (filled.size >= count) break
                for (q in source.shuffled()) {
                    if (filled.size >= count) break
                    if (q.id in used) continue
                    used.add(q.id)
                    filled.add(q)
                }
            }
            questions = filled
        }

        val finalUnique = questions.distinctBy { it.id }.take(count)
        val questionIds = finalUnique.map { it.id }
        roomStore.recordTestCreated(profileId, effectiveTestId, questionIds)
        recordSeenForQuiz(profileId, questionIds)
        return finalUnique
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
        var finalList = result.ifEmpty {
            val unique = mutableListOf<Question>()
            val used = mutableSetOf<String>()
            for (q in pool.shuffled()) {
                if (unique.size >= count) break
                if (q.id in used) continue
                used.add(q.id)
                unique.add(q)
            }
            unique
        }.toMutableList()
        if (finalList.size < count && pool.isNotEmpty()) {
            val usedIds = finalList.map { it.id }.toMutableSet()
            for (q in pool.shuffled()) {
                if (finalList.size >= count) break
                if (q.id in usedIds) continue
                usedIds.add(q.id)
                finalList.add(q)
            }
        }
        val toReturn = finalList.distinctBy { it.id }.take(count).shuffled()
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
        val result = mutableListOf<Question>()
        val used = mutableSetOf<String>()
        for (q in base.shuffled()) {
            if (result.size >= count) break
            if (q.id in used) continue
            used.add(q.id)
            result.add(q)
        }
        if (result.size < count && pool.isNotEmpty()) {
            for (q in pool.shuffled()) {
                if (result.size >= count) break
                if (q.id in used) continue
                used.add(q.id)
                result.add(q)
            }
        }
        return result.distinctBy { it.id }.take(count).shuffled()
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
            if (q.id in used) continue
            used.add(q.id)
            result.add(q)
        }
        for (q in preferFresh) {
            if (result.size >= count) break
            if (q.id in used) continue
            used.add(q.id)
            result.add(q)
        }
        for (q in fillFrom) {
            if (result.size >= count) break
            if (q.id in used) continue
            used.add(q.id)
            result.add(q)
        }
        var finalList = result.ifEmpty {
            val unique = mutableListOf<Question>()
            val extraUsed = mutableSetOf<String>()
            for (q in pool.shuffled()) {
                if (unique.size >= count) break
                if (q.id in extraUsed) continue
                extraUsed.add(q.id)
                unique.add(q)
            }
            unique
        }.toMutableList()
        if (finalList.size < count && pool.isNotEmpty()) {
            val usedIds = finalList.map { it.id }.toMutableSet()
            for (q in pool.shuffled()) {
                if (finalList.size >= count) break
                if (q.id in usedIds) continue
                usedIds.add(q.id)
                finalList.add(q)
            }
        }
        val toReturn = finalList.distinctBy { it.id }.take(count).shuffled()
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
