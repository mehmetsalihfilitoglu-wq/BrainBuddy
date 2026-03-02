package com.brainbuddy.app.quiz

import android.content.Context
import android.util.Log
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.QuizPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

class QuestionRepository(private val context: Context) {

    companion object {
        private const val TAG = "QuestionRepository"
        /** Every test (gate, normal, remedial, boss) has exactly this many questions. */
        const val MIN_QUESTIONS_PER_TEST = 20
    }

    private val historyStore = QuestionHistoryStore(context)
    private val wrongQuestionStore = WrongQuestionStore(context)

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

    fun loadAllQuestionsWithStats(): Pair<List<Question>, LoadStats> {
        val fromAssets = loadFromAssets()
        val fromImported = loadFromImported()
        val merged = (fromAssets + fromImported).distinctBy { it.id }
        val stats = LoadStats(
            fileFound = fromAssets.isNotEmpty() || fromImported.isNotEmpty(),
            totalInJson = fromAssets.size + fromImported.size,
            parsedTotal = merged.size,
            parseFailed = 0
        )
        return Pair(if (merged.isEmpty()) getFallbackQuestions() else merged, stats)
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

    /** Merge and persist imported questions. Returns count of newly added. */
    fun mergeImportedQuestions(arr: JSONArray): Int {
        val current = loadFromImported()
        val existingIds = current.map { it.id }.toSet().toMutableSet()
        val toAdd = ArrayList<Question>()
        for (i in 0 until arr.length()) {
            try {
                val q = parseQuestion(arr.getJSONObject(i))
                if (q.id !in existingIds) {
                    toAdd.add(q)
                    existingIds.add(q.id)
                }
            } catch (_: Exception) { }
        }
        if (toAdd.isEmpty()) return 0
        val merged = current + toAdd
        val jsonArr = JSONArray()
        merged.forEach { q ->
            jsonArr.put(org.json.JSONObject().apply {
                put("id", q.id)
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
        return toAdd.size
    }

    data class LoadStats(
        val fileFound: Boolean,
        val totalInJson: Int,
        val parsedTotal: Int,
        val parseFailed: Int
    )

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
        Question(id = "fb1", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", stem = "12 × 15 işleminin sonucu kaçtır?", choices = listOf("160", "170", "180", "190"), correctIndex = 2, hint = "12×10=120, 12×5=60", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb2", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", stem = "Türkiye'nin başkenti neresidir?", choices = listOf("İstanbul", "İzmir", "Ankara", "Bursa"), correctIndex = 2, hint = "Mustafa Kemal Atatürk'ün kararıyla.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb3", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", stem = "Güneş sisteminde Dünya'dan sonra gelen gezegen hangisidir?", choices = listOf("Venüs", "Mars", "Jüpiter", "Satürn"), correctIndex = 1, hint = "Merkür, Venüs, Dünya, Mars...", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb4", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.ING, gradeTag = "6", stem = "\"Hello\" kelimesinin Türkçe karşılığı nedir?", choices = listOf("Hoşça kal", "Merhaba", "Teşekkürler", "Evet"), correctIndex = 1, hint = "Selamlama sözcüğü.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb5", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.SOSYAL, gradeTag = "7", stem = "Türkiye Cumhuriyeti hangi yıl kurulmuştur?", choices = listOf("1920", "1922", "1923", "1924"), correctIndex = 2, hint = "Lozan Antlaşması sonrası.", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb6", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", stem = "2x + 5 = 15 denkleminde x kaçtır?", choices = listOf("3", "4", "5", "6"), correctIndex = 2, hint = "Önce 5'i karşı tarafa at.", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb7", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", stem = "\"Koşmak\" fiilinin geniş zaman 1. tekil şahıs çekimi hangisidir?", choices = listOf("koşarım", "koşuyorum", "koşar", "koşarsın"), correctIndex = 0, hint = "Geniş zaman -ar/-er eki alır.", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb8", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", stem = "Fotosentez olayında hangi gaz üretilir?", choices = listOf("Karbondioksit", "Azot", "Oksijen", "Hidrojen"), correctIndex = 2, hint = "Bitkiler ışıkta ne üretir?", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb9", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "8", stem = "√64 işleminin sonucu kaçtır?", choices = listOf("6", "7", "8", "9"), correctIndex = 2, hint = "8×8=64", imageAsset = null, difficulty = QuizDifficulty.HARD),
        Question(id = "fb10", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.SOSYAL, gradeTag = "8", stem = "TBMM'nin açılış tarihi nedir?", choices = listOf("19 Mayıs 1919", "23 Nisan 1920", "30 Ağustos 1922", "29 Ekim 1923"), correctIndex = 1, hint = "Ulusal Egemenlik ve Çocuk Bayramı.", imageAsset = null, difficulty = QuizDifficulty.HARD),
        // Seed questions for development (30+ across 3 topics)
        Question(id = "fb11", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", stem = "18 + 27 = ?", choices = listOf("43", "44", "45", "46"), correctIndex = 2, hint = "8+7=15", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb12", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", stem = "56 − 29 = ?", choices = listOf("25", "26", "27", "28"), correctIndex = 2, hint = "Borrow from tens", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb13", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", stem = "7 × 8 = ?", choices = listOf("54", "55", "56", "58"), correctIndex = 2, hint = "7×8=56", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb14", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", stem = "72 ÷ 9 = ?", choices = listOf("6", "7", "8", "9"), correctIndex = 2, hint = "9×8=72", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb15", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "8", stem = "Bir üçgenin iç açıları toplamı?", choices = listOf("90°", "180°", "270°", "360°"), correctIndex = 1, hint = "Tüm üçgenlerde geçerli", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb16", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", stem = "1 km kaç metredir?", choices = listOf("10", "100", "500", "1000"), correctIndex = 3, hint = "kilo=1000", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb17", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", stem = "25 × 4 = ?", choices = listOf("90", "95", "100", "105"), correctIndex = 2, hint = "25×4=100", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb18", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "8", stem = "15² kaçtır?", choices = listOf("200", "215", "225", "250"), correctIndex = 2, hint = "15×15", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb19", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "6", stem = "144 ÷ 12 = ?", choices = listOf("10", "11", "12", "13"), correctIndex = 2, hint = "12×12=144", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb20", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.MAT, gradeTag = "7", stem = "0,5 kesir olarak?", choices = listOf("1/3", "1/4", "1/2", "2/3"), correctIndex = 2, hint = "5/10=1/2", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb21", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", stem = "Cümle sonuna hangi noktalama konur?", choices = listOf("Virgül", "Nokta", "Ünlem", "Soru işareti"), correctIndex = 1, hint = "Cümle biter", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb22", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", stem = "Ev kelimesinin çoğul hali?", choices = listOf("evler", "evlar", "evs", "evden"), correctIndex = 0, hint = "-ler/-lar eki", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb23", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", stem = "Özel isim örneği?", choices = listOf("ev", "Ankara", "büyük", "koşmak"), correctIndex = 1, hint = "Yer adları özel isimdir", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb24", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", stem = "Yüklem hangi soru ile bulunur?", choices = listOf("Kim?", "Ne yapıyor?", "Nerede?", "Nasıl?"), correctIndex = 1, hint = "Eylemi bildirir", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb25", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "8", stem = "Deyim nedir?", choices = listOf("Gerçek anlamlı söz", "Kalıplaşmış mecazlı söz", "Yabancı kelime", "Eski kelime"), correctIndex = 1, hint = "Göz açıp kapayıncaya kadar", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb26", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", stem = "Okul kelimesi kaç hecelidir?", choices = listOf("1", "2", "3", "4"), correctIndex = 1, hint = "O-kul", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb27", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", stem = "Güzel sıfatının zıt anlamlısı?", choices = listOf("İyi", "Çirkin", "Büyük", "Küçük"), correctIndex = 1, hint = "Görünüm", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb28", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "8", stem = "Yazım ve imla ilişkisi?", choices = listOf("Zıt", "Eş anlamlı", "Yakın", "Eş sesli"), correctIndex = 1, hint = "Aynı anlam", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb29", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "6", stem = "Büyük kelimesinin zıttı?", choices = listOf("Geniş", "Küçük", "Uzun", "Kısa"), correctIndex = 1, hint = "Boyut", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb30", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.TURKCE, gradeTag = "7", stem = "Atatürk özel isim midir?", choices = listOf("Evet", "Hayır", "Bazen", "Belirsiz"), correctIndex = 0, hint = "Kişi adları özeldir", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb31", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", stem = "Hücrenin yönetim merkezi?", choices = listOf("Sitoplazma", "Çekirdek", "Hücre zarı", "Mitokondri"), correctIndex = 1, hint = "DNA burada", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb32", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", stem = "Canlıların temel yapı taşı?", choices = listOf("Organ", "Doku", "Hücre", "Sistem"), correctIndex = 2, hint = "En küçük birim", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb33", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", stem = "Fotosentezde üretilen gaz?", choices = listOf("CO2", "Azot", "Oksijen", "Hidrojen"), correctIndex = 2, hint = "Bitkiler ne üretir?", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb34", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", stem = "Güneş ne tür gök cismidir?", choices = listOf("Gezegen", "Uydu", "Yıldız", "Asteroid"), correctIndex = 2, hint = "Işık yayar", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb35", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "8", stem = "Suyun formülü?", choices = listOf("CO2", "NaCl", "H2O", "O2"), correctIndex = 2, hint = "Hidrojen ve oksijen", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb36", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", stem = "Dünya'nın uydusu?", choices = listOf("Mars", "Venüs", "Ay", "Güneş"), correctIndex = 2, hint = "Geceleri görünür", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb37", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", stem = "Kuvvetin birimi?", choices = listOf("Metre", "Newton", "Saniye", "Kilogram"), correctIndex = 1, hint = "N ile gösterilir", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb38", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "8", stem = "Saf suyun kaynama noktası (°C)?", choices = listOf("90", "95", "100", "105"), correctIndex = 2, hint = "Deniz seviyesi", imageAsset = null, difficulty = QuizDifficulty.MEDIUM),
        Question(id = "fb39", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "6", stem = "Maddenin halleri?", choices = listOf("Katı, sıvı, gaz", "Ateş, su, toprak", "Kök, gövde, yaprak", "Hücre, doku, organ"), correctIndex = 0, hint = "Fiziksel haller", imageAsset = null, difficulty = QuizDifficulty.EASY),
        Question(id = "fb40", levelGroup = LevelGroup.GRADE_5_8, subject = Subject.FEN, gradeTag = "7", stem = "Mıknatıs hangi metali çeker?", choices = listOf("Bakır", "Demir", "Alüminyum", "Altın"), correctIndex = 1, hint = "Demir, nikel, kobalt", imageAsset = null, difficulty = QuizDifficulty.EASY)
    )

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
            QuizDifficulty.valueOf(diffStr)
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
        return Question(
            id = o.optString("id", "q_${System.currentTimeMillis()}"),
            levelGroup = levelGroup,
            subject = subject,
            gradeTag = o.optString("gradeTag", ""),
            stem = o.optString("stem", "?"),
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
    fun recordAnswers(answers: List<AnswerRecord>, questionsMap: Map<String, Question>? = null) {
        answers.forEach { a ->
            historyStore.recordAnswer(a.questionId, a.isCorrect)
            val q = questionsMap?.get(a.questionId)
            if (a.isCorrect) {
                wrongQuestionStore.markFixed(a.questionId)
            } else {
                wrongQuestionStore.recordWrong(a.questionId, q?.subject?.tr ?: "Diğer")
            }
        }
    }

    /**
     * Smart selection for quiz session:
     * 1) Build pool with progressive filter relaxation (category → difficulty → levelGroup)
     * 2) Prioritize wrong (within 7 days)
     * 3) Avoid recent correct (cooldown ~2 days)
     * 4) Variety: least recently seen
     * 5) Never return empty: fallback to built-in questions if needed
     */
    fun pickQuizQuestions(
        levelGroup: LevelGroup,
        count: Int,
        difficulty: QuizDifficulty,
        categories: Set<String> = emptySet()
    ): List<Question> {
        val (all, _) = loadAllQuestionsWithStats()
        val allPool = if (all.isEmpty()) getFallbackQuestions() else all
        val (pool, _) = buildPoolWithFallback(allPool, levelGroup, difficulty, categories)
        val finalPool = if (pool.isEmpty()) {
            Log.w(TAG, "Pool empty after filters, using global/fallback questions")
            allPool.ifEmpty { getFallbackQuestions() }
        } else pool

        val profileId = ProfileStore(context).getCurrentProfileId()
        val wrongIds = (historyStore.getWrongQuestionIds(7) + wrongQuestionStore.getUnfixedWrongIds(14)).toSet()
        val now = System.currentTimeMillis()
        val cooldownMs = TimeUnit.DAYS.toMillis(2)

        val recentIds = historyStore.getRecentlySeenIdsForProfile(profileId, 100)
        val wrongPool = finalPool.filter { it.id in wrongIds }
        val cooldownExcluded = finalPool.filter { q ->
            val h = historyStore.getHistory(q.id) ?: return@filter true
            if (h.lastResult != "correct") return@filter true
            (now - h.lastSeenAt) < cooldownMs
        }
        val available = finalPool.filter { it !in cooldownExcluded }
        val topicCap = (count / 3).coerceAtLeast(1)
        val selected = mutableSetOf<String>()
        val result = ArrayList<Question>()
        val topicCount = mutableMapOf<String, Int>()

        fun canAdd(q: Question): Boolean {
            if (q.id in selected) return false
            val topic = q.subject.tr
            if ((topicCount[topic] ?: 0) >= topicCap) return false
            return true
        }

        // 1) Add wrong questions first (adaptive: weak topics)
        wrongPool.shuffled().forEach { q ->
            if (result.size >= count) return@forEach
            if (canAdd(q)) {
                result.add(q)
                selected.add(q.id)
                topicCount[q.subject.tr] = (topicCount[q.subject.tr] ?: 0) + 1
            }
        }

        // 2) Fill with least-recently-seen, prefer non-recent
        var rest = available.filter { it.id !in selected }
        if (rest.size > count) rest = rest.filter { it.id !in recentIds }.ifEmpty { rest }
        rest = rest.sortedBy { historyStore.getHistory(it.id)?.lastSeenAt ?: 0L }
        rest.forEach { q ->
            if (result.size >= count) return@forEach
            if (canAdd(q) && (q.id !in recentIds || available.size < count * 2)) {
                result.add(q)
                selected.add(q.id)
                topicCount[q.subject.tr] = (topicCount[q.subject.tr] ?: 0) + 1
            }
        }

        // 3) If still empty (e.g. all in cooldown, no wrong), ignore cooldown and use pool
        if (result.isEmpty() && finalPool.isNotEmpty()) {
            Log.i(TAG, "Result empty after selection, using pool (ignoring cooldown)")
            val fallback = finalPool.shuffled().take(count)
            recordSeenForQuiz(profileId, fallback.map { it.id })
            return fallback
        }

        var finalResult = result.shuffled()
        if (finalResult.size < count) {
            val fillPool = finalPool.shuffled()
            var idx = 0
            while (finalResult.size < count && fillPool.isNotEmpty()) {
                finalResult = (finalResult + fillPool[idx % fillPool.size]).toMutableList()
                idx++
            }
            finalResult = finalResult.take(count).shuffled()
        }
        recordSeenForQuiz(profileId, finalResult.map { it.id })
        return finalResult
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
        val wrongIds = weakTopicIds.ifEmpty { historyStore.getWrongQuestionIds(14).toList() }
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
        val recentIds = historyStore.getRecentlySeenIdsForProfile(profileId, 100)
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
        val wrongIds = historyStore.getAllWrongIds()
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
        val recentIds = historyStore.getRecentlySeenIdsForProfile(profileId, 50)
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
        historyStore.recordSeenIdsForProfile(profileId, toReturn.map { it.id })
        return toReturn
    }

    /** Records seen IDs for pickQuizQuestions per-user variety (avoids repeat across attempts). */
    fun recordSeenForQuiz(profileId: String, questionIds: List<String>) {
        historyStore.recordSeenIdsForProfile(profileId, questionIds)
    }

    fun getLevelGroupFromPrefs(): LevelGroup {
        return when (ProtectionPrefs(context).studentLevel()) {
            com.brainbuddy.app.core.StudentLevel.AGE_3_5 -> LevelGroup.AGE_3_5
            com.brainbuddy.app.core.StudentLevel.GRADES_1_4 -> LevelGroup.GRADE_1_4
            com.brainbuddy.app.core.StudentLevel.GRADES_5_8 -> LevelGroup.GRADE_5_8
            com.brainbuddy.app.core.StudentLevel.GRADES_9_12 -> LevelGroup.GRADE_9_12
        }
    }
}
