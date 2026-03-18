package com.brainbuddy.app.db

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.brainbuddy.app.quiz.QuestionQualityGate
import com.brainbuddy.app.quiz.QuestionDiversity
import com.brainbuddy.app.quiz.Subject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.nio.charset.Charset

/**
 * Seeds Room DB from JSON on first install.
 * Idempotent: app_meta["db_seeded"] == "true" skips.
 */
object DbSeeder {
    private const val TAG = "DbSeeder"
    private const val KEY_DB_SEEDED = "db_seeded"
    private const val KEY_DB_SEED_VERSION = "db_seed_version"
    private const val CURRENT_DB_SEED_VERSION = 3
    private const val TARGET_QUESTIONS_PER_SUBJECT = 500
    private const val MIN_REASONABLE_DB_COUNT = 8000

    /** Pack asset name pattern: grade{G}_{subject}.json under assets/packs (and subdirs). */
    private val PACK_FILE_REGEX = Regex(
        pattern = "^grade(1|2|3|4|5|6|7)_(mat|turkce|fen|sosyal|ing)\\.json$",
        option = RegexOption.IGNORE_CASE
    )

    /** Root-level GENERAL question JSON files (array or wrapped { "questions": [] }). */
    private val ROOT_GENERAL_QUESTION_FILES = listOf("questions_tr.json", "import_template.json")

    /** Desteklenen ders anahtarları (DB'ye bu kısa kodlarla yazılır). */
    private val SUBJECT_KEYS = listOf("mat", "turkce", "fen", "sosyal", "ing")

    suspend fun seedIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        Log.i(TAG, "seedIfNeeded entered (CURRENT_DB_SEED_VERSION=$CURRENT_DB_SEED_VERSION)")
        val db = DatabaseProvider.get(context)
        val meta = db.appMetaDao()
        val questionDao = db.questionDao()

        // Versioned seeding: allows safe re-import when packs/assets grow.
        val storedVersionStr = meta.get(KEY_DB_SEED_VERSION)
        val legacySeededFlag = meta.get(KEY_DB_SEEDED)
        val storedVersion = when {
            storedVersionStr != null -> storedVersionStr.toIntOrNull() ?: 0
            legacySeededFlag == "true" -> 1 // previous apps that only had boolean flag
            else -> 0
        }
        Log.i(TAG, "seedIfNeeded stored db_seed_version=$storedVersionStr legacySeeded=$legacySeededFlag computedStoredVersion=$storedVersion skip=${storedVersion >= CURRENT_DB_SEED_VERSION}")
        val count = try { questionDao.countAll() } catch (_: Exception) { 0 }
        if (storedVersion >= CURRENT_DB_SEED_VERSION && count > 0) {
            // Critical recovery path: older buggy builds may have marked seed_version as up-to-date
            // while only inserting a small subset of the asset pool. Never clear the DB here;
            // instead, safely "top up" by inserting any missing IDs (INSERT IGNORE).
            if (count < MIN_REASONABLE_DB_COUNT) {
                Log.w(TAG, "Seed recovery: DB count seems too low (count=$count, version=$storedVersion). Will top-up seed safely.")
                val toInsert = buildSeedQuestions(context)
                if (toInsert.isEmpty()) {
                    Log.e(TAG, "Seed recovery aborted: loaded=0, preserving existing DB (count=$count)")
                    return@withContext false
                }
                val before = count
                try {
                    questionDao.insertAllIgnore(toInsert)
                    val after = questionDao.countAll()
                    meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
                    meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
                    Log.i(TAG, "Seed recovery complete: attempted=${toInsert.size} DB before=$before after=$after (added=${after - before})")
                    return@withContext (after > before)
                } catch (e: Exception) {
                    Log.e(TAG, "Seed recovery failed; existing DB preserved", e)
                    return@withContext false
                }
            }
            Log.d(TAG, "Seed already up to date (version=$storedVersion), skip")
            return@withContext false
        }
        if (storedVersion >= CURRENT_DB_SEED_VERSION && count == 0) {
            Log.d(TAG, "Seed forced because DB is empty (version=$storedVersion)")
        }

        Log.i(TAG, "performSeed will run (storedVersion=$storedVersion < $CURRENT_DB_SEED_VERSION)")
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

        // SAFE/transactional: load first, only then replace DB.
        val toInsert = buildSeedQuestions(context)
        if (toInsert.isEmpty()) {
            Log.e(TAG, "Force reseed aborted: loaded=0, preserving existing DB")
            return@withContext false
        }

        // Version alanlarını güncel sürüme çek – böylece sonraki açılışlarda tekrar seedIfNeeded tetiklenmez.
        meta.set(AppMetaEntity(KEY_DB_SEEDED, "false"))
        meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, "0"))
        return@withContext try {
            db.withTransaction {
                questionDao.deleteAll()
                // Use REPLACE so the final DB exactly matches the loaded seed set.
                questionDao.insertAll(toInsert)
                meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
                meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
            }
            Log.i(TAG, "Force reseed successful: inserted=${toInsert.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Force reseed failed, old database may be preserved", e)
            false
        }
    }

    /**
     * DEBUG: Sadece GENERAL havuzunu temizleyip yeniden seed eder; LGS soruları korunur.
     * Root GENERAL dosyaları, packs/ ve lgs_import/ altındaki sınıf paketleri (GENERAL) yeniden yüklenir.
     */
    suspend fun forceReseedGeneralBanks(context: Context): Boolean = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(context)
        val meta = db.appMetaDao()
        val questionDao = db.questionDao()
        // SAFE/transactional: load first, only then replace GENERAL rows.
        val toInsert = buildSeedQuestions(context)
        if (toInsert.isEmpty()) {
            Log.e(TAG, "Force reseed GENERAL aborted: loaded=0, preserving existing DB")
            return@withContext false
        }
        meta.set(AppMetaEntity(KEY_DB_SEEDED, "false"))
        meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, "0"))
        return@withContext try {
            db.withTransaction {
                // Keep LGS rows intact; replace only GENERAL.
                questionDao.deleteGeneralQuestions()
                questionDao.insertAll(toInsert.filter { (it.examType ?: "GENERAL") != "LGS" })
                meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
                meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
            }
            Log.i(TAG, "Force reseed GENERAL successful: inserted=${toInsert.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Force reseed GENERAL failed, old database preserved", e)
            false
        }
    }

    private fun buildSeedQuestions(context: Context): List<QuestionEntity> {
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
        // Dedup should only drop true duplicates.
        // Many banks intentionally reuse the same stem with different options; dedup on stem alone
        // destroys the pool. Use (grade, subject, exactStem, options, answer) as key.
        return questions.distinctBy(::dedupKey)
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
        Log.i(TAG, "performSeed started")
        val questions = mutableListOf<QuestionEntity>()
        try {
            val fromAssets = loadFromAssets(context)
            questions.addAll(fromAssets)
            val imported = loadFromImported(context)
            val existingIds = questions.map { it.id }.toSet()
            imported.filter { it.id !in existingIds }.forEach { questions.add(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Seed load error", e)
        }
        if (questions.isEmpty()) {
            Log.w(TAG, "performSeed: no questions from assets/imported, adding fallback entities")
            questions.addAll(getFallbackEntities())
        }

        Log.i(TAG, "Seed load complete: ${questions.size} questions from assets+imported before dedup")

        // (grade, subject, exactStem+options+answer) dedup: only drop true duplicates.
        val dedupedList = questions.distinctBy(::dedupKey)
        if (dedupedList.size < questions.size) {
            Log.i(TAG, "Seed dedup: ${questions.size} -> ${dedupedList.size} (dropped ${questions.size - dedupedList.size} in-batch duplicates)")
        }

        val questionDao = db.questionDao()
        val countBefore = questionDao.countAll()
        Log.i(TAG, "performSeed: inserting dedupedList.size=${dedupedList.size} DB countBefore=$countBefore")
        questionDao.insertAllIgnore(dedupedList)
        val countAfter = questionDao.countAll()
        meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
        meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
        Log.i(TAG, "performSeed done: inserted batch=${dedupedList.size} DB total before=$countBefore after=$countAfter (seed complete)")

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

        // 1) Root-level GENERAL question files (array or wrapped { "questions": [], "grade", "subject" }).
        for (assetName in ROOT_GENERAL_QUESTION_FILES) {
            try {
                val json = context.assets.open(assetName).use { input ->
                    input.readBytes().toString(Charset.forName("UTF-8"))
                }
                val added = parseRootQuestionFile(assetName, json)
                all += added
                Log.i(TAG, "Loaded root general file: $assetName (${added.size} questions)")
            } catch (e: Exception) {
                Log.w(TAG, "Root file $assetName error: ${e.message}")
            }
        }
        val rootCount = all.size

        // 2) Grade 1..8 × subject pack JSONs under assets/packs (recursive, GENERAL).
        val packFiles = discoverPackAssetFilesRecursive(context)
        if (packFiles.isNotEmpty()) {
            Log.i(TAG, "Discovered ${packFiles.size} pack assets")
        }
        // packs/ is optional; absence is fine (grade_based & lgs_exam are the main pools).
        packFiles.forEach { assetPath ->
            try {
                val json = context.assets.open(assetPath).use { input ->
                    input.readBytes().toString(Charset.forName("UTF-8"))
                }
                val parsed = parsePackFileContent(assetPath, json)
                all += parsed
                if (parsed.isNotEmpty()) Log.i(TAG, "Loaded pack $assetPath: ${parsed.size} questions")
            } catch (e: Exception) {
                Log.w(TAG, "Pack load error for $assetPath: ${e.message}")
            }
        }
        val packCount = all.size - rootCount

        // 3) Grade-based packs under assets/grade_based/** (GENERAL).
        //    Bunlar MEB müfredatına göre 1–7. sınıf ders paketi olup normal GENERAL havuzunda görünmelidir.
        try {
            val lgsGradePacks = loadFromLgsGradePacksAsGeneral(context)
            if (lgsGradePacks.isNotEmpty()) {
                Log.i(TAG, "Loaded ${lgsGradePacks.size} questions from grade_based/* grade packs as GENERAL")
                all += lgsGradePacks
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load grade-based packs from grade_based as GENERAL: ${e.message}")
        }
        val lgsImportCount = all.size - rootCount - packCount

        // 4) LGS-root subject dirs under assets/lgs_exam/{mat,fen,turkce,din,english,inkilap} (recursive).
        // These are NOT grade banks; they are treated as LGS-root mode only.
        try {
            val lgsRootDirs = loadFromLgsRootSubjectDirsAsLgs(context)
            if (lgsRootDirs.isNotEmpty()) {
                Log.i(TAG, "Loaded ${lgsRootDirs.size} questions from lgs_exam/* root subject dirs as LGS")
                all += lgsRootDirs
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load root subject dirs from lgs_exam: ${e.message}")
        }
        val lgsImportRootDirsCount = all.size - rootCount - packCount - lgsImportCount

        // 5) Programmatically üretilen 6. sınıf genişletme paketleri.
        // Pack dosyalarında yeterli soru varsa (>= TARGET_QUESTIONS_PER_SUBJECT) atlanır.
        val existingIds = all.map { it.id }.toMutableSet()
        val g6Counts = all.filter { it.grade == 6 }.groupBy { it.subject }.mapValues { it.value.size }
        val needSynthetic = SUBJECT_KEYS.any { (g6Counts[it] ?: 0) < TARGET_QUESTIONS_PER_SUBJECT }
        if (needSynthetic) {
            all += generateGrade6SyntheticQuestions(existingIds)
        } else {
            Log.i(TAG, "Grade 6 packs have sufficient questions (>= $TARGET_QUESTIONS_PER_SUBJECT per subject), skip synthetic")
        }
        val syntheticCount = all.size - rootCount - packCount - lgsImportCount - lgsImportRootDirsCount

        Log.i(TAG, "loadBySource: root=$rootCount packs=$packCount grade_based_gradePacks=$lgsImportCount lgs_exam_rootDirs(LGS)=$lgsImportRootDirsCount synthetic=$syntheticCount total=${all.size}")
        return all
    }

    /**
     * Recursively loads JSON question files under root subject folders:
     * assets/lgs_import/{mat,fen,turkce,din,english,inkilap}/**/*.json
     *
     * Grade safety:
     * - grade is inferred from path if possible (e.g. ".../fen7/..." -> 7)
     * - if missing/unknown, defaults to 6 (never 8)
     */
    /**
     * LGS-root mode loader (non-grade).
     *
     * Root subject folders (no trailing digit) are treated as LGS-only:
     * - lgs_exam/mat
     * - lgs_exam/fen
     * - lgs_exam/turkce
     * - lgs_exam/din
     * - lgs_exam/english
     * - lgs_exam/inkilap
     *
     * Grade is NOT inferred from solving bank logic here. We store grade=7 as a safe in-range placeholder
     * because the LGS picker relies on examType='LGS' rather than grade filtering.
     */
    private fun loadFromLgsRootSubjectDirsAsLgs(context: Context): List<QuestionEntity> {
        val assets = context.assets
        val rootFolders = listOf("mat", "fen", "turkce", "din", "english", "inkilap")
        val out = mutableListOf<QuestionEntity>()

        for (folder in rootFolders) {
            val rootPath = "lgs_exam/$folder"
            val files = discoverJsonAssetFilesRecursive(assets, rootPath)
            var loadedForFolder = 0
            for (assetPath in files) {
                try {
                    val json = assets.open(assetPath).use { input ->
                        input.readBytes().toString(Charset.forName("UTF-8"))
                    }
                    val subjectKey = when (folder.lowercase()) {
                        "english" -> "ing"
                        else -> folder.lowercase()
                    }
                    val lgsGradePlaceholder = 7

                    val trimmed = json.trimStart()
                    val entities: List<QuestionEntity> = when {
                        trimmed.startsWith("{") -> {
                            val root = JSONObject(json)
                            val arr = root.optJSONArray("questions") ?: JSONArray()
                            parseWrappedQuestionArrayStrict(root, arr, assetPath, lgsGradePlaceholder, subjectKey)
                        }
                        trimmed.startsWith("[") -> {
                            parseJsonArrayWithDefaultsStrict(JSONArray(json), lgsGradePlaceholder, subjectKey)
                        }
                        else -> emptyList()
                    }
                    if (entities.isNotEmpty()) {
                        // Force LGS-root mode: examType=LGS; grade must be 1..7 (use placeholder).
                        out += entities.map { it.copy(examType = "LGS", grade = lgsGradePlaceholder) }
                        loadedForFolder += entities.size
                    }
                } catch (_: Exception) {
                    // ignore individual file errors
                }
            }
            if (loadedForFolder > 0) Log.i(TAG, "Loaded lgs_exam/$folder root: $loadedForFolder questions")
        }

        return out
    }

    private fun discoverJsonAssetFilesRecursive(
        assets: android.content.res.AssetManager,
        rootPath: String
    ): List<String> {
        val out = mutableListOf<String>()
        try {
            collectJsonPaths(assets, rootPath, out)
            out.sort()
        } catch (_: Exception) {}
        return out
    }

    private fun collectJsonPaths(
        assets: android.content.res.AssetManager,
        path: String,
        out: MutableList<String>
    ) {
        val names = try { assets.list(path)?.toList().orEmpty() } catch (_: Exception) { emptyList() }
        for (name in names) {
            val childPath = if (path.isEmpty()) name else "$path/$name"
            if (name.endsWith(".json", ignoreCase = true)) {
                out.add(childPath)
            } else {
                val sub = try { assets.list(childPath) } catch (_: Exception) { null }
                if (!sub.isNullOrEmpty()) {
                    collectJsonPaths(assets, childPath, out)
                }
            }
        }
    }

    private fun deriveGradeFromAssetPath(assetPath: String): Int? {
        val segments = assetPath.split('/', '\\').filter { it.isNotBlank() }
        val regex = Regex("(?i)(?:grade)?([1-7])")
        for (seg in segments.asReversed()) {
            val m = regex.find(seg) ?: continue
            return m.groupValues.getOrNull(1)?.toIntOrNull()
        }
        return null
    }

    /**
     * Reclassifies certain grade-based JSON packs that physically live under assets/lgs_import/
     * (grade-based subdirs) into the GENERAL (non-LGS) question pool.
     *
     * Bu dizinler isim ve raporlara göre sınıf bazlı (2–7. sınıf) müfredat soruları içerir;
     * gerçek LGS (8. sınıf sınav) içeriği değildir. Bu nedenle examType=GENERAL olarak
     * grade-mode havuzuna alınırlar.
     *
     * Desteklenen dizinler:
     * - lgs_import/hayat2, hayat3
     * - lgs_import/fen3..7
     * - lgs_import/mat1..5
     * - lgs_import/turkce1..5, turkce7
     * - lgs_import/english1..7
     * - lgs_import/din4..7
     * - lgs_import/sosyal4..6
     * - lgs_import/inkilap7 (7. sınıf İnkılap)
     *
     * Grade ve subject JSON'da geçerli değilse veya yoksa path'ten türetilir (örn. fen3 -> grade=3, subject=fen).
     */
    private val LGS_PATH_GRADE_SUBJECT_REGEX = Regex("^(mat|turkce|fen|sosyal|english|hayat|din|inkilap)(\\d+)$", RegexOption.IGNORE_CASE)

    /**
     * Derives (grade, subjectKey) from an lgs_import path segment.
     * E.g. "fen3" -> (3, "fen"), "english2" -> (2, "ing"), "inkilap7" -> (7, "inkilap").
     * Returns null if the path does not match a known grade-based folder.
     */
    private fun parseGradeAndSubjectFromLgsPath(assetPath: String): Pair<Int, String>? {
        val segment = assetPath.removePrefix("grade_based/").substringBefore("/")
        val match = LGS_PATH_GRADE_SUBJECT_REGEX.find(segment) ?: return null
        val (subjectPart, gradePart) = match.destructured
        val grade = gradePart.toIntOrNull()?.coerceIn(1, 8) ?: return null
        val subjectKey = when (subjectPart.lowercase()) {
            "mat" -> "mat"
            "turkce" -> "turkce"
            "fen" -> "fen"
            "sosyal" -> "sosyal"
            "english" -> "ing"
            "hayat" -> "hayat"
            "din" -> "din"
            "inkilap" -> "inkilap"
            else -> return null
        }
        return grade to subjectKey
    }

    private fun loadFromLgsGradePacksAsGeneral(context: Context): List<QuestionEntity> {
        val assets = context.assets
        val out = mutableListOf<QuestionEntity>()

        // Auto-discover: assets/grade_based/{subject}{1..7}/
        // Only folders matching the allowed pattern are scanned; nothing else is treated as grade banks.
        val allowed = Regex("^(hayat|mat|fen|turkce|english|din|sosyal|inkilap)[1-7]$", RegexOption.IGNORE_CASE)
        val gradeBasedDirs = try {
            assets.list("grade_based")
                ?.filter { allowed.matches(it) }
                ?.sorted()
                ?.map { "grade_based/$it" }
                .orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Asset list failed for grade_based/: ${e.message}")
            emptyList()
        }

        for (dir in gradeBasedDirs) {
            val fileNames = try {
                assets.list(dir)?.filter { it.endsWith(".json", ignoreCase = true) }?.sorted()
            } catch (e: Exception) {
                Log.w(TAG, "Asset list failed for $dir: ${e.message}")
                null
            } ?: continue

            for (fileName in fileNames) {
                val assetPath = "$dir/$fileName"
                val pathDerived = parseGradeAndSubjectFromLgsPath(assetPath)
                val (pathGrade, pathSubject) = pathDerived ?: (6 to "mat")
                try {
                    val json = assets.open(assetPath).use { input ->
                        input.readBytes().toString(Charset.forName("UTF-8"))
                    }
                    val trimmed = json.trimStart()
                    val entities: List<QuestionEntity> = when {
                        // Wrapped LGS-style format: { version, mode, subject, publisher, questions: [...] }
                        trimmed.startsWith("{") -> {
                            val root = org.json.JSONObject(json)
                            val arr = root.optJSONArray("questions") ?: org.json.JSONArray()
                            parseWrappedQuestionArray(root, arr, assetPath, pathGrade, pathSubject)
                        }
                        // Saf dizi: path'ten türetilen grade/subject ile parse et.
                        trimmed.startsWith("[") -> {
                            parseJsonArrayWithDefaults(org.json.JSONArray(json), pathGrade, pathSubject)
                        }
                        else -> emptyList()
                    }
                    if (entities.isNotEmpty()) {
                        out += entities
                        Log.i(TAG, "Loaded $assetPath as GENERAL pack: ${entities.size} questions (grade=$pathGrade subject=$pathSubject)")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load $assetPath as GENERAL: ${e.message}")
                }
            }
        }

        return out
    }

    /**
     * Recursively discovers pack JSON files under assets/packs.
     * Matches filename grade{G}_{subject}.json (G ∈ 1..7, subject ∈ mat,turkce,fen,sosyal,ing).
     */
    private fun discoverPackAssetFilesRecursive(context: Context): List<String> {
        val out = mutableListOf<String>()
        try {
            collectPackPaths(context.assets, "packs", out)
            out.sort()
        } catch (e: Exception) {
            Log.w(TAG, "Pack asset discovery failed: ${e.message}")
        }
        return out
    }

    private fun collectPackPaths(assets: android.content.res.AssetManager, path: String, out: MutableList<String>) {
        val names = assets.list(path)?.toList().orEmpty()
        for (name in names) {
            val childPath = if (path.isEmpty()) name else "$path/$name"
            if (PACK_FILE_REGEX.matches(name)) {
                out.add(childPath)
            } else {
                // Recurse into subdirs (list() on a dir returns non-null; on a file returns null/empty).
                val sub = assets.list(childPath)
                if (!sub.isNullOrEmpty()) {
                    for (subName in sub) collectPackPaths(assets, "$childPath/$subName", out)
                }
            }
        }
    }

    /** Parse root-level file: either a JSON array or wrapped { "questions": [], "grade", "subject" }. */
    private fun parseRootQuestionFile(assetName: String, json: String): List<QuestionEntity> {
        val trimmed = json.trimStart()
        return when {
            trimmed.startsWith("[") -> parseJsonArray(org.json.JSONArray(json))
            trimmed.startsWith("{") -> {
                val root = org.json.JSONObject(json)
                val arr = root.optJSONArray("questions") ?: return emptyList()
                parseWrappedQuestionArray(root, arr, assetName)
            }
            else -> emptyList()
        }
    }

    /** Parse pack file content: array or wrapped object. grade/subject are forced from filename. */
    private fun parsePackFileContent(assetPath: String, json: String): List<QuestionEntity> {
        val fileName = assetPath.substringAfterLast('/')
        val derived = deriveGradeAndSubjectFromPackFilename(fileName) ?: return emptyList()
        val (forcedGrade, forcedSubject) = derived
        val trimmed = json.trimStart()
        return when {
            trimmed.startsWith("[") -> parsePackJsonArray(org.json.JSONArray(json), forcedGrade, forcedSubject, assetPath)
            trimmed.startsWith("{") -> {
                val root = org.json.JSONObject(json)
                val arr = root.optJSONArray("questions") ?: return emptyList()
                parsePackWrappedQuestions(arr, forcedGrade, forcedSubject, assetPath)
            }
            else -> emptyList()
        }
    }

    private fun deriveGradeAndSubjectFromPackFilename(fileName: String): Pair<Int, String>? {
        val m = PACK_FILE_REGEX.find(fileName) ?: return null
        val grade = m.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val subject = m.groupValues.getOrNull(2)?.lowercase() ?: return null
        return grade to subject
    }

    /**
     * Pack parsing: force grade/subject from filename. Do NOT trust JSON grade/subject inside packs.
     * Ensures questions stay in the GENERAL pool (grades 1..7).
     */
    private fun parsePackJsonArray(arr: JSONArray, forcedGrade: Int, forcedSubject: String, sourceLabel: String): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val combined = org.json.JSONObject(o.toString())
                combined.put("grade", forcedGrade)
                combined.put("subject", forcedSubject)
                if (!combined.has("examType")) combined.put("examType", "GENERAL")
                out.add(parseQuestionObject(combined, i))
            } catch (e: Exception) {
                Log.w(TAG, "Pack parse failed $sourceLabel index $i: ${e.message}")
            }
        }
        return out
    }

    private fun parsePackWrappedQuestions(arr: JSONArray, forcedGrade: Int, forcedSubject: String, sourceLabel: String): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val q = arr.getJSONObject(i)
                val combined = org.json.JSONObject(q.toString())
                combined.put("grade", forcedGrade)
                combined.put("subject", forcedSubject)
                if (!combined.has("examType")) combined.put("examType", "GENERAL")
                out.add(parseQuestionObject(combined, i))
            } catch (e: Exception) {
                Log.w(TAG, "Pack parse failed $sourceLabel index $i: ${e.message}")
            }
        }
        return out
    }

    /**
     * Parse questions array from wrapped format; inject grade/subject into each item.
     * When pathGrade/pathSubject are provided (e.g. from lgs_import/fen3/), they override root when
     * JSON grade/subject are missing or invalid (so grade 1–7 is correct for grade-based banks).
     */
    private fun parseWrappedQuestionArray(
        root: org.json.JSONObject,
        arr: org.json.JSONArray,
        sourceLabel: String,
        pathGrade: Int? = null,
        pathSubject: String? = null
    ): List<QuestionEntity> {
        val rootGrade = root.optInt("grade", 6).coerceIn(1, 8)
        val rootSubject = root.optString("subject", "mat").trim().lowercase()
        val defaultGrade = pathGrade?.takeIf { it in 1..8 } ?: rootGrade
        val defaultSubject = pathSubject?.takeIf { it.isNotBlank() } ?: rootSubject
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val q = arr.getJSONObject(i)
                val combined = org.json.JSONObject(q.toString())
                val qGrade = q.optInt("grade", -1)
                val qSubject = q.optString("subject", "").trim().lowercase()
                combined.put("grade", if (qGrade in 1..8) qGrade else defaultGrade)
                combined.put("subject", if (qSubject.isNotBlank()) qSubject else defaultSubject)
                out.add(parseQuestionObject(combined, i))
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed $sourceLabel index $i: ${e.message}")
            }
        }
        return out
    }

    /** Parse JSON array when each item may lack grade/subject; use path-derived defaults. */
    private fun parseJsonArrayWithDefaults(arr: JSONArray, defaultGrade: Int, defaultSubject: String): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val combined = org.json.JSONObject(o.toString())
                val qGrade = o.optInt("grade", -1)
                val qSubject = o.optString("subject", "").trim().lowercase()
                combined.put("grade", if (qGrade in 1..8) qGrade else defaultGrade)
                combined.put("subject", if (qSubject.isNotBlank()) qSubject else defaultSubject)
                out.add(parseQuestionObject(combined, i))
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed array index $i: ${e.message}")
            }
        }
        return out
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

    private fun parseJsonArrayWithDefaultsStrict(arr: JSONArray, defaultGrade: Int, defaultSubject: String): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val combined = JSONObject(o.toString())
                val qGrade = o.optInt("grade", -1)
                val qSubject = o.optString("subject", "").trim().lowercase()
                val g = when {
                    qGrade in 1..7 -> qGrade
                    qGrade == 8 -> defaultGrade
                    else -> defaultGrade
                }
                combined.put("grade", g)
                combined.put("subject", if (qSubject.isNotBlank()) qSubject else defaultSubject)
                out.add(parseQuestionObject(combined, i))
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed array index $i: ${e.message}")
            }
        }
        return out
    }

    private fun parseWrappedQuestionArrayStrict(
        root: JSONObject,
        arr: JSONArray,
        sourceLabel: String,
        pathGrade: Int,
        pathSubject: String
    ): List<QuestionEntity> {
        val rootGradeRaw = root.optInt("grade", -1)
        val rootGrade = when {
            rootGradeRaw in 1..7 -> rootGradeRaw
            rootGradeRaw == 8 -> pathGrade
            else -> pathGrade
        }
        val rootSubject = root.optString("subject", "").trim().lowercase().ifBlank { pathSubject }
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val q = arr.getJSONObject(i)
                val combined = JSONObject(q.toString())
                val qGrade = q.optInt("grade", -1)
                val qSubject = q.optString("subject", "").trim().lowercase()

                val chosenGrade = when {
                    qGrade in 1..7 -> qGrade
                    qGrade == 8 -> pathGrade
                    else -> rootGrade
                }
                combined.put("grade", chosenGrade)

                val chosenSubject = when {
                    qSubject.isNotBlank() -> qSubject
                    rootSubject.isNotBlank() -> rootSubject
                    else -> pathSubject
                }
                combined.put("subject", chosenSubject)
                out.add(parseQuestionObject(combined, i))
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed $sourceLabel index $i: ${e.message}")
            }
        }
        return out
    }

    private fun getFallbackEntities(): List<QuestionEntity> {
        return listOf(
            makeFallbackEntity("fb1", 6, "mat", "12 × 15 işleminin sonucu kaçtır?",
                "[\"160\",\"170\",\"180\",\"190\"]", 2, "12×10=120, 12×5=60"),
            makeFallbackEntity("fb2", 6, "turkce", "Türkiye'nin başkenti neresidir?",
                "[\"İstanbul\",\"İzmir\",\"Ankara\",\"Bursa\"]", 2, "Mustafa Kemal Atatürk'ün kararıyla."),
            makeFallbackEntity("fb3", 6, "fen", "Güneş sisteminde Dünya'dan sonra gelen gezegen hangisidir?",
                "[\"Venüs\",\"Mars\",\"Jüpiter\",\"Satürn\"]", 1, "Merkür, Venüs, Dünya, Mars..."),
            makeFallbackEntity("fb4", 6, "ing", "\"Hello\" kelimesinin Türkçe karşılığı nedir?",
                "[\"Hoşça kal\",\"Merhaba\",\"Teşekkürler\",\"Evet\"]", 1, "Selamlama sözcüğü.")
        )
    }

    private fun makeFallbackEntity(
        id: String, grade: Int, subject: String, questionText: String,
        optionsJson: String, answerIndex: Int, explanation: String
    ): QuestionEntity {
        val stemNorm = QuestionStemHash.normalizeStem(questionText)
        val hash = QuestionStemHash.stemHash(questionText)
        return QuestionEntity(
            id = id,
            grade = grade,
            subject = subject,
            difficulty = 1,
            questionText = questionText,
            optionsJson = optionsJson,
            answerIndex = answerIndex,
            explanation = explanation,
            isActive = true,
            version = 1,
            examType = "GENERAL",
            imageAsset = null,
            stemNormalized = stemNorm,
            stemHash = hash
        )
    }

    /**
     * JSON formatı (standart):
     * {
     *   id: String,
     *   grade: Int (1..7),
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
        // App expects 1..7. Never keep grade=8; map missing/invalid safely.
        val gradeFromJson = when {
            o.has("grade") -> o.optInt("grade", 0)
            o.has("grade_level") -> o.optInt("grade_level", 0)
            else -> 0
        }
        val grade = when {
            gradeFromJson in 1..7 -> gradeFromJson
            gradeFromJson == 8 -> 6
            else -> {
                val gradeTagStr = o.optString("gradeTag", o.optString("grade_level", ""))
                val gradeTag = gradeTagStr.toIntOrNull()
                when (gradeTag) {
                    in 1..7 -> gradeTag!!
                    8 -> 6
                    else -> 6
                }
            }
        }

        // subject normalize -> mat/turkce/fen/sosyal/ing/hayat/din/inkilap
        val rawSubject = o.optString("subject", "").trim().lowercase()
        val subjectKey = when (rawSubject) {
            "", "null" -> throw IllegalArgumentException("Missing subject for question index=$index")
            "mat", "matematik", "math" -> "mat"
            "turkce", "türkçe", "tr" -> "turkce"
            "fen", "fen bilimleri" -> "fen"
            "sosyal", "sosyal bilgiler" -> "sosyal"
            "ing", "ingilizce", "ingilizce dersi", "english", "eng" -> "ing"
            "hayat", "hayat bilgisi" -> "hayat"
            "din", "din kültürü", "din kültürü ve ahlak bilgisi" -> "din"
            "inkilap", "inkılap", "tc inkılap tarihi" -> "inkilap"
            else -> throw IllegalArgumentException("Unsupported subject '$rawSubject' at index=$index")
        }

        // question text: stem (yeni şema) veya questionText (eski) + imported alias: "question"
        val questionText = o.optString("stem", "").ifBlank {
            o.optString("questionText", "")
        }.ifBlank {
            o.optString("question", "")
        }.ifBlank {
            throw IllegalArgumentException("Missing questionText/stem at index=$index")
        }

        // Hashes (used for ID fallback + duplicate diagnostics).
        val stemNorm = QuestionStemHash.normalizeStem(questionText)
        val hash = QuestionStemHash.stemHash(questionText)

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
        // Avoid PK collisions across multiple files where index restarts (notably lgs_exam).
        val id = explicitId ?: "${grade}_${subjectKey}_${hash.take(16)}_${(index + 1).toString().padStart(4, '0')}"

        // Kalite gate: düşük kaliteli soruları pasifleştir, deactivationReason sakla.
        val subjectEnum = when (subjectKey) {
            "mat" -> Subject.MAT
            "turkce" -> Subject.TURKCE
            "fen" -> Subject.FEN
            "sosyal" -> Subject.SOSYAL
            "hayat" -> Subject.HAYAT
            "ing" -> Subject.ING
            "din" -> Subject.DIN
            "inkilap" -> Subject.INKILAP
            else -> Subject.MAT
        }
        val gate = QuestionQualityGate.evaluate(
            subject = subjectEnum,
            grade = grade,
            questionText = questionText,
            options = padded,
            difficulty = difficulty
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
            skill = diversitySkill,
            stemNormalized = stemNorm,
            stemHash = hash
        )
    }

    private fun dedupKey(e: QuestionEntity): String {
        val exactStem = QuestionStemHash.normalizeStemExact(e.questionText)
        val payload = buildString {
            append(exactStem)
            append('\n')
            append(e.optionsJson)
            append('\n')
            append(e.answerIndex)
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
        val contentHash = digest.joinToString("") { "%02x".format(it) }
        return "${e.grade}|${e.subject}|$contentHash"
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
            "hayat" -> Subject.HAYAT
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
            options = paddedOptions,
            difficulty = normalizedDifficulty
        )

        val diversityType = QuestionDiversity.inferType(subjectEnum, spec.stem)
        val diversitySkill = QuestionDiversity.inferSkill(subjectEnum, grade, diversityType, spec.stem)

        val stemNorm = QuestionStemHash.normalizeStem(spec.stem)
        val hash = QuestionStemHash.stemHash(spec.stem)

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
            skill = diversitySkill,
            stemNormalized = stemNorm,
            stemHash = hash
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
     * Her grade (1..7) × her subject için COUNT >= TARGET_QUESTIONS_PER_SUBJECT değilse
     * debug log + warning üretir.
     */
    private suspend fun validatePoolCoverage(questionDao: QuestionDao) {
        val counts = questionDao.getCountsByGradeSubject()
        val byKey = counts.associateBy { it.grade to it.subject.lowercase() }

        val shortages = mutableListOf<String>()
        for (grade in 1..7) {
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
            Log.d(TAG, "Question pool OK for all grade+subject combinations (1..7)")
        } else {
            Log.w(TAG, "Question pool has shortages for ${shortages.size} grade+subject combinations. See warnings above for details.")
        }
    }
}
