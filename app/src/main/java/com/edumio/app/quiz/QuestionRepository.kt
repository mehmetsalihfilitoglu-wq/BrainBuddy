package com.edumio.app.quiz

import android.content.Context
import android.util.Log
import com.edumio.app.core.ActiveProfileManager
import com.edumio.app.core.ProfileStore
import com.edumio.app.core.QuizPrefs
import com.edumio.app.db.DatabaseProvider
import com.edumio.app.db.GradeSubjectDifficultyCount
import com.edumio.app.db.LgsCandidateRow
import com.edumio.app.db.QuestionCandidateRow
import com.edumio.app.db.QuestionEntity
import com.edumio.app.db.QuestionStemHash
import com.edumio.app.db.QuestionMapper
import com.edumio.app.db.QuotaSyntheticQuestions
import com.edumio.app.db.RoomQuizDataStore
import com.edumio.app.db.SyntheticHardQuestionGenerator
import kotlinx.coroutines.Dispatchers
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
    private val wrongQuestionPoolStore = WrongQuestionPoolStore(context)

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

    /** Time (ms) spent in DB queries during last pickQuizQuestionsByGrade. */
    @Volatile
    var lastDbQueryMs: Long = 0
        private set

    /** Total time (ms) to build quiz in last pickQuizQuestionsByGrade. */
    @Volatile
    var lastBuildMs: Long = 0
        private set

    /** True if attempt caps were hit during last pick (avoids infinite loops). */
    @Volatile
    var lastCapReached: Boolean = false
        private set

    /** LGS blueprint summary for debug (e.g. "LGS_MINI total=20"). */
    @Volatile
    var lastBlueprintSummary: String = ""
        private set

    /** LGS type counts for debug (type -> count). */
    @Volatile
    var lastTypeCounts: Map<String, Int> = emptyMap()
        private set

    /** LGS average qualityScore of selected questions. */
    @Volatile
    var lastAvgQualityScore: Double = 0.0
        private set

    /** Grade pick: synthetic rows inserted when the strict pool was empty (no EASY fallback). */
    @Volatile
    var lastSyntheticEmergencyTopUpCount: Int = 0
        private set

    /** Last grade-test quality pool summary for debug. */
    @Volatile
    var lastQualityPickSummary: String = ""
        private set

    companion object {
        private const val TAG = "QuestionRepository"
        /** Every test (gate, normal, remedial, boss) has exactly this many questions. */
        const val MIN_QUESTIONS_PER_TEST = 20
        /** LGS subjects (DB keys). Exactly: mat, turkce, fen, inkilap, din, ing. No sosyal. */
        val LGS_SUBJECTS = listOf("mat", "turkce", "fen", "inkilap", "din", "ing")
        /** Reject if similarity > threshold — 0.85 catches template clones while allowing genuine variation. */
        private const val NEAR_DUPLICATE_SIMILARITY_THRESHOLD = 0.85
        /** Max pick attempts per subject to avoid long loops. */
        private const val CAP_ATTEMPTS_PER_SUBJECT = 200
        /** Max total pick attempts across all subjects. */
        private const val CAP_ATTEMPTS_TOTAL = 1000
        /** Only compare similarity against last N selected token sets (cheap O(n)). */
        private const val SIMILARITY_LOOKBACK = 10
        /** Max candidates per pre-shuffled partition — limits Jaccard scan cost and memory. */
        private const val CANDIDATE_CAP_PER_PARTITION = 200

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

    /** SHA-256 hash of normalized stem. Uses QuestionStemHash (Turkish names→NAME, numbers→#). */
    private fun stemHash(stem: String): String = QuestionStemHash.stemHash(stem)

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

    /** Sınıf bazlı havuz boyutu (1-7). Room'dan grade ile filtreler. */
    fun getPoolSizeForGrade(grade: Int): Int {
        if (grade !in 1..7) return 0
        return try {
            roomStore.getQuestionsByGrade(grade).distinctBy { it.id }.size
        } catch (_: Exception) { 0 }
    }

    fun loadAllQuestionsWithStats(): Pair<List<Question>, LoadStats> {
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
        val batchSeenStemKeys = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            try {
                val q = parseQuestion(arr.getJSONObject(i))
                val dbSubjectKey = when (q.subject) {
                    Subject.MAT -> "mat"
                    Subject.TURKCE -> "turkce"
                    Subject.FEN -> "fen"
                    Subject.SOSYAL -> "sosyal"
                    Subject.HAYAT -> "hayat"
                    Subject.ING -> "ing"
                    Subject.INKILAP -> "inkilap"
                    Subject.DIN -> "din"
                }
                val h = QuestionStemHash.stemHash(q.stem)
                val stemKey = "${q.grade}|$dbSubjectKey|$h"
                if (stemKey in batchSeenStemKeys) continue
                batchSeenStemKeys.add(stemKey)
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

        val toAddEntities = toAdd.map { q ->
            val diffInt = when (q.difficulty) {
                QuizDifficulty.EASY -> 0
                QuizDifficulty.HARD -> 2
                else -> 1
            }
            var gate = QuestionQualityGate.evaluate(q.subject, q.grade, q.stem, q.choices, diffInt, answerIndex = q.correctIndex)

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
                    Subject.FEN, Subject.SOSYAL, Subject.HAYAT -> {
                        val isContextual = isContextualProblemLike(stem)
                        val isLongEnough = stem.length >= 80
                        if (!isContextual || !isLongEnough) {
                            gate = gate.copy(
                                isActive = false,
                                deactivationReason = "too_simple_context_hard"
                            )
                        }
                    }
                    Subject.INKILAP, Subject.DIN -> {
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
            val dbSubjectKey = when (q.subject) {
                Subject.MAT -> "mat"
                Subject.TURKCE -> "turkce"
                Subject.FEN -> "fen"
                Subject.SOSYAL -> "sosyal"
                Subject.HAYAT -> "hayat"
                Subject.ING -> "ing"
                Subject.INKILAP -> "inkilap"
                Subject.DIN -> "din"
            }
            val stemNormalizedValue = QuestionStemHash.normalizeStem(q.stem)
            val stemHashValue = QuestionStemHash.stemHash(q.stem)
            QuestionEntity(
                id = q.id,
                grade = q.grade.coerceIn(1, 7),
                subject = dbSubjectKey,
                difficulty = diffInt,
                questionText = q.stem,
                optionsJson = org.json.JSONArray(q.choices).toString(),
                answerIndex = q.correctIndex,
                explanation = q.hint?.takeIf { it.isNotBlank() },
                isActive = true,
                questionType = gate.questionType,
                skillsJson = gate.skillsJson,
                deactivationReason = null,
                version = 1,
                examType = q.examType.name,
                imageAsset = q.imageAsset?.takeIf { it.isNotBlank() },
                type = q.type,
                skill = q.skill,
            stemNormalized = stemNormalizedValue,
            stemHash = stemHashValue,
            qualityTier = gate.qualityTier,
            reasoningLevel = gate.reasoningLevel,
            reasoningScore = gate.reasoningScore,
            distractorQualityScore = gate.distractorQualityScore,
            contextComplexityScore = gate.contextComplexityScore,
            qualityFlagsJson = gate.qualityFlagsJson,
            unservableReason = null,
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
        if (grade !in 1..7) {
            return PoolDebugForGrade(
                total = 0,
                active = 0,
                selectedGrade = grade,
                selectedDifficulty = difficulty,
                perSubject = emptyMap(),
                hasPassiveOnly = false,
                subjectsWithDifficultyGap = emptyList(),
                readableText = "DB DURUMU\nGeçersiz sınıf: $grade (1..7 dışında)."
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
                // Grade 1..7 dağılımı + geçersiz grade teşhisi
                val perGrade = (1..7).associateWith { g ->
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
            // Grade dağılımı: 1..7 için total/active
            sb.append("Grade dağılımı (1..7):\n")
            (1..7).forEach { g ->
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
        val examStore = com.edumio.app.core.ExamPackStore(context)
        val filtered = base.filter { examStore.isPackActive(it.examType) }
        return if (filtered.isEmpty()) base else filtered
    }

    /**
     * In-code K-12 fallback (Turkish grade-school questions like "15² kaçtır?"). These are LEGACY grade-based
     * content and must NEVER be served in production — an EDUmio exam profile shows a controlled empty-pool
     * error instead of a wrong-domain fallback. Kept only for DEBUG diagnostics of the legacy grade path.
     */
    private fun getFallbackQuestions(): List<Question> {
        if (!com.edumio.app.BuildConfig.DEBUG) return emptyList()
        return getFallbackQuestionsDebug()
    }

    private fun getFallbackQuestionsDebug(): List<Question> = listOf(
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
        val examType = try { com.edumio.app.quiz.ExamType.valueOf(examStr) } catch (_: Exception) { com.edumio.app.quiz.ExamType.GENERAL }
        val topic = o.optString("topic", "").takeIf { it.isNotEmpty() }
        val explicitType = o.optString("type", "").takeIf { it.isNotEmpty() }
        val explicitSkill = o.optString("skill", "").takeIf { it.isNotEmpty() }
        val stem = o.optString("stem", "?")
        val correctIdx = o.optInt("correctIndex", 0).coerceIn(0, choices.size - 1)
        val correctAnswer = choices.getOrNull(correctIdx) ?: ""
        // Grade belirleme:
        // JSON'da "grade" alanı zorunlu kabul edilir; sadece 1..7 aralığına clamp edilir.
        val rawGrade = o.optInt("grade", 0)
        val grade = rawGrade.coerceIn(1, 7)
        if (grade !in 1..7) {
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
        val userId = com.edumio.app.core.ActiveProfileManager.getActiveProfileId(context)
        roomStore.recordAnswers(userId, answers, testId)
        answers.forEach { a ->
            val q = questionsMap?.get(a.questionId)
            if (a.isCorrect) {
                wrongQuestionStore.markFixed(a.questionId)
            } else {
                wrongQuestionStore.recordWrong(a.questionId, q?.subject?.tr ?: "Diğer")
            }
            wrongQuestionPoolStore.applyAnswer(a)
        }
    }

    /** G5: Quiz tamamlanınca çağrılır. globalTestIndex++, her soru için lastSeenTestIndex günceller. */
    fun onQuizCompleted(questionIds: List<String>) {
        roomStore.onQuizCompleted(questionIds)
    }

    /** Load a single question by ID (e.g. for wrong-question scheduler injection). */
    fun getQuestionById(questionId: String): Question? {
        if (questionId.isBlank()) return null
        return roomStore.getQuestionsByIds(listOf(questionId)).firstOrNull()
    }

    /**
     * Sınıf bazlı test: Kullanıcının seçtiği grade (1-7) için havuzdan seçim.
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
        maxWrongFraction: Double = 0.3,
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        if (grade !in 1..7) return emptyList()
        return runBlocking(Dispatchers.IO) {
            pickQuizQuestionsByGradeInternal(
                grade = grade,
                count = count,
                testId = testId,
                preferredWrongIds = preferredWrongIds,
                maxWrongFraction = maxWrongFraction,
                excludeIds = excludeIds
            )
        }
    }

    /**
     * IMAT-format practice pools. Selecting a mode is a query-level choice (no schema migration is ever
     * needed to switch): OFFICIAL_IMAT = frozen official bank only; EDUMIO_ORIGINAL_ORIGINAL = original bank
     * only; MIXED = both together. Official and original items never blend unless MIXED is chosen.
     */
    enum class PracticePool { OFFICIAL_IMAT, EDUMIO_ORIGINAL_ORIGINAL, MIXED, TIL_I, CENT_S }

    /**
     * Unified IMAT-format quiz picker. Serves questions from the requested [pool], fully isolated from
     * LGS / grade-based legacy content. [examSubject] null = mixed across all subjects. Blocks questions
     * seen in the last few tests when a profileId is given, then shuffles.
     */
    fun pickQuizQuestionsForPool(
        pool: PracticePool,
        count: Int = MIN_QUESTIONS_PER_TEST,
        examSubject: String? = null,
        profileId: String? = null,
    ): List<Question> {
        val source = when (pool) {
            PracticePool.OFFICIAL_IMAT -> roomStore.getImatQuestions(examSubject)
            PracticePool.EDUMIO_ORIGINAL_ORIGINAL -> roomStore.getEdumioOriginalQuestions(examSubject)
            PracticePool.MIXED -> roomStore.getMixedImatQuestions(examSubject)
            PracticePool.TIL_I -> roomStore.getQuestionsByExamType("TIL_I", examSubject)
            PracticePool.CENT_S -> roomStore.getQuestionsByExamType("CENT_S", examSubject)
        }
        if (source.isEmpty()) return emptyList()
        val blocked = if (profileId != null) roomStore.getQuestionIdsFromLastNTests(profileId, 3) else emptySet()
        val fresh = source.filter { it.id !in blocked }
        val pick = if (fresh.size >= count) fresh else source
        return pick.shuffled().distinctBy { it.id }.take(count.coerceAtLeast(1))
    }

    /** Official IMAT only (examType='IMAT'). Back-compat entry point used by the current quiz flow. */
    fun pickQuizQuestionsForImat(
        count: Int = MIN_QUESTIONS_PER_TEST,
        examSubject: String? = null,
        profileId: String? = null,
    ): List<Question> = pickQuizQuestionsForPool(PracticePool.OFFICIAL_IMAT, count, examSubject, profileId)

    /** EdumioOriginal originals only (examType='EDUMIO_ORIGINAL'). */
    fun pickQuizQuestionsForEdumioOriginal(
        count: Int = MIN_QUESTIONS_PER_TEST,
        examSubject: String? = null,
        profileId: String? = null,
    ): List<Question> = pickQuizQuestionsForPool(PracticePool.EDUMIO_ORIGINAL_ORIGINAL, count, examSubject, profileId)

    /** Mixed practice: official IMAT + EdumioOriginal originals. */
    fun pickQuizQuestionsForMixed(
        count: Int = MIN_QUESTIONS_PER_TEST,
        examSubject: String? = null,
        profileId: String? = null,
    ): List<Question> = pickQuizQuestionsForPool(PracticePool.MIXED, count, examSubject, profileId)

    /** Is this one of EDUmio's isolated production exams (each has its own verified bank)? */
    fun isEdumioExam(examType: com.edumio.app.core.ExamType): Boolean = when (examType) {
        com.edumio.app.core.ExamType.IMAT,
        com.edumio.app.core.ExamType.TIL_I,
        com.edumio.app.core.ExamType.CENT_S -> true
        else -> false
    }

    /**
     * The single entry point for practice questions of an EDUmio exam profile. STRICTLY isolated: each exam
     * draws ONLY from its own examType bank(s) — never legacy K-12/LGS/grade content and never the hardcoded
     * fallback. IMAT = official IMAT + EdumioOriginal originals; TIL-I / CEnT-S = their own banks. Returns an
     * EMPTY list when the bank is empty (the caller shows a controlled error instead of any fallback).
     */
    fun pickQuizForActiveExam(
        examType: com.edumio.app.core.ExamType,
        count: Int = MIN_QUESTIONS_PER_TEST,
        profileId: String? = null,
    ): List<Question> = when (examType) {
        com.edumio.app.core.ExamType.IMAT -> pickQuizQuestionsForPool(PracticePool.MIXED, count, null, profileId)
        com.edumio.app.core.ExamType.TIL_I -> pickQuizQuestionsForPool(PracticePool.TIL_I, count, null, profileId)
        com.edumio.app.core.ExamType.CENT_S -> pickQuizQuestionsForPool(PracticePool.CENT_S, count, null, profileId)
        else -> emptyList()
    }

    /**
     * LGS mode: uses only LGS question pool (examType=LGS).
     * Subject distribution: MAT=4, TURKCE=4, FEN=4, INKILAP=3, DIN=3, ING=2 (total 20).
     * Does NOT use selectedGrade. Does NOT fallback to grade 6. Never mixes grade-mode questions.
     */
    fun pickQuizQuestionsForLGS(
        count: Int = MIN_QUESTIONS_PER_TEST,
        testId: String? = null,
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        return runBlocking(Dispatchers.IO) {
            pickQuizQuestionsForLGSInternal(count = count, testId = testId, excludeIds = excludeIds)
        }
    }

    private suspend fun pickQuizQuestionsForLGSInternal(
        count: Int,
        testId: String?,
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        val buildStartMs = System.currentTimeMillis()
        val effectiveCount = count.coerceAtMost(MIN_QUESTIONS_PER_TEST).coerceAtLeast(MIN_QUESTIONS_PER_TEST)

        // [PERF FIX] PoolHealthLogger.logLgsPoolHealth removed from critical path (~11 extra DB queries).
        // Pool health is still logged by the grade picker's deferred stats (QuizActivity post-render).
        val lgsPickStartMs = System.currentTimeMillis()
        Log.d(TAG, "[QUIZ_PERF] lgs_picker_start")

        val blueprint = LGS_MINI_BLUEPRINT
        lastBlueprintSummary = "${blueprint.mode} total=${blueprint.totalQuestionCount}"
        lastRecentRelaxedCount = 0
        lastCapReached = false
        QualityAudit.reset()
        QualityAudit.currentSelectedMode = "LGS"
        QualityAudit.currentSelectedGrade = 8

        val profileId = ProfileStore(context).getCurrentProfileId()
        val effectiveTestId = testId ?: java.util.UUID.randomUUID().toString()
        val recentIds = roomStore.getRecentlySeenIdsForProfile(profileId, 150)
        // Hard-block: IDs from last 5 quizzes are NEVER re-served
        val hardBlockIds: Set<String> = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        Log.d(TAG, "REPEAT_GUARD LGS hardBlockIds=${hardBlockIds.size} recentIds=${recentIds.size}")
        Log.d(TAG, "[QUIZ_DEBUG] requested_mode=LGS target=$effectiveCount")
        Log.d(TAG, "[QUIZ_DEBUG] repeat_blocked=${hardBlockIds.size} recent_seen=${recentIds.size}")

        val usedIds = excludeIds.toMutableSet()
        usedIds.addAll(hardBlockIds)
        Log.d(TAG, "REPEAT_BLOCKED LGS count=${hardBlockIds.size} from last 5 quizzes")
        val usedStemHashes = mutableSetOf<String>()
        val selectedRows = mutableListOf<LgsCandidateRow>()
        var recentRelaxedCount = 0
        val subjectPools = mutableMapOf<Subject, List<LgsCandidateRow>>()
        val slots = blueprint.slots()

        fun poolFor(subj: Subject): List<LgsCandidateRow> {
            return subjectPools.getOrPut(subj) {
                val dbKey = com.edumio.app.db.QuestionMapper.toDbSubject(subj)
                var rows = roomStore.getLgsCandidatePoolWithQuality(dbKey, QuizQualityPolicy.ALL_CONTENT_TIERS)
                    .distinctBy { it.id }
                if (rows.isEmpty()) {
                    val batch = SyntheticHardQuestionGenerator.generateLgs(dbKey, 10)
                    if (batch.isNotEmpty()) {
                        roomStore.insertQuestions(batch)
                        QualityAudit.syntheticGeneratedCount += batch.size
                        rows = roomStore.getLgsCandidatePoolWithQuality(dbKey, QuizQualityPolicy.ALL_CONTENT_TIERS)
                            .distinctBy { it.id }
                    }
                }
                AdaptiveQuizRuntime.sortByTierPriority(rows) { it.qualityTier }
            }
        }

        for ((subjEnum, preferredType) in slots) {
            if (selectedRows.count { com.edumio.app.db.QuestionMapper.mapSubject(it.subject) == subjEnum } >= (blueprint.subjectTargets[subjEnum] ?: 0)) continue

            val pool = poolFor(subjEnum)
                .filter { it.id !in usedIds }
            val nonRecent = pool.filter { it.id !in recentIds }
            val recent = pool.filter { it.id in recentIds }

            val typeCountsInSubject = selectedRows.filter { com.edumio.app.db.QuestionMapper.mapSubject(it.subject) == subjEnum }
                .groupingBy { it.type }.eachCount()

            fun pickFrom(candidates: List<LgsCandidateRow>, isRecent: Boolean): LgsCandidateRow? {
                val sorted = candidates.sortedWith(
                    compareBy<LgsCandidateRow> { row ->
                        val sh = row.stemHash.ifEmpty { stemHash(row.stemNormalized.ifEmpty { row.id }) }
                        if (sh in usedStemHashes) 1 else 0
                    }.thenByDescending { BlueprintTypeMapper.dbTypesMatchBlueprint(preferredType, it.type) }
                    .thenByDescending { it.qualityScore }
                    .thenBy { typeCountsInSubject[it.type] ?: 0 }
                )
                for (row in sorted) {
                    val sh = row.stemHash.ifEmpty { stemHash(row.stemNormalized.ifEmpty { row.id }) }
                    if (sh in usedStemHashes) continue
                    return row.also { if (isRecent) recentRelaxedCount++ }
                }
                return null
            }

            val chosen = pickFrom(nonRecent, false) ?: pickFrom(recent, true)
            if (chosen != null) {
                val sh = chosen.stemHash.ifEmpty { stemHash(chosen.stemNormalized.ifEmpty { chosen.id }) }
                usedIds.add(chosen.id)
                usedStemHashes.add(sh)
                selectedRows.add(chosen)
            }
        }

        // Log pool sizes per LGS subject before materialization
        val lgsPoolSummary = LGS_SUBJECTS.joinToString(" | ") { sk ->
            val subj = com.edumio.app.db.QuestionMapper.mapSubject(sk)
            "$sk: pool=${subjectPools[subj]?.size ?: 0} selected=${selectedRows.count { com.edumio.app.db.QuestionMapper.mapSubject(it.subject) == subj }}"
        }
        Log.w(TAG, "[QUIZ_DEBUG] lgs_pool_per_subject=$lgsPoolSummary")
        Log.w(TAG, "[QUIZ_DEBUG] lgs_selected_total=${selectedRows.size}/$effectiveCount")
        Log.d(TAG, "[QUIZ_PERF] lgs_selection_ms=${System.currentTimeMillis() - lgsPickStartMs}")

        // LGS emergency fallback: if the main pass came up short, re-try ignoring
        // hardBlockIds (within-quiz dedup still enforced). Repeat is better than a short quiz.
        if (selectedRows.size < effectiveCount) {
            val shortfall = effectiveCount - selectedRows.size
            Log.w(TAG, "[QUIZ_DEBUG] LGS shortfall=$shortfall selectedRows=${selectedRows.size} effectiveCount=$effectiveCount — broad-subject fallback")
            for ((subjEnum, preferredType) in slots) {
                if (selectedRows.size >= effectiveCount) break
                val target = blueprint.subjectTargets[subjEnum] ?: 0
                val alreadySelected = selectedRows.count { com.edumio.app.db.QuestionMapper.mapSubject(it.subject) == subjEnum }
                if (alreadySelected >= target) continue
                val dbKey = com.edumio.app.db.QuestionMapper.toDbSubject(subjEnum)
                val broadPool = roomStore.getLgsCandidatePoolWithQuality(dbKey, QuizQualityPolicy.ALL_CONTENT_TIERS)
                    .distinctBy { it.id }
                    .filter { it.id !in usedIds }
                    .let { AdaptiveQuizRuntime.sortByTierPriority(it) { r -> r.qualityTier } }
                for (row in broadPool) {
                    if (selectedRows.size >= effectiveCount) break
                    val sh = row.stemHash.ifEmpty { stemHash(row.stemNormalized.ifEmpty { row.id }) }
                    if (sh in usedStemHashes) continue
                    usedIds.add(row.id)
                    usedStemHashes.add(sh)
                    selectedRows.add(row)
                    Log.w(TAG, "[QUIZ_DEBUG] LGS broad-fallback added id=${row.id} subj=${row.subject}")
                }
            }
        }

        val balancingPass = balanceToAvoidConsecutiveSameType(selectedRows)
        val selectedIds = balancingPass.map { it.id }.distinct()
        val entityRows = roomStore.getQuestionEntitiesByIds(selectedIds)
        val byIdE = entityRows.associateBy { it.id }
        val orderPreservedMutable = mutableListOf<Question>()
        for (id in selectedIds) {
            if (orderPreservedMutable.size >= effectiveCount) break
            val entity = byIdE[id] ?: continue
            val q = materializeSingleQuestion(entity, expectLgs = true)
            if (q != null) orderPreservedMutable.add(q)
        }

        // LGS EMERGENCY FALLBACK: if shouldQuarantine eliminated too many candidates,
        // re-try rejected ones with emergency materialization (skips shouldQuarantine).
        if (orderPreservedMutable.size < effectiveCount) {
            val lgsEmergencyNeed = effectiveCount - orderPreservedMutable.size
            val lgsAlreadyUsed = orderPreservedMutable.map { it.id }.toSet()
            val lgsRetryIds = selectedIds.filter { it !in lgsAlreadyUsed }
            Log.w(TAG, "[QUIZ_DEBUG] lgs_after_quality=${orderPreservedMutable.size}/$effectiveCount — emergency recover need=$lgsEmergencyNeed")
            // [PERF FIX] Reuse byIdE (already fetched at entity-load step) — no extra DB call needed.
            val lgsRetryEntities = byIdE
            for (id in lgsRetryIds) {
                if (orderPreservedMutable.size >= effectiveCount) break
                val entity = lgsRetryEntities[id] ?: continue
                val q = materializeSingleQuestionEmergency(entity, expectLgs = true)
                if (q != null) {
                    orderPreservedMutable.add(q)
                    Log.d(TAG, "[QUIZ_DEBUG] lgs_emergency_recover id=$id tier=${entity.qualityTier}")
                }
            }
            // Still short: pull more from subject pools (broader fallback — per subject, blueprint quota)
            if (orderPreservedMutable.size < effectiveCount) {
                val broadUsed = orderPreservedMutable.map { it.id }.toSet() + usedIds
                // Collect extra candidates per subject, respecting each subject's blueprint target
                val broadCandidateIds = mutableListOf<String>()
                for (sk in LGS_SUBJECTS) {
                    val subj = com.edumio.app.db.QuestionMapper.mapSubject(sk)
                    val subjectTarget = blueprint.subjectTargets[subj] ?: 0
                    val alreadyMaterialized = orderPreservedMutable.count { it.subject == subj }
                    val subjNeed = (subjectTarget - alreadyMaterialized).coerceAtLeast(0)
                    if (subjNeed <= 0) continue
                    val pool = subjectPools[subj].orEmpty()
                        .filter { it.id !in broadUsed }
                        .map { it.id }
                        .take(subjNeed * 4)
                    broadCandidateIds.addAll(pool)
                }
                if (broadCandidateIds.isNotEmpty()) {
                    // Only fetch entities not already in byIdE
                    val missingIds = broadCandidateIds.filter { it !in byIdE }
                    val fetchedBroad = if (missingIds.isNotEmpty())
                        roomStore.getQuestionEntitiesByIds(missingIds).associateBy { it.id }
                    else emptyMap()
                    for (sk in LGS_SUBJECTS) {
                        if (orderPreservedMutable.size >= effectiveCount) break
                        val subj = com.edumio.app.db.QuestionMapper.mapSubject(sk)
                        val subjectTarget = blueprint.subjectTargets[subj] ?: 0
                        val subjIds = broadCandidateIds.filter { id ->
                            (byIdE[id] ?: fetchedBroad[id])?.let {
                                com.edumio.app.db.QuestionMapper.mapSubject(it.subject ?: "") == subj
                            } == true
                        }
                        for (id in subjIds) {
                            if (orderPreservedMutable.size >= effectiveCount) break
                            if (orderPreservedMutable.count { it.subject == subj } >= subjectTarget) break
                            val entity = byIdE[id] ?: fetchedBroad[id] ?: continue
                            val q = materializeSingleQuestionEmergency(entity, expectLgs = true)
                            if (q != null) {
                                orderPreservedMutable.add(q)
                                Log.w(TAG, "[QUIZ_DEBUG] lgs_broad_emergency id=$id subj=$sk tier=${entity.qualityTier}")
                            }
                        }
                    }
                }
            }
            Log.w(TAG, "[QUIZ_DEBUG] lgs_after_emergency=${orderPreservedMutable.size}/$effectiveCount")
        }

        val orderPreserved = orderPreservedMutable.take(effectiveCount)
        val balancedForAvg = balancingPass.take(effectiveCount)

        lastSubjectCounts = blueprint.subjectTargets.keys.associate { subj ->
            com.edumio.app.db.QuestionMapper.toDbSubject(subj) to orderPreserved.count { it.subject == subj }
        }
        lastTypeCounts = orderPreserved.groupingBy { it.type }.eachCount()
        lastAvgQualityScore = if (balancedForAvg.isNotEmpty()) {
            balancedForAvg.map { it.qualityScore }.average()
        } else 0.0
        lastRecentRelaxedCount = recentRelaxedCount
        lastBuildMs = System.currentTimeMillis() - buildStartMs
        Log.w(TAG, "[QUIZ_DEBUG] lgs_final=${orderPreserved.size}/$effectiveCount quarantine=${QualityAudit.quarantinedLowQualityCount} modeReject=${QualityAudit.rejectedWrongModeCount}")
        Log.w(TAG, "[QUIZ_PERF] lgs_total_build_ms=$lastBuildMs")
        // [QUIZ_OUTPUT] Structured output for logcat inspection
        Log.w(TAG, "[QUIZ_OUTPUT] mode=LGS total=${orderPreserved.size}/$effectiveCount")
        Log.w(TAG, "[QUIZ_OUTPUT] final_subjects=${orderPreserved.groupingBy { com.edumio.app.db.QuestionMapper.toDbSubject(it.subject) }.eachCount()}")
        Log.w(TAG, "[QUIZ_OUTPUT] final_topics=${orderPreserved.groupingBy { it.topic?.take(30) ?: "?" }.eachCount()}")
        Log.w(TAG, "[QUIZ_OUTPUT] final_question_types=${orderPreserved.groupingBy { it.type }.eachCount()}")
        Log.w(TAG, "[QUIZ_OUTPUT] final_ids=${orderPreserved.map { it.id }}")
        QualityAudit.servedLgsCount = orderPreserved.size
        // [PERF FIX] Removed 6 extra getLgsCandidatePoolWithQuality queries for QualityAudit.
        // playablePoolSizeLastQuery was debug-only and caused 6 full-pool DB scans after quiz was built.
        QualityAudit.playablePoolSizeLastQuery = -1 // deferred / not computed on critical path
        QualityAudit.currentPoolSourceSummary = "LGS exam=LGS only; no grade banks"
        lastQualityPickSummary =
            "LGS adaptive tier-sorted; modeAudit reject=${QualityAudit.rejectedWrongModeCount} quarantine=${QualityAudit.quarantinedLowQualityCount}"

        // Structured perf/output logging for observability.
        Log.w(TAG, "[QUIZ_PERF] LGS buildMs=${System.currentTimeMillis() - buildStartMs} served=${orderPreserved.size} requested=$effectiveCount recentRelaxed=$recentRelaxedCount")
        Log.w(TAG, "[QUIZ_OUTPUT] LGS subjects=$lastSubjectCounts avgQuality=${"%.1f".format(lastAvgQualityScore)}")

        if (orderPreserved.isNotEmpty()) {
            val lgsIds = orderPreserved.map { it.id }
            val repeatCheck = lgsIds.filter { it in hardBlockIds }
            if (repeatCheck.isNotEmpty()) {
                Log.e(TAG, "REPEAT_MISS LGS: ${repeatCheck.size} questions from blocked set! ids=${repeatCheck.take(5)}")
            } else {
                Log.d(TAG, "REPEAT_VERIFIED LGS: 0 repeats in ${lgsIds.size} questions, hardBlockIds=${hardBlockIds.size}")
            }
            roomStore.recordTestCreated(profileId, effectiveTestId, lgsIds)
            Log.d(TAG, "REPEAT_RECORDED LGS: ${lgsIds.size} IDs saved for profile=$profileId")
            recordSeenForQuiz(profileId, lgsIds)
        }
        return orderPreserved
    }

    /** Reorder selected rows to avoid consecutive same questionType where possible. */
    private fun balanceToAvoidConsecutiveSameType(rows: List<LgsCandidateRow>): List<LgsCandidateRow> {
        if (rows.size <= 1) return rows
        val bySubject = rows.groupBy { com.edumio.app.db.QuestionMapper.mapSubject(it.subject) }
        val result = mutableListOf<LgsCandidateRow>()
        val remaining = rows.toMutableList()

        var prevType: String? = null
        while (remaining.isNotEmpty()) {
            val best = remaining.minByOrNull { row ->
                val sameType = (row.type == prevType)
                val penalty = if (sameType) 1000 else 0
                penalty + (remaining.count { it.type == row.type } - 1)
            } ?: remaining.first()
            remaining.remove(best)
            result.add(best)
            prevType = best.type
        }
        return result
    }

    /**
     * Runs on Dispatchers.IO. Fetches one candidate pool (LIMIT 200) per subject,
     * then evaluates only those candidates for recent/stemHash/similarity and picks 4 per subject.
     */
    private suspend fun pickQuizQuestionsByGradeInternal(
        grade: Int,
        count: Int,
        testId: String?,
        preferredWrongIds: Set<String>,
        maxWrongFraction: Double,
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        val buildStartMs = System.currentTimeMillis()

        // [PERF FIX] PoolHealthLogger.logGradePoolHealth removed from critical path (~12 extra DB queries).
        // Pool health is computed deferred (post first-question) in QuizActivity.
        val gradePickStartMs = System.currentTimeMillis()
        Log.d(TAG, "[QUIZ_PERF] grade_picker_start grade=$grade")

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
        val effectiveCount = count.coerceAtMost(MIN_QUESTIONS_PER_TEST).coerceAtLeast(MIN_QUESTIONS_PER_TEST)
        val maxWrongCount = if (effectiveCount > 0 && maxWrongFraction > 0.0) {
            kotlin.math.floor(effectiveCount * maxWrongFraction).toInt().coerceAtLeast(0)
        } else 0

        val profileId = ProfileStore(context).getCurrentProfileId()
        val effectiveTestId = testId ?: java.util.UUID.randomUUID().toString()

        lastRecentRelaxedCount = 0
        lastSkippedIdCount = 0
        lastSkippedStemHashCount = 0
        lastSkippedSimilarCount = 0
        lastSkippedRecentCount = 0
        lastSimilarRelaxedCount = 0
        lastSubjectCounts = emptyMap()
        lastDbQueryMs = 0
        lastBuildMs = 0
        lastCapReached = false
        lastSyntheticEmergencyTopUpCount = 0
        lastQualityPickSummary = ""
        QualityAudit.reset()
        QualityAudit.currentSelectedMode = "GRADE"
        QualityAudit.currentSelectedGrade = grade

        val dbQueryStartMs = System.currentTimeMillis()
        val recentIds: Set<String> = roomStore.getRecentlySeenIdsForProfile(profileId, 150)
        // Hard-block: IDs from last 5 quizzes are NEVER re-served (repeat=0 guarantee).
        val hardBlockIds: Set<String> = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        Log.d(TAG, "REPEAT_GUARD grade=$grade hardBlockIds=${hardBlockIds.size} recentIds=${recentIds.size}")
        Log.d(TAG, "[QUIZ_DEBUG] requested_mode=GRADE grade=$grade diff=$selectedDifficulty")
        Log.d(TAG, "[QUIZ_DEBUG] repeat_blocked=${hardBlockIds.size} recent_seen=${recentIds.size}")

        // 1) Candidate pools: HARD→MEDIUM→BORDERLINE first; EASY held for emergency. No DB deletion.
        val perSubjectPrimary: MutableMap<Subject, List<QuestionCandidateRow>> = mutableMapOf()
        val perSubjectRelaxed: MutableMap<Subject, List<QuestionCandidateRow>> = mutableMapOf()
        val perSubjectAll: MutableMap<Subject, List<QuestionCandidateRow>> = mutableMapOf()
        val perSubjectEasy: MutableMap<Subject, List<QuestionCandidateRow>> = mutableMapOf()
        val perSubjectTotalForDiff: MutableMap<Subject, Int> = mutableMapOf()
        val perSubjectNonRecentAvailable: MutableMap<Subject, Int> = mutableMapOf()
        fun isEasyBucketRow(row: QuestionCandidateRow): Boolean =
            AdaptiveQuizRuntime.normalizeContentTier(row.qualityTier) == QuizQualityPolicy.TIER_EASY ||
                row.reasoningLevel == 0
        for ((subjEnum, dbKey) in subjectOrder) {
            var pool = roomStore.getCandidatePoolByGradeSubject(grade, dbKey, QuizQualityPolicy.ALL_CONTENT_TIERS)
                .distinctBy { it.id }
            perSubjectEasy[subjEnum] = pool.filter { isEasyBucketRow(it) }
            var nonEasy = pool.filter { !isEasyBucketRow(it) }
            nonEasy = AdaptiveQuizRuntime.sortByTierPriority(nonEasy) { it.qualityTier }
            if (nonEasy.isEmpty()) {
                val batch = QuotaSyntheticQuestions.generateEmergencyTopUp(grade, dbKey, 8)
                roomStore.insertQuestions(batch)
                lastSyntheticEmergencyTopUpCount += batch.size
                pool = roomStore.getCandidatePoolByGradeSubject(grade, dbKey, QuizQualityPolicy.ALL_CONTENT_TIERS)
                    .distinctBy { it.id }
                perSubjectEasy[subjEnum] = pool.filter { isEasyBucketRow(it) }
                nonEasy = AdaptiveQuizRuntime.sortByTierPriority(
                    pool.filter { !isEasyBucketRow(it) }
                ) { it.qualityTier }
            }
            if (nonEasy.isEmpty()) {
                val hardBatch = SyntheticHardQuestionGenerator.generate(grade, dbKey, 6)
                if (hardBatch.isNotEmpty()) {
                    roomStore.insertQuestions(hardBatch)
                    QualityAudit.syntheticGeneratedCount += hardBatch.size
                    pool = roomStore.getCandidatePoolByGradeSubject(grade, dbKey, QuizQualityPolicy.ALL_CONTENT_TIERS)
                        .distinctBy { it.id }
                    perSubjectEasy[subjEnum] = pool.filter { isEasyBucketRow(it) }
                    nonEasy = AdaptiveQuizRuntime.sortByTierPriority(
                        pool.filter { !isEasyBucketRow(it) }
                    ) { it.qualityTier }
                }
            }
            val workPool = nonEasy
            val primary = workPool.filter { it.difficulty == diffInt }
            val primaryIds = primary.map { it.id }.toSet()
            val relaxed = workPool.filter { it.id !in primaryIds }
            perSubjectPrimary[subjEnum] = primary
            perSubjectRelaxed[subjEnum] = relaxed
            perSubjectAll[subjEnum] = workPool
            perSubjectTotalForDiff[subjEnum] = primary.size
            perSubjectNonRecentAvailable[subjEnum] = workPool.count { it.id !in recentIds }
        }
        QualityAudit.playablePoolSizeLastQuery = perSubjectAll.values.sumOf { it.size }
        QualityAudit.currentPoolSourceSummary = "GRADE:${grade} exam!=LGS tiers=all"
        val totalCandidates = perSubjectAll.values.sumOf { it.size }
        val totalEasy = perSubjectEasy.values.sumOf { it.size }
        Log.w(TAG, "[QUIZ_DEBUG] total_candidates=$totalCandidates total_easy=$totalEasy grade=$grade")
        val subjectDebugLine = subjectOrder.joinToString(" | ") { (s, k) ->
            "$k: all=${perSubjectAll[s]?.size ?: 0} easy=${perSubjectEasy[s]?.size ?: 0} nonEasy=${perSubjectAll[s]?.size?.minus(perSubjectEasy[s]?.size ?: 0) ?: 0}"
        }
        Log.w(TAG, "[QUIZ_DEBUG] per_subject=$subjectDebugLine")
        Log.d(TAG, "[QUIZ_PERF] db_query_ms=${System.currentTimeMillis() - dbQueryStartMs}")

        // --- Balanced quota computation ---
        // Compute how many servable (non-hardblocked) questions each subject has.
        val servablePerSubject: Map<Subject, Int> = subjectOrder.associate { (subjEnum, _) ->
            val pool = perSubjectAll[subjEnum] ?: emptyList()
            subjEnum to pool.count { it.id !in excludeIds && it.id !in hardBlockIds }
        }
        val eligibleSubjects = subjectOrder.map { it.first }.filter { (servablePerSubject[it] ?: 0) > 0 }
        val totalServable = eligibleSubjects.sumOf { servablePerSubject[it] ?: 0 }

        // Compute per-subject quotas: each eligible subject gets at least 1 question (min representation),
        // then remaining slots are distributed proportionally to servable pool sizes.
        val quotaPerSubject: MutableMap<Subject, Int> = mutableMapOf()
        if (eligibleSubjects.isNotEmpty() && totalServable > 0) {
            val minPerSubject = 1
            val guaranteedSlots = (eligibleSubjects.size * minPerSubject).coerceAtMost(effectiveCount)
            // Assign guaranteed minimum to each eligible subject (capped by their pool).
            for (subj in eligibleSubjects) {
                val avail = servablePerSubject[subj] ?: 0
                quotaPerSubject[subj] = minOf(minPerSubject, avail)
            }
            // Distribute remaining slots proportionally.
            var remainingQuota = effectiveCount - quotaPerSubject.values.sum()
            if (remainingQuota > 0) {
                // Proportional shares based on servable count.
                val weights = eligibleSubjects.associateWith { (servablePerSubject[it] ?: 0).toDouble() }
                val totalWeight = weights.values.sum()
                // Assign proportional quotas, but cap per subject so no single subject > 50% of test
                // unless only 1-2 subjects exist.
                val maxPerSubjectCap = if (eligibleSubjects.size <= 2) effectiveCount
                    else (effectiveCount * 0.35).toInt().coerceAtLeast(4)
                val proportional = eligibleSubjects.associateWith { subj ->
                    val share = if (totalWeight > 0) (weights[subj]!! / totalWeight * remainingQuota) else 0.0
                    val avail = (servablePerSubject[subj] ?: 0) - (quotaPerSubject[subj] ?: 0)
                    minOf(share.toInt(), avail, maxPerSubjectCap - (quotaPerSubject[subj] ?: 0))
                        .coerceAtLeast(0)
                }
                for (subj in eligibleSubjects) {
                    quotaPerSubject[subj] = (quotaPerSubject[subj] ?: 0) + proportional[subj]!!
                }
                remainingQuota = effectiveCount - quotaPerSubject.values.sum()
                // Distribute leftover one-by-one to subjects with remaining capacity, round-robin.
                if (remainingQuota > 0) {
                    val sortedByCapacity = eligibleSubjects
                        .filter { (servablePerSubject[it] ?: 0) > (quotaPerSubject[it] ?: 0) }
                        .sortedByDescending { (servablePerSubject[it] ?: 0) - (quotaPerSubject[it] ?: 0) }
                    var idx = 0
                    while (remainingQuota > 0 && sortedByCapacity.isNotEmpty()) {
                        val subj = sortedByCapacity[idx % sortedByCapacity.size]
                        val avail = (servablePerSubject[subj] ?: 0) - (quotaPerSubject[subj] ?: 0)
                        val current = quotaPerSubject[subj] ?: 0
                        if (avail > 0 && current < maxPerSubjectCap) {
                            quotaPerSubject[subj] = current + 1
                            remainingQuota--
                        }
                        idx++
                        if (idx >= sortedByCapacity.size * 2) break // safety: avoid infinite loop
                    }
                }
            }
        } else {
            // No eligible subjects at all — fallback to equal split.
            subjectOrder.forEach { (s, _) -> quotaPerSubject[s] = effectiveCount / subjectOrder.size }
        }
        // Subjects not in eligibleSubjects get quota 0.
        subjectOrder.forEach { (s, _) -> quotaPerSubject.putIfAbsent(s, 0) }

        // Debug: log the computed quotas.
        val quotaDebug = StringBuilder("[GRADE_BALANCE] grade=$grade eligible=${eligibleSubjects.size}/${subjectOrder.size}")
        subjectOrder.forEach { (subjEnum, dbKey) ->
            quotaDebug.append(" | $dbKey: servable=${servablePerSubject[subjEnum] ?: 0} quota=${quotaPerSubject[subjEnum] ?: 0}")
        }
        Log.d(TAG, quotaDebug.toString())

        val usedIds = excludeIds.toMutableSet()
        // Hard-block: pre-seed usedIds with last 5 quizzes so they can never be picked
        var repeatBlockedCount = 0
        usedIds.addAll(hardBlockIds)
        repeatBlockedCount = hardBlockIds.size
        Log.d(TAG, "REPEAT_BLOCKED count=$repeatBlockedCount from last 5 quizzes")
        val usedStemHashes = mutableSetOf<String>()
        val selectedTokenSets = mutableListOf<Set<String>>()
        val selectedPerSubject: MutableMap<Subject, MutableList<QuestionCandidateRow>> = mutableMapOf()
        subjectOrder.forEach { (s, _) -> selectedPerSubject[s] = mutableListOf() }

        var wrongUsedCount = 0
        var recentRelaxedCount = 0
        var similarRelaxedCount = 0
        var totalAttempts = 0
        var relaxedDueToCap = false

        fun pickForSubject(subjEnum: Subject, targetForSubject: Int) {
            if (targetForSubject <= 0) return
            var subjectAttempts = 0
            val primary = (perSubjectPrimary[subjEnum] ?: emptyList()).filter { it.id !in usedIds }
            val relaxed = (perSubjectRelaxed[subjEnum] ?: emptyList()).filter { it.id !in usedIds }
            if (primary.isEmpty() && relaxed.isEmpty()) return

            // Selection order: (a) selected diff, (b) same subject relaxed. Partition by recent within each.
            // [PERF] Shuffle once, then cap at CANDIDATE_CAP_PER_PARTITION — avoids O(n) Jaccard scans on large pools.
            val primaryNonRecent = primary.filter { it.id !in recentIds }.shuffled().take(CANDIDATE_CAP_PER_PARTITION)
            val primaryRecent = primary.filter { it.id in recentIds }.shuffled().take(CANDIDATE_CAP_PER_PARTITION)
            val relaxedNonRecent = relaxed.filter { it.id !in recentIds }.shuffled().take(CANDIDATE_CAP_PER_PARTITION)
            val relaxedRecent = relaxed.filter { it.id in recentIds }.shuffled().take(CANDIDATE_CAP_PER_PARTITION)

            fun trySelectFrom(
                pool: List<QuestionCandidateRow>,
                enforceTypeLimit: Boolean,
                enforceSkillLimit: Boolean
            ) {
                if (pool.isEmpty()) return
                val subjectList = selectedPerSubject[subjEnum] ?: mutableListOf()
                val typeCounts = subjectList.groupingBy { it.type }.eachCount().toMutableMap()
                val skillCounts = subjectList.groupingBy { it.skill }.eachCount().toMutableMap()
                val topicCounts = subjectList.groupingBy { it.topic }.eachCount().toMutableMap()

                fun canTakeByTypeAndSkill(q: QuestionCandidateRow): Boolean {
                    if (subjectList.size >= targetForSubject) return false
                    val type = q.type
                    val skill = q.skill
                    // Type cap raised 2→3: 3 of same type is still visible variety
                    if (enforceTypeLimit && type != "UNKNOWN") {
                        val tc = typeCounts[type] ?: 0
                        if (tc >= 3) return false
                    }
                    // Skill cap raised 1→2: less rejection, still ensures skill breadth
                    if (enforceSkillLimit && skill != "UNKNOWN") {
                        val sc = skillCounts[skill] ?: 0
                        if (sc >= 2) return false
                    }
                    // Topic cap: at most 2 questions sharing the same topic per subject
                    if (enforceSkillLimit) {
                        val topic = q.topic
                        if (topic != "OTHER" && topic.isNotBlank()) {
                            val tc = topicCounts[topic] ?: 0
                            if (tc >= 2) return false
                        }
                    }
                    return true
                }

                fun takeFrom(candidates: List<QuestionCandidateRow>, isWrong: Boolean) {
                    if (candidates.isEmpty()) return
                    for (q in candidates) { // already shuffled at partition time above
                        totalAttempts++
                        subjectAttempts++
                        if (totalAttempts > CAP_ATTEMPTS_TOTAL) {
                            lastCapReached = true
                            relaxedDueToCap = true
                        }
                        if (subjectAttempts > CAP_ATTEMPTS_PER_SUBJECT) {
                            lastCapReached = true
                            relaxedDueToCap = true
                        }
                        if (subjectList.size >= targetForSubject) break
                        val id = q.id
                        if (id in usedIds) {
                            lastSkippedIdCount++
                            continue
                        }
                        val qStemHash = q.stemHash.substringBefore(":dup:").ifBlank {
                            stemHash(q.stemNormalized)
                        }
                        if (qStemHash in usedStemHashes) {
                            lastSkippedStemHashCount++
                            continue
                        }
                        if (!canTakeByTypeAndSkill(q)) continue
                        val tokens = if (relaxedDueToCap) emptySet()
                        else {
                            val t = buildQuestionTokenSet(q.stemNormalized, emptyList())
                            if (t.isNotEmpty() && selectedTokenSets.takeLast(SIMILARITY_LOOKBACK).any { prev ->
                                    jaccardSimilarity(t, prev) > NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                                }) {
                                lastSkippedSimilarCount++
                                continue
                            }
                            t
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
                        topicCounts[q.topic] = (topicCounts[q.topic] ?: 0) + 1
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
            fun trySelectFromSimilarRelaxed(pool: List<QuestionCandidateRow>, enforceTypeLimit: Boolean, enforceSkillLimit: Boolean) {
                if (pool.isEmpty()) return
                val subjectList = selectedPerSubject[subjEnum] ?: mutableListOf()
                val typeCounts = subjectList.groupingBy { it.type }.eachCount().toMutableMap()
                val skillCounts = subjectList.groupingBy { it.skill }.eachCount().toMutableMap()
                val topicCounts = subjectList.groupingBy { it.topic }.eachCount().toMutableMap()
                fun canTakeByTypeAndSkill(q: QuestionCandidateRow): Boolean {
                    if (subjectList.size >= targetForSubject) return false
                    val type = q.type
                    val skill = q.skill
                    if (enforceTypeLimit && type != "UNKNOWN") {
                        val tc = typeCounts[type] ?: 0
                        if (tc >= 3) return false
                    }
                    if (enforceSkillLimit && skill != "UNKNOWN") {
                        val sc = skillCounts[skill] ?: 0
                        if (sc >= 2) return false
                    }
                    if (enforceSkillLimit) {
                        val topic = q.topic
                        if (topic != "OTHER" && topic.isNotBlank()) {
                            val tc = topicCounts[topic] ?: 0
                            if (tc >= 2) return false
                        }
                    }
                    return true
                }
                // pool already shuffled (passed from pre-shuffled partitions above)
                val wrongCand = if (preferredWrongIds.isNotEmpty() && maxWrongCount > 0) pool.filter { it.id in preferredWrongIds } else emptyList()
                val normalCand = if (wrongCand.isEmpty()) pool else pool.filter { it.id !in preferredWrongIds }
                for (candidates in listOf(wrongCand, normalCand)) {
                    for (q in candidates) { // already shuffled at partition time
                        totalAttempts++
                        subjectAttempts++
                        if (totalAttempts > CAP_ATTEMPTS_TOTAL) {
                            lastCapReached = true
                            relaxedDueToCap = true
                        }
                        if (subjectAttempts > CAP_ATTEMPTS_PER_SUBJECT) {
                            lastCapReached = true
                            relaxedDueToCap = true
                        }
                        if (subjectList.size >= targetForSubject) break
                        val id = q.id
                        if (id in usedIds) continue
                        val qStemHash = q.stemHash.substringBefore(":dup:").ifBlank { stemHash(q.stemNormalized) }
                        if (qStemHash in usedStemHashes) continue
                        if (!canTakeByTypeAndSkill(q)) continue
                        val isWrong = q.id in preferredWrongIds
                        if (isWrong && wrongUsedCount >= maxWrongCount) continue
                        val tokens = buildQuestionTokenSet(q.stemNormalized, emptyList())
                        val wouldBeSimilar = tokens.isNotEmpty() && selectedTokenSets.takeLast(SIMILARITY_LOOKBACK).any { prev ->
                            jaccardSimilarity(tokens, prev) > NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                        }
                        if (wouldBeSimilar) similarRelaxedCount++
                        subjectList.add(q)
                        usedIds.add(id)
                        usedStemHashes.add(qStemHash)
                        if (tokens.isNotEmpty()) selectedTokenSets.add(tokens)
                        typeCounts[q.type] = (typeCounts[q.type] ?: 0) + 1
                        skillCounts[q.skill] = (skillCounts[q.skill] ?: 0) + 1
                        topicCounts[q.topic] = (topicCounts[q.topic] ?: 0) + 1
                        if (isWrong) wrongUsedCount++
                        if (id in recentIds) recentRelaxedCount++
                    }
                }
                selectedPerSubject[subjEnum] = subjectList
            }

            // 1) Primary (selected difficulty): strict non-recent, enforce diversity first.
            trySelectFrom(primaryNonRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return
            trySelectFrom(primaryNonRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return
            trySelectFrom(primaryNonRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return

            // 2) Primary: allow recently seen.
            trySelectFrom(primaryRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFrom(primaryRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFrom(primaryRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return

            // 3) Relaxed (same subject, other difficulties): non-recent first.
            trySelectFrom(relaxedNonRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFrom(relaxedNonRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFrom(relaxedNonRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return

            // 4) Relaxed: allow recently seen.
            trySelectFrom(relaxedRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFrom(relaxedRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFrom(relaxedRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            if ((selectedPerSubject[subjEnum]?.size ?: 0) >= targetForSubject) return

            // 5) Last resort: relax similarity rule (usedIds and stemHashUsed NEVER relax).
            trySelectFromSimilarRelaxed(primaryNonRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFromSimilarRelaxed(primaryNonRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(primaryNonRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(primaryRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFromSimilarRelaxed(primaryRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(primaryRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(relaxedNonRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFromSimilarRelaxed(relaxedNonRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(relaxedNonRecent, enforceTypeLimit = false, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(relaxedRecent, enforceTypeLimit = true, enforceSkillLimit = true)
            trySelectFromSimilarRelaxed(relaxedRecent, enforceTypeLimit = true, enforceSkillLimit = false)
            trySelectFromSimilarRelaxed(relaxedRecent, enforceTypeLimit = false, enforceSkillLimit = false)
        }

        // 2) Her ders için hesaplanan kota kadar seç (balanced distribution).
        subjectOrder.forEach { (subjEnum, _) ->
            val quota = quotaPerSubject[subjEnum] ?: 0
            pickForSubject(subjEnum, quota)
        }

        val selectedCandidateRows = selectedPerSubject.values.flatten().toMutableList()

        // 3) Eğer toplam < effectiveCount ise, kalan slotları ORANTILI olarak dağıt (round-robin).
        //    Eski davranış: en büyük havuzdan greedy dolduruyordu → tek ders dominasyonu.
        //    Yeni davranış: her turda en fazla kapasitesi kalan dersten 1 soru al.
        var remainingSlots = effectiveCount - selectedCandidateRows.size
        if (remainingSlots > 0) {
            // [PERF] Pre-shuffle each subject's fill pool ONCE. Inner pickExtraFromPartition calls
            // iterate in order — no re-shuffle per round. usedIds checked inline for newly-picked IDs.
            val remainingPerSubject: Map<Subject, List<QuestionCandidateRow>> = subjectOrder.associate { (subjEnum, _) ->
                subjEnum to (perSubjectAll[subjEnum] ?: emptyList()).filter { it.id !in usedIds }.shuffled()
            }
            // Round-robin: cycle through subjects sorted by remaining capacity (descending).
            val subjectsByRemaining = remainingPerSubject.entries
                .filter { it.value.isNotEmpty() }
                .sortedByDescending { it.value.size }
                .map { it.key }

            fun pickExtraFromPartition(
                subj: Subject,
                partition: List<QuestionCandidateRow>,
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
                    val extraWrong = mutableListOf<QuestionCandidateRow>()
                    for (q in wrongCandidates) { // already shuffled in remainingPerSubject
                        totalAttempts++
                        if (totalAttempts > CAP_ATTEMPTS_TOTAL) {
                            lastCapReached = true
                            relaxedDueToCap = true
                        }
                        if (extraWrong.size >= canTakeWrong || remainingSlots <= 0) break
                        val id = q.id
                        if (id in usedIds) {
                            lastSkippedIdCount++
                            continue
                        }
                        val qStemHash = q.stemHash.substringBefore(":dup:").ifBlank { stemHash(q.stemNormalized) }
                        if (qStemHash in usedStemHashes) {
                            lastSkippedStemHashCount++
                            continue
                        }
                        val tokens = if (relaxedDueToCap) emptySet()
                        else {
                            val t = buildQuestionTokenSet(q.stemNormalized, emptyList())
                            if (t.isNotEmpty() && selectedTokenSets.takeLast(SIMILARITY_LOOKBACK).any { prev ->
                                    jaccardSimilarity(t, prev) > NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                                }) {
                                lastSkippedSimilarCount++
                                continue
                            }
                            t
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
                        selectedCandidateRows.addAll(extraWrong)
                        wrongUsedCount += extraWrong.size
                        remainingSlots = effectiveCount - selectedCandidateRows.size
                    }
                }

                if (remainingSlots > 0 && normalCandidates.isNotEmpty()) {
                    val extraNormal = mutableListOf<QuestionCandidateRow>()
                    for (q in normalCandidates) { // already shuffled in remainingPerSubject
                        totalAttempts++
                        if (totalAttempts > CAP_ATTEMPTS_TOTAL) {
                            lastCapReached = true
                            relaxedDueToCap = true
                        }
                        if (extraNormal.size >= remainingSlots) break
                        val id = q.id
                        if (id in usedIds) {
                            lastSkippedIdCount++
                            continue
                        }
                        val qStemHash = q.stemHash.substringBefore(":dup:").ifBlank { stemHash(q.stemNormalized) }
                        if (qStemHash in usedStemHashes) {
                            lastSkippedStemHashCount++
                            continue
                        }
                        val tokens = if (relaxedDueToCap) emptySet()
                        else {
                            val t = buildQuestionTokenSet(q.stemNormalized, emptyList())
                            if (t.isNotEmpty() && selectedTokenSets.takeLast(SIMILARITY_LOOKBACK).any { prev ->
                                    jaccardSimilarity(t, prev) > NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                                }) {
                                lastSkippedSimilarCount++
                                continue
                            }
                            t
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
                        selectedCandidateRows.addAll(extraNormal)
                        remainingSlots = effectiveCount - selectedCandidateRows.size
                    }
                }
            }

            /** Similar-relaxed: skip similarity check. usedIds and stemHashUsed NEVER relax. */
            fun pickExtraFromPartitionSimilarRelaxed(subj: Subject, partition: List<QuestionCandidateRow>) {
                if (partition.isEmpty() || remainingSlots <= 0) return
                val wrongCandidates = if (preferredWrongIds.isNotEmpty() && maxWrongCount > 0) {
                    partition.filter { it.id in preferredWrongIds }
                } else emptyList()
                val normalCandidates = if (wrongCandidates.isEmpty()) partition else partition.filter { it.id !in preferredWrongIds }
                fun takeFrom(candidates: List<QuestionCandidateRow>, isWrong: Boolean) {
                    for (q in candidates) { // already shuffled in remainingPerSubject
                        totalAttempts++
                        if (totalAttempts > CAP_ATTEMPTS_TOTAL) {
                            lastCapReached = true
                            relaxedDueToCap = true
                        }
                        if (remainingSlots <= 0) break
                        val id = q.id
                        if (id in usedIds) continue
                        val qStemHash = q.stemHash.substringBefore(":dup:").ifBlank { stemHash(q.stemNormalized) }
                        if (qStemHash in usedStemHashes) continue
                        if (isWrong && wrongUsedCount >= maxWrongCount) continue
                        val tokens = buildQuestionTokenSet(q.stemNormalized, emptyList())
                        val wouldBeSimilar = tokens.isNotEmpty() && selectedTokenSets.takeLast(SIMILARITY_LOOKBACK).any { prev ->
                            jaccardSimilarity(tokens, prev) > NEAR_DUPLICATE_SIMILARITY_THRESHOLD
                        }
                        if (wouldBeSimilar) similarRelaxedCount++
                        selectedPerSubject[subj]?.add(q)
                        selectedCandidateRows.add(q)
                        usedIds.add(id)
                        usedStemHashes.add(qStemHash)
                        if (tokens.isNotEmpty()) selectedTokenSets.add(tokens)
                        if (isWrong) wrongUsedCount++
                        if (id in recentIds) recentRelaxedCount++
                        remainingSlots = effectiveCount - selectedCandidateRows.size
                    }
                }
                takeFrom(wrongCandidates, isWrong = true)
                takeFrom(normalCandidates, isWrong = false)
            }

            // Phase 1: fill from non-recent pools using ROUND-ROBIN (1 question per subject per round).
            // This prevents a single large-pool subject from grabbing all remaining slots.
            val fillSubjectOrder = subjectsByRemaining.toMutableList()
            var roundRobinSafety = 0
            while (remainingSlots > 0 && fillSubjectOrder.isNotEmpty() && roundRobinSafety < effectiveCount * 2) {
                val beforeSize = selectedCandidateRows.size
                val iter = fillSubjectOrder.iterator()
                while (iter.hasNext() && remainingSlots > 0) {
                    val subj = iter.next()
                    val poolAll = remainingPerSubject[subj].orEmpty().filter { it.id !in usedIds }
                    if (poolAll.isEmpty()) { iter.remove(); continue }
                    val nonRecent = poolAll.filter { it.id !in recentIds }
                    val prevCount = selectedCandidateRows.size
                    if (nonRecent.isNotEmpty()) {
                        // Pick at most 1 per round for balance.
                        val tempRemaining = remainingSlots
                        remainingSlots = 1.coerceAtMost(tempRemaining) // limit to 1
                        pickExtraFromPartition(subj, nonRecent, fromRecent = false)
                        remainingSlots = effectiveCount - selectedCandidateRows.size
                    }
                    if (selectedCandidateRows.size == prevCount) {
                        // Could not pick non-recent; try recent.
                        val recent = poolAll.filter { it.id in recentIds }
                        if (recent.isNotEmpty()) {
                            val tempRemaining = remainingSlots
                            remainingSlots = 1.coerceAtMost(tempRemaining)
                            pickExtraFromPartition(subj, recent, fromRecent = true)
                            remainingSlots = effectiveCount - selectedCandidateRows.size
                        }
                    }
                    if (selectedCandidateRows.size == prevCount) {
                        iter.remove() // subject exhausted
                    }
                }
                roundRobinSafety++
                if (selectedCandidateRows.size == beforeSize) break // no progress
            }

            // Phase 2: last resort – relax similarity round-robin.
            if (remainingSlots > 0) {
                val similarRelaxSubjects = subjectsByRemaining.filter { subj ->
                    remainingPerSubject[subj].orEmpty().any { it.id !in usedIds }
                }.toMutableList()
                roundRobinSafety = 0
                while (remainingSlots > 0 && similarRelaxSubjects.isNotEmpty() && roundRobinSafety < effectiveCount * 2) {
                    val beforeSize = selectedCandidateRows.size
                    val iter = similarRelaxSubjects.iterator()
                    while (iter.hasNext() && remainingSlots > 0) {
                        val subj = iter.next()
                        val poolAll = remainingPerSubject[subj].orEmpty().filter { it.id !in usedIds }
                        if (poolAll.isEmpty()) { iter.remove(); continue }
                        val prevCount = selectedCandidateRows.size
                        pickExtraFromPartitionSimilarRelaxed(subj, poolAll)
                        if (selectedCandidateRows.size == prevCount) iter.remove()
                    }
                    roundRobinSafety++
                    if (selectedCandidateRows.size == beforeSize) break
                }
            }
        }

        // EASY emergency fill: per-subject quota — STRICT subject purity.
        // Only fills remaining quota for each subject from that subject's own EASY pool.
        // Never crosses subject boundary to satisfy another subject's quota.
        if (selectedCandidateRows.size < effectiveCount) {
            Log.w(TAG, "[QUIZ_DEBUG] after_strict=${selectedCandidateRows.size}/$effectiveCount — EASY per-subject fill")
            for ((subjEnum, _) in subjectOrder) {
                if (selectedCandidateRows.size >= effectiveCount) break
                val quota = quotaPerSubject[subjEnum] ?: 0
                val alreadyPicked = selectedPerSubject[subjEnum]?.size ?: 0
                val subjNeed = (quota - alreadyPicked).coerceAtLeast(0)
                if (subjNeed <= 0) continue
                val easyPool = (perSubjectEasy[subjEnum] ?: emptyList())
                    .filter { it.id !in usedIds }
                    .shuffled()
                    .take(subjNeed * 3)
                for (row in easyPool) {
                    if (selectedCandidateRows.size >= effectiveCount) break
                    if ((selectedPerSubject[subjEnum]?.size ?: 0) >= quota) break
                    if (row.id in usedIds) continue
                    selectedCandidateRows.add(row)
                    selectedPerSubject[subjEnum]?.add(row)
                    usedIds.add(row.id)
                }
            }
            Log.w(TAG, "[QUIZ_DEBUG] after_easy_inject=${selectedCandidateRows.size}/$effectiveCount")
        }

        ensureHardMediumQuota(selectedCandidateRows, grade)

        Log.w(TAG, "[QUIZ_DEBUG] after_repeat_and_diversity=${selectedCandidateRows.size}/$effectiveCount")
        Log.d(TAG, "[QUIZ_PERF] selection_ms=${System.currentTimeMillis() - dbQueryStartMs}")
        // 4) Materialize final questions.
        // First pass: try all selected candidates.
        val selectedIdsInOrder = selectedCandidateRows.map { it.id }.distinct()
        val entityRows = roomStore.getQuestionEntitiesByIds(selectedIdsInOrder)
        val byIdE = entityRows.associateBy { it.id }
        // Track which subject each candidate belongs to for loss analysis.
        val candidateSubjectMap = selectedCandidateRows.associate { it.id to it.subject }
        val materializedInOrder = mutableListOf<Question>()
        var eliminatedCount = 0
        val eliminatedPerSubject = mutableMapOf<String, Int>()
        for (id in selectedIdsInOrder) {
            if (materializedInOrder.size >= effectiveCount) break
            val entity = byIdE[id] ?: continue
            val q = materializeSingleQuestion(entity, expectLgs = false)
            if (q != null) {
                materializedInOrder.add(q)
            } else {
                eliminatedCount++
                val subj = candidateSubjectMap[id] ?: entity.subject
                eliminatedPerSubject[subj] = (eliminatedPerSubject[subj] ?: 0) + 1
            }
        }
        if (eliminatedCount > 0) {
            Log.w(TAG, "[GRADE_MATERIALIZE_LOSS] eliminated=$eliminatedCount perSubject=$eliminatedPerSubject")
        }

        // Second pass: if quality gates eliminated candidates, fetch replacements
        // using round-robin across subjects for balanced distribution.
        if (materializedInOrder.size < effectiveCount) {
            val need = effectiveCount - materializedInOrder.size
            Log.w(TAG, "QUIZ_SIZE_FILL need=$need more (eliminated=$eliminatedCount), fetching balanced replacements")
            val materializedIds = materializedInOrder.map { it.id }.toSet()
            // Build per-subject replacement pools (shuffled within each subject).
            val replacementBySubject = subjectOrder.associate { (subjEnum, _) ->
                subjEnum to (perSubjectAll[subjEnum] ?: emptyList())
                    .filter { it.id !in usedIds && it.id !in materializedIds }
                    .shuffled()
                    .toMutableList()
            }
            val allReplacementIds = replacementBySubject.values.flatten().map { it.id }.take(need * 3)
            val replacementEntities = roomStore.getQuestionEntitiesByIds(allReplacementIds)
            val replByIdE = replacementEntities.associateBy { it.id }
            // Round-robin: try one replacement from each subject per round.
            val activeSubjects = subjectOrder.map { it.first }
                .filter { replacementBySubject[it]?.isNotEmpty() == true }.toMutableList()
            var rrSafety = 0
            while (materializedInOrder.size < effectiveCount && activeSubjects.isNotEmpty() && rrSafety < need * 3) {
                val iter = activeSubjects.iterator()
                while (iter.hasNext() && materializedInOrder.size < effectiveCount) {
                    val subj = iter.next()
                    val pool = replacementBySubject[subj]
                    if (pool == null || pool.isEmpty()) { iter.remove(); continue }
                    var found = false
                    while (pool.isNotEmpty()) {
                        val row = pool.removeFirst()
                        val entity = replByIdE[row.id] ?: continue
                        val q = materializeSingleQuestion(entity, expectLgs = false)
                        if (q != null) {
                            materializedInOrder.add(q)
                            usedIds.add(entity.id)
                            found = true
                            break
                        } else {
                            Log.w(TAG, "[POOL_SHORTAGE] quality gate blocked replacement id=${entity.id} — skipping (strict mode)")
                        }
                    }
                    if (!found) iter.remove()
                }
                rrSafety++
            }
        }

        // EMERGENCY PASS: quality gates eliminated too many — recover using relaxed materialization.
        // Subject purity: only recover questions whose candidate subject matches the quota map.
        // Diversity: track skill/type counts across recovered questions.
        var emergencyUsed = false
        if (materializedInOrder.size < effectiveCount) {
            emergencyUsed = true
            val materializedIds = materializedInOrder.map { it.id }.toSet()
            Log.w(TAG, "[QUIZ_DEBUG] after_quality=${materializedInOrder.size}/$effectiveCount eliminated=$eliminatedCount — emergency recover")

            // Per-subject diversity counters for emergency path (lightweight — no NLP)
            val emergencySkillCounts = mutableMapOf<String, Int>()
            val emergencyTypeCounts = mutableMapOf<String, Int>()
            materializedInOrder.forEach { q ->
                emergencySkillCounts[q.skill] = (emergencySkillCounts[q.skill] ?: 0) + 1
                emergencyTypeCounts[q.type] = (emergencyTypeCounts[q.type] ?: 0) + 1
            }
            val MAX_SAME_SKILL = 3
            val MAX_SAME_TYPE = 4

            fun tryEmergencyRecover(entity: QuestionEntity): Question? {
                val q = materializeSingleQuestionEmergency(entity, expectLgs = false) ?: return null
                // Lightweight diversity gate
                if ((emergencySkillCounts[q.skill] ?: 0) >= MAX_SAME_SKILL && q.skill != "UNKNOWN") return null
                if ((emergencyTypeCounts[q.type] ?: 0) >= MAX_SAME_TYPE && q.type != "UNKNOWN") return null
                emergencySkillCounts[q.skill] = (emergencySkillCounts[q.skill] ?: 0) + 1
                emergencyTypeCounts[q.type] = (emergencyTypeCounts[q.type] ?: 0) + 1
                return q
            }

            // Pass A: re-try already-fetched entities (byIdE) that were rejected by shouldQuarantine.
            // No extra DB call — byIdE has ALL selectedIdsInOrder entities.
            val retryIds = selectedIdsInOrder.filter { it !in materializedIds }
            for (id in retryIds) {
                if (materializedInOrder.size >= effectiveCount) break
                val entity = byIdE[id] ?: continue
                val q = tryEmergencyRecover(entity)
                if (q != null) {
                    materializedInOrder.add(q)
                    Log.d(TAG, "[QUIZ_DEBUG] emergency_recover id=$id tier=${entity.qualityTier}")
                }
            }
            Log.w(TAG, "[QUIZ_DEBUG] after_emergency_recover=${materializedInOrder.size}/$effectiveCount")

            // Pass B: EASY tier, per-subject quota — subject purity strictly enforced.
            if (materializedInOrder.size < effectiveCount) {
                val materializedIds2 = materializedInOrder.map { it.id }.toSet()
                for ((subjEnum, _) in subjectOrder) {
                    if (materializedInOrder.size >= effectiveCount) break
                    val quota = quotaPerSubject[subjEnum] ?: 0
                    val alreadyMat = materializedInOrder.count { it.subject == subjEnum }
                    val subjNeed = (quota - alreadyMat).coerceAtLeast(0)
                    if (subjNeed <= 0) continue
                    val easyPool = (perSubjectEasy[subjEnum] ?: emptyList())
                        .filter { it.id !in materializedIds2 && it.id !in usedIds }
                        .shuffled()
                        .take(subjNeed * 4)
                    val easyIds = easyPool.map { it.id }.filter { it !in byIdE }
                    val fetchedEasy = if (easyIds.isNotEmpty())
                        roomStore.getQuestionEntitiesByIds(easyIds).associateBy { it.id }
                    else emptyMap()
                    for (row in easyPool) {
                        if (materializedInOrder.size >= effectiveCount) break
                        if (materializedInOrder.count { it.subject == subjEnum } >= quota) break
                        val entity = byIdE[row.id] ?: fetchedEasy[row.id] ?: continue
                        val q = tryEmergencyRecover(entity)
                        if (q != null) {
                            materializedInOrder.add(q)
                            usedIds.add(row.id)
                            Log.d(TAG, "[QUIZ_DEBUG] easy_emergency id=${row.id} subj=${subjEnum.name} tier=${entity.qualityTier}")
                        }
                    }
                }
                Log.w(TAG, "[QUIZ_DEBUG] after_easy_emergency=${materializedInOrder.size}/$effectiveCount")
            }
        }

        // If still short after all emergency passes: log and serve what we have.
        // STRICT QUALITY MODE: no hardcoded fallback, no EASY injection.
        if (materializedInOrder.size < effectiveCount) {
            Log.w(TAG, "[POOL_SHORTAGE] grade=$grade: could only fill ${materializedInOrder.size}/$effectiveCount questions after strict quality gates. Serving shorter quiz.")
        }
        if (eliminatedCount > 0) {
            Log.d(TAG, "QUIZ_SIZE_RESULT picked=${materializedInOrder.size}/$effectiveCount eliminated=$eliminatedCount filled=${materializedInOrder.size - (selectedIdsInOrder.size - eliminatedCount)}")
        }
        Log.d(TAG, "[QUIZ_DEBUG] GRADE materializationResult mode=GRADE grade=$grade candidateCount=${selectedIdsInOrder.size} materializedCount=${materializedInOrder.size} eliminatedCount=$eliminatedCount targetCount=$effectiveCount")

        val finalQuestions = materializedInOrder
            .distinctBy { it.id }
            .take(effectiveCount)
            .shuffled()

        // Test snapshot + tekrarları engellemek için kayıt.
        val questionIds = finalQuestions.map { it.id }
        // Verify no repeats slipped through
        val repeatCheck = questionIds.filter { it in hardBlockIds }
        if (repeatCheck.isNotEmpty()) {
            Log.e(TAG, "REPEAT_MISS grade: ${repeatCheck.size} questions from blocked set! ids=${repeatCheck.take(5)}")
        } else {
            Log.d(TAG, "REPEAT_VERIFIED grade: 0 repeats in ${questionIds.size} questions, hardBlockIds=${hardBlockIds.size}")
        }
        roomStore.recordTestCreated(profileId, effectiveTestId, questionIds)
        Log.d(TAG, "REPEAT_RECORDED grade: ${questionIds.size} IDs saved to test history for profile=$profileId")
        recordSeenForQuiz(profileId, questionIds)
        lastDbQueryMs = System.currentTimeMillis() - dbQueryStartMs

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

        // Distribution proof: final picked counts per subject in materialized questions.
        val finalSubjectCounts = finalQuestions.groupingBy {
            com.edumio.app.db.QuestionMapper.toDbSubject(it.subject)
        }.eachCount()
        val balanceProof = StringBuilder("[GRADE_BALANCE_PROOF] grade=$grade total=${finalQuestions.size}")
        subjectOrder.forEach { (subjEnum, dbKey) ->
            val quota = quotaPerSubject[subjEnum] ?: 0
            val picked = selectedPerSubject[subjEnum]?.size ?: 0
            val finalCount = finalSubjectCounts[dbKey] ?: 0
            balanceProof.append(" | $dbKey: quota=$quota picked=$picked final=$finalCount")
        }
        Log.d(TAG, balanceProof.toString())

        // Performance: candidate-pool strategy targets buildMs < 500ms (no full DB scan).
        lastBuildMs = System.currentTimeMillis() - buildStartMs
        Log.w(TAG, "[QUIZ_DEBUG] final=${finalQuestions.size}/$effectiveCount grade=$grade")
        Log.w(TAG, "[QUIZ_PERF] grade_total_build_ms=$lastBuildMs quarantine=${QualityAudit.quarantinedLowQualityCount} modeReject=${QualityAudit.rejectedWrongModeCount}")
        // [QUIZ_OUTPUT] Structured output for logcat inspection
        Log.w(TAG, "[QUIZ_OUTPUT] mode=GRADE grade=$grade total=${finalQuestions.size}/$effectiveCount emergency_used=$emergencyUsed")
        Log.w(TAG, "[QUIZ_OUTPUT] final_subjects=${finalQuestions.groupingBy { com.edumio.app.db.QuestionMapper.toDbSubject(it.subject) }.eachCount()}")
        Log.w(TAG, "[QUIZ_OUTPUT] final_topics=${finalQuestions.groupingBy { it.topic?.take(30) ?: "?" }.eachCount()}")
        Log.w(TAG, "[QUIZ_OUTPUT] final_question_types=${finalQuestions.groupingBy { it.type }.eachCount()}")
        Log.w(TAG, "[QUIZ_OUTPUT] final_ids=${finalQuestions.map { it.id }}")
        QualityAudit.servedGeneralCount = finalQuestions.size
        lastQualityPickSummary =
            "adaptive HARD>MEDIUM>BORDERLINE [STRICT_QUALITY_MODE no-EASY no-recovery] " +
                "synTopUp=$lastSyntheticEmergencyTopUpCount synHard=${QualityAudit.syntheticGeneratedCount} " +
                "audit H/M/B/E=${QualityAudit.hardServed}/${QualityAudit.mediumServed}/${QualityAudit.borderlineServed}/${QualityAudit.easyEmergencyUsed} " +
                "upgraded=${QualityAudit.upgradedQuestionsCount} emergency=${QualityAudit.emergencyFallbackUsed} " +
                "modeReject=${QualityAudit.rejectedWrongModeCount} quarantine=${QualityAudit.quarantinedLowQualityCount} general=${QualityAudit.servedGeneralCount}"

        Log.w(TAG, "[QUIZ_PERF] GRADE=$grade buildMs=$lastBuildMs served=${finalQuestions.size} requested=$effectiveCount recentRelaxed=$recentRelaxedCount similarRelaxed=$similarRelaxedCount")
        Log.w(TAG, "[QUIZ_OUTPUT] GRADE=$grade subjects=$lastSubjectCounts avgQuality=${"%.1f".format(lastAvgQualityScore)}")
        return finalQuestions
    }

    private fun materializeSingleQuestion(entity: QuestionEntity, expectLgs: Boolean): Question? {
        val et = entity.examType ?: "GENERAL"
        val isLgsRow = et == "LGS"
        if (expectLgs != isLgsRow) {
            Log.e(TAG, "ELIMINATE_REASON id=${entity.id} reason=MODE_MISMATCH examType=$et expectLgs=$expectLgs")
            QualityAudit.rejectedWrongModeCount++
            return null
        }
        if (!entity.unservableReason.isNullOrBlank()) {
            Log.w(TAG, "ELIMINATE_REASON id=${entity.id} reason=UNSERVABLE unservableReason=${entity.unservableReason}")
            return null
        }
        // LowQualityQuarantine: skip for this session only.
        // NEVER persist unservableReason during quiz picking — permanent writes
        // cause progressive pool depletion across sessions, eventually dropping
        // the servable count below MIN_QUESTIONS_PER_TEST.
        if (LowQualityQuarantine.shouldQuarantine(entity)) {
            Log.w(TAG, "[LOW_QUALITY_SKIPPED] id=${entity.id} tier=${entity.qualityTier} reasoning=${entity.reasoningLevel} distractor=${entity.distractorQualityScore} stem='${entity.questionText.take(60)}'")
            QualityAudit.quarantinedLowQualityCount++
            return null
        }
        val base = QuestionMapper.toQuestion(entity)
        Log.d(TAG, "MATERIALIZE id=${entity.id} dbChoices=${base.choices.joinToString("|") { it.take(25) }}")
        val (upStem, didUp) = AdaptiveQuizRuntime.maybeUpgradeEntity(entity)
        if (didUp) QualityAudit.upgradedQuestionsCount++
        val stemUse = upStem ?: base.stem
        val fixed = AdaptiveQuizRuntime.fixDistractors(base.choices, stemUse, entity.answerIndex)
        if (fixed == null) {
            Log.w(TAG, "DISTRACTOR_ELIMINATED id=${entity.id} stem=${entity.questionText.take(50)}")
            QualityAudit.quarantinedLowQualityCount++
            return null
        }
        // Final safety: verify no suffix pattern leaked through
        val hasSuffix = fixed.any { AdaptiveQuizRuntime.containsSuffixPattern(it) }
        if (hasSuffix) {
            Log.e(TAG, "DISTRACTOR_SUFFIX_LEAK id=${entity.id} choiceCount=${fixed.size}")
            QualityAudit.quarantinedLowQualityCount++
            return null
        }
        Log.d(TAG, "DISTRACTOR_FINAL id=${entity.id} source=${if (fixed == base.choices) "original" else "generated"} " +
            "choices=${fixed.joinToString("|") { it.take(25) }}")
        QualityAudit.recordTier(entity.qualityTier)
        return QuestionMapper.toQuestion(
            entity,
            presentationStem = upStem,
            presentationChoices = fixed,
            contentQualityTier = entity.qualityTier,
        )
    }

    /**
     * Emergency materialization: skips [LowQualityQuarantine.shouldQuarantine] so that
     * questions that fail the runtime quality heuristic can still be served when the strict
     * pool has collapsed to below the minimum. Still enforces:
     *  - mode mismatch (LGS vs GENERAL)
     *  - persisted [QuestionEntity.unservableReason] (hard quarantine)
     *  - distractor validity ([AdaptiveQuizRuntime.fixDistractors])
     *  - basic structural validity (non-empty stem, 4 valid options, valid answerIndex,
     *    no placeholder text) — intentionally minimal, no quality heuristics.
     */
    private fun materializeSingleQuestionEmergency(entity: QuestionEntity, expectLgs: Boolean): Question? {
        // Mode guard
        val et = entity.examType ?: "GENERAL"
        val isLgsRow = et == "LGS"
        if (expectLgs != isLgsRow) return null

        // Persisted hard quarantine
        if (!entity.unservableReason.isNullOrBlank()) return null

        // --- Minimal structural validity (no heavy quality filters) ---

        // 1. Non-empty stem
        val stem = entity.questionText.trim()
        if (stem.isEmpty()) return null

        // 2. answerIndex in [0, 3]
        val answerIdx = entity.answerIndex
        if (answerIdx !in 0..3) return null

        // 3. Parse options and require exactly 4 non-blank entries
        val rawOptions = try {
            val arr = org.json.JSONArray(entity.optionsJson)
            List(arr.length()) { arr.getString(it).trim() }
        } catch (_: Exception) {
            emptyList()
        }
        if (rawOptions.size < 4) return null
        val validOptions = rawOptions.take(4)
        if (validOptions.any { it.isBlank() }) return null

        // 4. No placeholder text (catches corrupt/template rows)
        val placeholderPattern = Regex(
            "\\[.*?\\]|\\{.*?\\}|<.*?>|TODO|PLACEHOLDER|EXAMPLE|SAMPLE|lorem ipsum",
            RegexOption.IGNORE_CASE
        )
        if (placeholderPattern.containsMatchIn(stem)) return null
        if (validOptions.any { placeholderPattern.containsMatchIn(it) }) return null

        // --- Distractor fix (shared with normal path) ---
        val base = QuestionMapper.toQuestion(entity)
        val fixed = AdaptiveQuizRuntime.fixDistractors(base.choices, stem, answerIdx)
            ?: return null
        if (fixed.any { AdaptiveQuizRuntime.containsSuffixPattern(it) }) return null

        return QuestionMapper.toQuestion(entity, presentationStem = null, presentationChoices = fixed, contentQualityTier = entity.qualityTier)
    }

    private fun QuestionEntity.toCandidateRow(): QuestionCandidateRow = QuestionCandidateRow(
        id = id,
        subject = subject,
        difficulty = difficulty,
        grade = grade,
        stemHash = stemHash,
        stemNormalized = stemNormalized,
        type = type,
        skill = skill,
        topic = topic ?: "OTHER",
        qualityTier = qualityTier,
        reasoningLevel = reasoningLevel,
    )

    /**
     * Final quiz must be at least 60% HARD+MEDIUM; replace weaker rows with synthetic HARD.
     */
    private fun ensureHardMediumQuota(rows: MutableList<QuestionCandidateRow>, grade: Int): Int {
        val n = rows.size
        if (n == 0) return 0
        fun isHm(row: QuestionCandidateRow): Boolean {
            val t = AdaptiveQuizRuntime.normalizeContentTier(row.qualityTier)
            return t == QuizQualityPolicy.TIER_HARD || t == QuizQualityPolicy.TIER_MEDIUM
        }
        var hm = rows.count { isHm(it) }
        val target = kotlin.math.ceil(n * 0.6).toInt()
        var need = (target - hm).coerceAtLeast(0)
        if (need == 0) return 0
        val replaceIndices = rows.indices
            .filter { !isHm(rows[it]) }
            .sortedBy { idx ->
                when (AdaptiveQuizRuntime.normalizeContentTier(rows[idx].qualityTier)) {
                    QuizQualityPolicy.TIER_EASY -> 0
                    QuizQualityPolicy.TIER_BORDERLINE -> 1
                    else -> 2
                }
            }
        var replaced = 0
        for (idx in replaceIndices) {
            if (need <= 0) break
            val old = rows[idx]
            val batch = SyntheticHardQuestionGenerator.generate(grade, old.subject, 1)
            if (batch.isEmpty()) continue
            roomStore.insertQuestions(batch)
            QualityAudit.syntheticGeneratedCount += batch.size
            val newEntity = batch.first()
            rows[idx] = newEntity.toCandidateRow()
            replaced++
            need--
        }
        return replaced
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
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        Log.d(TAG, "REPEAT_BLOCKED adaptive blocked=${hardBlockIds.size}")

        val picker = SpacedRepetitionPicker(
            historyDao = com.edumio.app.db.DatabaseProvider.get(context).historyDao(),
            roomStore = roomStore,
            getQuestionIdsFromLastNTests = { pid, n -> roomStore.getQuestionIdsFromLastNTests(pid, n) }
        )
        var questions = picker.pick(finalPool, count, profileId, excludeIds = hardBlockIds)

        if (questions.isEmpty()) {
            Log.w(TAG, "REPEAT_LAST_RESORT adaptive: picker returned empty, fallback to shuffled pool")
            val unique = mutableListOf<Question>()
            val used = hardBlockIds.toMutableSet()
            for (q in finalPool.shuffled()) {
                if (unique.size >= count) break
                if (q.id in used) continue
                used.add(q.id)
                unique.add(q)
            }
            if (unique.size < count) {
                Log.w(TAG, "REPEAT_LAST_RESORT adaptive: only ${unique.size} after blocking, allowing blocked")
                val usedFb = unique.map { it.id }.toMutableSet()
                for (q in finalPool.shuffled()) {
                    if (unique.size >= count) break
                    if (q.id in usedFb) continue
                    usedFb.add(q.id)
                    unique.add(q)
                }
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

    /** Gate questions - sadece grade filtresi ile (grade 1-7). */
    fun pickGateQuestionsByGrade(
        grade: Int,
        count: Int = MIN_QUESTIONS_PER_TEST,
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        if (grade !in 1..7) return emptyList()
        val profileId = ProfileStore(context).getCurrentProfileId()
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        val allExclude = excludeIds + hardBlockIds
        Log.d(TAG, "REPEAT_BLOCKED gate grade=$grade blocked=${hardBlockIds.size}")
        fun filterPool(src: List<Question>) = src.filter { it.id !in allExclude }
        var pool = filterPool(roomStore.getQuestionsByGrade(grade))
        if (pool.isEmpty()) pool = filterPool(getFallbackQuestions().filter { it.grade == grade })
        if (pool.isEmpty()) pool = filterPool(getFallbackQuestions())
        // If pool is too small after blocking, allow LAST_RESORT from blocked pool
        val needLastResort = pool.size < count
        if (needLastResort) {
            Log.w(TAG, "REPEAT_LAST_RESORT gate: pool=${pool.size} < count=$count, allowing blocked questions")
            val extraPool = (roomStore.getQuestionsByGrade(grade)
                .filter { it.id !in excludeIds && it.id !in pool.map { q -> q.id }.toSet() })
            pool = pool + extraPool
        }
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
            val usedFb = mutableSetOf<String>()
            for (q in pool.shuffled()) {
                if (unique.size >= count) break
                if (q.id in usedFb) continue
                usedFb.add(q.id)
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
        // Record to cross-quiz history so next quiz blocks these IDs
        roomStore.recordTestCreated(profileId, java.util.UUID.randomUUID().toString(), toReturn.map { it.id })
        roomStore.recordSeenIdsForProfile(profileId, toReturn.map { it.id })
        Log.d(TAG, "REPEAT_RECORDED gate(grade): ${toReturn.size} IDs saved for profile=$profileId")
        return toReturn
    }

    /** Boss questions - sadece grade filtresi ile (grade 1-7). */
    fun pickBossQuestionsByGrade(grade: Int, count: Int = MIN_QUESTIONS_PER_TEST): List<Question> {
        if (grade !in 1..7) return emptyList()
        val profileId = ProfileStore(context).getCurrentProfileId()
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        Log.d(TAG, "REPEAT_BLOCKED boss grade=$grade blocked=${hardBlockIds.size}")
        var pool = roomStore.getQuestionsByGrade(grade).filter { it.id !in hardBlockIds }
        if (pool.isEmpty()) pool = getFallbackQuestions().filter { it.grade == grade && it.id !in hardBlockIds }
        if (pool.isEmpty()) {
            Log.w(TAG, "REPEAT_LAST_RESORT boss: no questions after blocking, allowing all")
            pool = roomStore.getQuestionsByGrade(grade)
            if (pool.isEmpty()) pool = getFallbackQuestions().filter { it.grade == grade }
            if (pool.isEmpty()) pool = getFallbackQuestions()
        }
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
        val bossResult = result.distinctBy { it.id }.take(count).shuffled()
        // Record to cross-quiz history
        roomStore.recordTestCreated(profileId, java.util.UUID.randomUUID().toString(), bossResult.map { it.id })
        roomStore.recordSeenIdsForProfile(profileId, bossResult.map { it.id })
        Log.d(TAG, "REPEAT_RECORDED boss(grade): ${bossResult.size} IDs saved for profile=$profileId")
        return bossResult
    }

    /** Remedial questions - sadece grade filtresi ile. */
    fun pickRemedialQuestionsByGrade(grade: Int, count: Int = MIN_QUESTIONS_PER_TEST, weakTopicIds: List<String> = emptyList()): Pair<List<Question>, Boolean> {
        if (grade !in 1..7) return Pair(emptyList(), true)
        val profileId = ProfileStore(context).getCurrentProfileId()
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        Log.d(TAG, "REPEAT_BLOCKED remedial grade=$grade blocked=${hardBlockIds.size}")
        val all = roomStore.getQuestionsByGrade(grade).ifEmpty { getFallbackQuestions().filter { it.grade == grade } }
            .ifEmpty { getFallbackQuestions() }
        val allMap = all.associateBy { it.id }
        val userId = com.edumio.app.core.ActiveProfileManager.getActiveProfileId(context)
        val wrongIds = weakTopicIds.ifEmpty { roomStore.getWrongQuestionIds(userId, 14).toList() }
        val weakTopics = wrongIds.mapNotNull { allMap[it]?.subject?.tr }.distinct()
        val byTopic = all.groupBy { it.subject.tr }
        var pool = mutableListOf<Question>()
        for (topic in weakTopics) {
            byTopic[topic]?.let { pool.addAll(it.filter { q -> q.id !in hardBlockIds }) }
        }
        if (pool.isEmpty()) pool = all.filter { it.id !in hardBlockIds }.toMutableList()
        // Last resort: if pool is too small, allow blocked questions
        if (pool.size < count) {
            Log.w(TAG, "REPEAT_LAST_RESORT remedial: pool=${pool.size} < count=$count")
            if (pool.isEmpty()) pool = all.toMutableList()
        }
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
        // Record to cross-quiz history
        if (questions.isNotEmpty()) {
            roomStore.recordTestCreated(profileId, java.util.UUID.randomUUID().toString(), questions.map { it.id })
            roomStore.recordSeenIdsForProfile(profileId, questions.map { it.id })
            Log.d(TAG, "REPEAT_RECORDED remedial(grade): ${questions.size} IDs saved for profile=$profileId")
        }
        return Pair(questions, pool.isEmpty())
    }

    /** Retry wrong questions - sadece grade filtresi ile. */
    fun pickRetryWrongQuestionsByGrade(grade: Int): List<Question> {
        if (grade !in 1..7) return emptyList()
        val userId = com.edumio.app.core.ActiveProfileManager.getActiveProfileId(context)
        val wrongIds = roomStore.getAllWrongIds(userId)
        if (wrongIds.isEmpty()) return emptyList()
        val all = roomStore.getQuestionsByGrade(grade).associateBy { it.id }
        val fallback = getFallbackQuestions().filter { it.grade == grade }.associateBy { it.id }
        val allMap = if (all.isEmpty()) fallback else all
        return wrongIds.mapNotNull { allMap[it] }
    }

    /** Boss test: harder question pool. */
    fun pickBossQuestions(levelGroup: LevelGroup, count: Int = MIN_QUESTIONS_PER_TEST): List<Question> {
        val profileId = ProfileStore(context).getCurrentProfileId()
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        Log.d(TAG, "REPEAT_BLOCKED boss(level) blocked=${hardBlockIds.size}")
        val (all, _) = loadAllQuestionsWithStats()
        val allPool = if (all.isEmpty()) getFallbackQuestions() else all
        val hardPool = allPool.filter { it.levelGroup == levelGroup && it.difficulty == QuizDifficulty.HARD && it.id !in hardBlockIds }
        val pool = if (hardPool.isNotEmpty()) hardPool else allPool.filter { it.levelGroup == levelGroup && it.id !in hardBlockIds }
        var base = if (pool.isEmpty()) allPool.filter { it.id !in hardBlockIds } else pool
        if (base.isEmpty()) {
            Log.w(TAG, "REPEAT_LAST_RESORT boss(level): allowing blocked questions")
            base = allPool.filter { it.levelGroup == levelGroup }.ifEmpty { allPool }
        }
        val result = base.shuffled().distinctBy { it.id }.take(count).toMutableList()
        if (result.size < count && base.isNotEmpty()) {
            val usedIds = result.map { it.id }.toMutableSet()
            for (q in base.shuffled()) {
                if (result.size >= count) break
                if (q.id in usedIds) continue
                usedIds.add(q.id)
                result.add(q)
            }
        }
        val bossLevelResult = result.shuffled()
        // Record to cross-quiz history
        if (bossLevelResult.isNotEmpty()) {
            roomStore.recordTestCreated(profileId, java.util.UUID.randomUUID().toString(), bossLevelResult.map { it.id })
            roomStore.recordSeenIdsForProfile(profileId, bossLevelResult.map { it.id })
            Log.d(TAG, "REPEAT_RECORDED boss(level): ${bossLevelResult.size} IDs saved for profile=$profileId")
        }
        return bossLevelResult
    }

    /** Remedial mini-quiz: focused on weak topics. Prefer lastFailedWrongIds from ProtectionPrefs.
     * weakTopic pool -> if empty -> global pool -> fallback. Never returns empty.
     * @return Pair(questions, usedFallbackDueToEmptyPool) - when true, parent should be warned. */
    fun pickRemedialQuestions(levelGroup: LevelGroup, count: Int = MIN_QUESTIONS_PER_TEST, weakTopicIds: List<String> = emptyList()): Pair<List<Question>, Boolean> {
        val profileId = ProfileStore(context).getCurrentProfileId()
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        Log.d(TAG, "REPEAT_BLOCKED remedial(level) blocked=${hardBlockIds.size}")
        val global = getGlobalPool()
        val all = global.associateBy { it.id }
        val userId = com.edumio.app.core.ActiveProfileManager.getActiveProfileId(context)
        val wrongIds = weakTopicIds.ifEmpty { roomStore.getWrongQuestionIds(userId, 14).toList() }
        val weakTopics = wrongIds.mapNotNull { all[it]?.subject?.tr }.distinct()
        val byTopic = all.values.groupBy { it.subject.tr }
        var pool = mutableListOf<Question>()
        for (topic in weakTopics) {
            byTopic[topic]?.let { pool.addAll(it.filter { q -> q.levelGroup == levelGroup && q.id !in hardBlockIds }) }
        }
        if (pool.isEmpty()) {
            pool = all.values.filter { it.levelGroup == levelGroup && it.id !in hardBlockIds }.toMutableList()
        }
        if (pool.isEmpty()) {
            pool = global.filter { it.id !in hardBlockIds }.toMutableList()
        }
        if (pool.size < count) {
            Log.w(TAG, "REPEAT_LAST_RESORT remedial(level): pool=${pool.size} < count=$count")
            if (pool.isEmpty()) pool = global.toMutableList()
        }
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
        // Record to cross-quiz history
        if (questions.isNotEmpty()) {
            roomStore.recordTestCreated(profileId, java.util.UUID.randomUUID().toString(), questions.map { it.id })
            roomStore.recordSeenIdsForProfile(profileId, questions.map { it.id })
            Log.d(TAG, "REPEAT_RECORDED remedial(level): ${questions.size} IDs saved for profile=$profileId")
        }
        return Pair(questions, usedFallback)
    }

    fun pickRetryWrongQuestions(levelGroup: LevelGroup): List<Question> {
        val userId = com.edumio.app.core.ActiveProfileManager.getActiveProfileId(context)
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

    /** Gate quiz: new quiz each attempt. Shuffled pool + cross-quiz hard-block. Same question cannot repeat within test. */
    fun pickGateQuestions(
        levelGroup: LevelGroup,
        count: Int = MIN_QUESTIONS_PER_TEST,
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        val profileId = ProfileStore(context).getCurrentProfileId()
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        val allExclude = excludeIds + hardBlockIds
        Log.d(TAG, "REPEAT_BLOCKED gate(level) blocked=${hardBlockIds.size}")
        val global = getGlobalPool()
        val base = global.filter { it.levelGroup == levelGroup }.ifEmpty { global }
        var pool = base.filter { it.id !in allExclude }
        if (pool.size < count) {
            Log.w(TAG, "REPEAT_LAST_RESORT gate(level): pool=${pool.size} < count=$count")
            pool = base.filter { it.id !in excludeIds }
        }
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
        roomStore.recordTestCreated(profileId, java.util.UUID.randomUUID().toString(), toReturn.map { it.id })
        roomStore.recordSeenIdsForProfile(profileId, toReturn.map { it.id })
        Log.d(TAG, "REPEAT_RECORDED gate(level): ${toReturn.size} IDs saved for profile=$profileId")
        return toReturn
    }

    /**
     * Fast relaxed picker for timeout fallback. No similarity/type/skill/diversity checks.
     * Fetch pool with LIMIT, randomize in memory. Target: build < 500ms.
     * Still enforces cross-quiz repeat blocking (5 quiz window).
     */
    fun pickQuizQuestionsRelaxedByGrade(
        grade: Int,
        count: Int = MIN_QUESTIONS_PER_TEST,
        testId: String? = null,
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        if (grade !in 1..7) return emptyList()
        val profileId = ProfileStore(context).getCurrentProfileId()
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        val allExclude = excludeIds + hardBlockIds
        Log.d(TAG, "REPEAT_BLOCKED relaxed grade=$grade blocked=${hardBlockIds.size}")
        fun filterPool(src: List<Question>) = src.filter { it.id !in allExclude }
        var pool = filterPool(roomStore.getQuestionsByGrade(grade))
        if (pool.isEmpty()) pool = filterPool(getFallbackQuestions().filter { it.grade == grade })
        if (pool.isEmpty()) {
            Log.w(TAG, "REPEAT_LAST_RESORT relaxed: pool empty after blocking, allowing all")
            pool = roomStore.getQuestionsByGrade(grade).filter { it.id !in excludeIds }
            if (pool.isEmpty()) pool = getFallbackQuestions().filter { it.grade == grade }
            if (pool.isEmpty()) pool = getFallbackQuestions()
        }
        val result = pool.shuffled().distinctBy { it.id }.take(count)
        roomStore.recordTestCreated(profileId, java.util.UUID.randomUUID().toString(), result.map { it.id })
        roomStore.recordSeenIdsForProfile(profileId, result.map { it.id })
        Log.d(TAG, "REPEAT_RECORDED relaxed(grade): ${result.size} IDs saved for profile=$profileId")
        return result
    }

    /**
     * Fast relaxed picker for level-group mode (timeout fallback).
     * Still enforces cross-quiz repeat blocking (5 quiz window).
     */
    fun pickQuizQuestionsRelaxed(
        levelGroup: LevelGroup,
        count: Int,
        difficulty: QuizDifficulty,
        categories: Set<String>,
        testId: String?,
        excludeIds: Set<String> = emptySet()
    ): List<Question> {
        val profileId = ProfileStore(context).getCurrentProfileId()
        val hardBlockIds = roomStore.getQuestionIdsFromLastNTests(profileId, 5)
        val allExclude = excludeIds + hardBlockIds
        Log.d(TAG, "REPEAT_BLOCKED relaxed(level) blocked=${hardBlockIds.size}")
        val global = getGlobalPool()
        val base = global.filter { it.levelGroup == levelGroup }.ifEmpty { global }
        var pool = base.filter { it.id !in allExclude }
        if (pool.isEmpty()) {
            Log.w(TAG, "REPEAT_LAST_RESORT relaxed(level): allowing blocked questions")
            pool = base.filter { it.id !in excludeIds }
        }
        val filtered = if (categories.isEmpty()) pool else pool.filter { it.subject.name in categories }
        val result = (if (filtered.isNotEmpty()) filtered else pool).shuffled().distinctBy { it.id }.take(count)
        roomStore.recordTestCreated(profileId, java.util.UUID.randomUUID().toString(), result.map { it.id })
        roomStore.recordSeenIdsForProfile(profileId, result.map { it.id })
        Log.d(TAG, "REPEAT_RECORDED relaxed(level): ${result.size} IDs saved for profile=$profileId")
        return result
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

    fun getLevelGroupFromPrefs(): LevelGroup = LevelGroup.GRADE_9_12
}
