package com.edumio.app.db

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.edumio.app.quiz.QuestionQualityGate
import com.edumio.app.quiz.TemplateQualityDetector
import com.edumio.app.quiz.QuestionDiversity
import com.edumio.app.quiz.Subject
import com.edumio.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
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
    // Version 8: fix duplicate options in synthetic grade-6 mat MEDIUM questions.
    // When givenAway==2, kept+2==boughtTotal → options[1] and options[2] were identical.
    // Fixed: use kept+3 when kept+2 would equal boughtTotal.
    // Also wires DataIntegrityChecker.runCleanup() into startup (EDUmioApp).
    // Also fixes runQualityProofScan to use case-sensitive distinct() (not lowercase).
    // Version 9: add telegraphed-answer and weak-numeric-distractor rejection gates
    // across importer (parseQuestionObject), DataIntegrityChecker (detectCorruption),
    // and QuizOutputGuard (choicesInvalidReason).
    private const val CURRENT_DB_SEED_VERSION = 9
    private const val TARGET_QUESTIONS_PER_SUBJECT = 500
    private const val MIN_REASONABLE_DB_COUNT = 8000

    /** When true (and DEBUG build), [seedIfNeeded] runs [forceReseed]. Default: never auto-reseed in debug. */
    private const val DEBUG_FORCE_RESEED = false

    private const val SEED_AUDIT_LOG_TAG = "EdumioSeedAudit"

    private val seedIfNeededMutex = Mutex()

    /** Last [seedIfNeeded] outcome (for audit UI). Updated from [logSeedAudit]. */
    @Volatile
    var lastSeedSkipped: Boolean = true
        internal set

    /** Rows inserted in the last [seedIfNeeded] run (delta), best-effort. */
    @Volatile
    var lastInsertedThisRun: Int = 0
        internal set

    /** Tag written to [QuestionEntity.sourcePack] for questions parsed from this asset. */
    const val GRADE6_MAT_SOURCE_PACK = "grade6_mat.json"

    private data class SeedSourceCounts(
        val loaded_root_general: Int,
        val loaded_packs: Int,
        val loaded_grade_based: Int,
        val loaded_lgs_exam: Int,
        val loaded_synthetic: Int
    )

    private var lastSeedDiagnosticsSnapshot: SeedDiagnosticsSnapshot? = null

    /** Last seed run: grade_based/mat6 JSON pipeline (DEBUG PoolStatus). */
    data class Mat6PipelineDiagnostics(
        val generatedMat6Total: Int,
        val parsedMat6Total: Int,
        val mat6DedupDropped: Int,
        val parseMissingFields: Int,
        val parseInvalidSchema: Int,
        val parseOther: Int
    )

    private var lastMat6PipelineDiagnostics: Mat6PipelineDiagnostics? = null

    /** Last load of assets [GRADE6_MAT_SOURCE_PACK] during [loadFromAssets] (DEBUG PoolStatus). */
    data class Grade6MatFileIngestDiagnostics(
        val fileSeen: Boolean,
        val lastParsedCount: Int,
        val lastLoadError: String?
    )

    private var lastGrade6MatFileIngest: Grade6MatFileIngestDiagnostics? = null

    private object Mat6PipelineStats {
        var generatedJsonQuestions: Int = 0
        var parsedEntitiesMat6: Int = 0
        var parseMissingFields: Int = 0
        var parseInvalidSchema: Int = 0
        var parseOther: Int = 0

        fun reset() {
            generatedJsonQuestions = 0
            parsedEntitiesMat6 = 0
            parseMissingFields = 0
            parseInvalidSchema = 0
            parseOther = 0
        }
    }

    /** The seed version baked into this build. Readable by UI without accessing private constants. */
    val currentSeedVersion: Int get() = CURRENT_DB_SEED_VERSION

    /** Reads the seed version stored in the device DB (the last successfully applied version). */
    suspend fun readStoredSeedVersion(context: Context): String {
        return try {
            val meta = DatabaseProvider.get(context).appMetaDao()
            meta.get("db_seed_version") ?: "null (never seeded)"
        } catch (e: Exception) {
            "ERROR: ${e.message}"
        }
    }

    /** Returns the absolute path of the SQLite DB file used at runtime. */
    fun getDatabasePath(context: Context): String =
        context.getDatabasePath("edumio.db").absolutePath

    // ── Official IMAT question bank ─────────────────────────────────────────────
    // Fully isolated from the legacy K-12 / LGS seed pipeline: own asset, own meta
    // version key, own quality-gate-free ingestion (these are official verbatim items).
    private const val KEY_IMAT_SEED_VERSION = "imat_seed_version"
    private const val CURRENT_IMAT_SEED_VERSION = 17
    private const val IMAT_ASSET = "imat/imat_questions.json"

    // ── Mioitalia ORIGINAL question bank ────────────────────────────────────────
    // Fully isolated pool (examType='MIOITALIA'), own asset + own version key + own reseed, kept
    // separate from the official IMAT bank so users never confuse original with official content.
    private const val KEY_MIOITALIA_SEED_VERSION = "mioitalia_seed_version"
    private const val CURRENT_MIOITALIA_SEED_VERSION = 22
    private const val MIOITALIA_ASSET = "mioitalia/questions.json"

    // ── TIL-I & CEnT-S Daily Challenge banks ────────────────────────────────────
    // Fully isolated production pools (examType='TIL_I' / 'CENT_S'), own asset + version key + reseed.
    // Only production-eligible, semantically-verified (PASS) questions are compiled into these assets.
    private const val KEY_TIL_SEED_VERSION = "til_i_seed_version"
    private const val CURRENT_TIL_SEED_VERSION = 1
    private const val TIL_ASSET = "til_i/questions.json"
    private const val KEY_CENTS_SEED_VERSION = "cents_s_seed_version"
    private const val CURRENT_CENTS_SEED_VERSION = 1
    private const val CENTS_ASSET = "cents_s/questions.json"

    @Volatile
    var lastTilSeeded: Int = 0
        internal set

    @Volatile
    var lastCentsSeeded: Int = 0
        internal set

    @Volatile
    var lastImatSeeded: Int = 0
        internal set

    @Volatile
    var lastMioitaliaSeeded: Int = 0
        internal set

    /**
     * Seeds official IMAT questions from [IMAT_ASSET] into Room (examType='IMAT'), idempotent and
     * versioned via [KEY_IMAT_SEED_VERSION] — independent of the K-12 seed flow. On version bump it
     * deletes existing IMAT rows and re-inserts. Bypasses the LGS-tuned quality gate on purpose
     * (these are official, verbatim, key-verified questions).
     */
    suspend fun seedImatIfNeeded(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val db = DatabaseProvider.get(context)
            val meta = db.appMetaDao()
            val dao = db.questionDao()
            val stored = meta.get(KEY_IMAT_SEED_VERSION)?.toIntOrNull() ?: 0
            val already = try { dao.countActiveImatQuestions() } catch (_: Exception) { 0 }
            if (stored >= CURRENT_IMAT_SEED_VERSION && already > 0) {
                Log.i(TAG, "seedImat skip (version=$stored, imatCount=$already)")
                lastImatSeeded = 0
                return@withContext 0
            }
            val entities = parseImatAsset(context)
            if (entities.isEmpty()) {
                Log.w(TAG, "seedImat: asset produced 0 entities — skipping")
                lastImatSeeded = 0
                return@withContext 0
            }
            db.withTransaction {
                dao.deleteImatQuestions()
                dao.insertAllIgnore(entities)
            }
            meta.set(AppMetaEntity(KEY_IMAT_SEED_VERSION, CURRENT_IMAT_SEED_VERSION.toString()))
            Log.i(TAG, "seedImat inserted ${entities.size} IMAT questions (version=$CURRENT_IMAT_SEED_VERSION)")
            lastImatSeeded = entities.size
            entities.size
        } catch (e: Exception) {
            Log.e(TAG, "seedImat failed: ${e.message}", e)
            lastImatSeeded = 0
            0
        }
    }

    private fun parseImatAsset(context: Context): List<QuestionEntity> {
        val json = context.assets.open(IMAT_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        val arr = JSONArray(json)
        val now = System.currentTimeMillis()
        val out = ArrayList<QuestionEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val examSubject = o.optString("examSubject", "logic")
            val stem = o.optString("stem").takeIf { it.isNotBlank() } ?: continue
            val choicesArr = o.optJSONArray("choices") ?: continue
            if (choicesArr.length() < 2) continue
            val answerIndex = o.optInt("answerIndex", -1)
            if (answerIndex < 0 || answerIndex >= choicesArr.length()) continue
            val image = o.optString("image").takeIf { it.isNotBlank() && it != "null" }
            val year = o.optInt("year", 0).takeIf { it > 0 }
            val stemNorm = stem.trim().replace(Regex("\\s+"), " ").lowercase()
            out.add(
                QuestionEntity(
                    id = id,
                    grade = 0,
                    subject = examSubject,
                    difficulty = o.optInt("difficulty", 1),
                    questionText = stem,
                    optionsJson = choicesArr.toString(),
                    answerIndex = answerIndex,
                    explanation = null,
                    isActive = true,
                    examType = "IMAT",
                    imageAsset = image,
                    topic = o.optString("topic").takeIf { it.isNotBlank() },
                    stemNormalized = stemNorm,
                    stemHash = imatSha256(stemNorm),
                    createdAt = now,
                    sourcePack = "imat_${year ?: 0}_$examSubject",
                    source = "pdf",
                    year = year,
                    qualityTier = "MEDIUM",
                    reasoningLevel = 2,
                    qualityScore = 80,
                    unservableReason = null,
                )
            )
        }
        return out
    }

    private fun imatSha256(s: String): String = try {
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }

    /**
     * Seeds Mioitalia ORIGINAL questions from [MIOITALIA_ASSET] into Room (examType='MIOITALIA'),
     * idempotent and versioned via [KEY_MIOITALIA_SEED_VERSION]. Fully isolated from the official IMAT
     * pool and the K-12/LGS flow. On version bump it deletes existing Mioitalia rows and re-inserts.
     */
    suspend fun seedMioitaliaIfNeeded(context: Context): Int = withContext(Dispatchers.IO) {
        try {
            val db = DatabaseProvider.get(context)
            val meta = db.appMetaDao()
            val dao = db.questionDao()
            val stored = meta.get(KEY_MIOITALIA_SEED_VERSION)?.toIntOrNull() ?: 0
            val already = try { dao.countActiveMioitaliaQuestions() } catch (_: Exception) { 0 }
            if (stored >= CURRENT_MIOITALIA_SEED_VERSION && already > 0) {
                Log.i(TAG, "seedMioitalia skip (version=$stored, count=$already)")
                lastMioitaliaSeeded = 0
                return@withContext 0
            }
            val entities = parseMioitaliaAsset(context)
            if (entities.isEmpty()) {
                Log.w(TAG, "seedMioitalia: asset produced 0 entities — skipping")
                lastMioitaliaSeeded = 0
                return@withContext 0
            }
            db.withTransaction {
                dao.deleteMioitaliaQuestions()
                dao.insertAllIgnore(entities)
            }
            meta.set(AppMetaEntity(KEY_MIOITALIA_SEED_VERSION, CURRENT_MIOITALIA_SEED_VERSION.toString()))
            Log.i(TAG, "seedMioitalia inserted ${entities.size} original questions (version=$CURRENT_MIOITALIA_SEED_VERSION)")
            lastMioitaliaSeeded = entities.size
            entities.size
        } catch (e: Exception) {
            Log.e(TAG, "seedMioitalia failed: ${e.message}", e)
            lastMioitaliaSeeded = 0
            0
        }
    }

    private fun parseMioitaliaAsset(context: Context): List<QuestionEntity> {
        val json = try {
            context.assets.open(MIOITALIA_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) { return emptyList() } // asset optional until the bank ships
        val arr = JSONArray(json)
        val now = System.currentTimeMillis()
        val out = ArrayList<QuestionEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val examSubject = o.optString("examSubject", "logic")
            val stem = o.optString("stem").takeIf { it.isNotBlank() } ?: continue
            val choicesArr = o.optJSONArray("choices") ?: continue
            if (choicesArr.length() < 2) continue
            val answerIndex = o.optInt("answerIndex", -1)
            if (answerIndex < 0 || answerIndex >= choicesArr.length()) continue
            val image = o.optString("image").takeIf { it.isNotBlank() && it != "null" }
            val explanation = o.optString("explanation").takeIf { it.isNotBlank() && it != "null" }
            val topic = o.optString("topic").takeIf { it.isNotBlank() && it != "null" }
            val stemNorm = stem.trim().replace(Regex("\\s+"), " ").lowercase()
            out.add(
                QuestionEntity(
                    id = id,
                    grade = 0,
                    subject = examSubject,
                    difficulty = o.optInt("difficulty", 2),
                    questionText = stem,
                    optionsJson = choicesArr.toString(),
                    answerIndex = answerIndex,
                    explanation = explanation,
                    isActive = true,
                    examType = "MIOITALIA",
                    imageAsset = image,
                    topic = topic,
                    stemNormalized = stemNorm,
                    stemHash = imatSha256(stemNorm),
                    createdAt = now,
                    sourcePack = "mioitalia_${o.optString("contentSubject", examSubject)}",
                    source = "original",
                    year = null,
                    qualityTier = "HIGH",
                    reasoningLevel = 3,
                    qualityScore = 90,
                    unservableReason = null,
                )
            )
        }
        return out
    }

    /**
     * Seeds the TIL-I Daily Challenge bank from [TIL_ASSET] (examType='TIL_I'), versioned + idempotent.
     * Fully isolated from IMAT/MIOITALIA/LGS. On version bump: delete existing TIL_I rows, re-insert.
     */
    suspend fun seedTilIIfNeeded(context: Context): Int =
        seedDailyChallengeExam(context, TIL_ASSET, "TIL_I", KEY_TIL_SEED_VERSION, CURRENT_TIL_SEED_VERSION) { lastTilSeeded = it }

    /** Seeds the CEnT-S Daily Challenge bank from [CENTS_ASSET] (examType='CENT_S'), versioned + idempotent. */
    suspend fun seedCentsIfNeeded(context: Context): Int =
        seedDailyChallengeExam(context, CENTS_ASSET, "CENT_S", KEY_CENTS_SEED_VERSION, CURRENT_CENTS_SEED_VERSION) { lastCentsSeeded = it }

    private suspend fun seedDailyChallengeExam(
        context: Context, asset: String, examType: String, versionKey: String, currentVersion: Int, record: (Int) -> Unit,
    ): Int = withContext(Dispatchers.IO) {
        try {
            val db = DatabaseProvider.get(context)
            val meta = db.appMetaDao()
            val dao = db.questionDao()
            val stored = meta.get(versionKey)?.toIntOrNull() ?: 0
            val already = try { dao.countByExamType(examType) } catch (_: Exception) { 0 }
            if (stored >= currentVersion && already > 0) {
                Log.i(TAG, "seed$examType skip (version=$stored, count=$already)")
                record(0); return@withContext 0
            }
            val entities = parseDailyChallengeAsset(context, asset, examType)
            if (entities.isEmpty()) {
                Log.w(TAG, "seed$examType: asset produced 0 entities — skipping")
                record(0); return@withContext 0
            }
            db.withTransaction {
                dao.deleteByExamType(examType)
                dao.insertAllIgnore(entities)
            }
            meta.set(AppMetaEntity(versionKey, currentVersion.toString()))
            Log.i(TAG, "seed$examType inserted ${entities.size} questions (version=$currentVersion)")
            record(entities.size); entities.size
        } catch (e: Exception) {
            Log.e(TAG, "seed$examType failed: ${e.message}", e); record(0); 0
        }
    }

    private fun parseDailyChallengeAsset(context: Context, asset: String, examType: String): List<QuestionEntity> {
        val json = try {
            context.assets.open(asset).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (_: Exception) { return emptyList() } // asset optional until the bank ships
        val arr = JSONArray(json)
        val now = System.currentTimeMillis()
        val out = ArrayList<QuestionEntity>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
            val section = o.optString("section", "unknown")
            val stem = o.optString("stem").takeIf { it.isNotBlank() } ?: continue
            val choicesArr = o.optJSONArray("choices") ?: continue
            if (choicesArr.length() < 2) continue
            val answerIndex = o.optInt("answerIndex", -1)
            if (answerIndex < 0 || answerIndex >= choicesArr.length()) continue
            val image = o.optString("image").takeIf { it.isNotBlank() && it != "null" }
            val explanation = o.optString("explanation").takeIf { it.isNotBlank() && it != "null" }
            val subSection = o.optString("subSection", section)
            val topic = o.optString("topic").takeIf { it.isNotBlank() && it != "null" }
            val tier = o.optString("tier", "MEDIUM")
            val stemNorm = stem.trim().replace(Regex("\\s+"), " ").lowercase()
            out.add(
                QuestionEntity(
                    id = id, grade = 0, subject = section, difficulty = o.optInt("difficulty", 1),
                    questionText = stem, optionsJson = choicesArr.toString(), answerIndex = answerIndex,
                    explanation = explanation, isActive = true, examType = examType, imageAsset = image,
                    topic = topic, type = "DAILY", skill = subSection, stemNormalized = stemNorm,
                    stemHash = imatSha256(stemNorm), createdAt = now,
                    sourcePack = "${examType.lowercase()}_daily", source = "original",
                    qualityTier = tier, reasoningLevel = if (tier == "MEDIUM" || tier == "EASY") 2 else 3,
                    qualityScore = 90, unservableReason = null,
                )
            )
        }
        return out
    }

    fun debugMat6PipelineDiagnostics(): Mat6PipelineDiagnostics? = lastMat6PipelineDiagnostics

    fun debugGrade6MatFileIngest(): Grade6MatFileIngestDiagnostics? = lastGrade6MatFileIngest
    private var lastSeedSourceCounts: SeedSourceCounts = SeedSourceCounts(0, 0, 0, 0, 0)
    private var lastDiscoveredGradeBasedDirs: Int = 0
    private var lastDiscoveredGradeBasedJsonFiles: Int = 0

    /** Latest seed-run diagnostics (nullable fields only where computed). Null if no seed pipeline ran this session. */
    fun getLastSeedDiagnosticsSnapshot(): SeedDiagnosticsSnapshot? = lastSeedDiagnosticsSnapshot

    private suspend fun logSeedAudit(questionDao: QuestionDao, insertedThisRun: Int, skipped: Boolean) {
        lastSeedSkipped = skipped
        lastInsertedThisRun = insertedThisRun
        val total = questionDao.countAll()
        val active = questionDao.countAllActive()
        val inactive = questionDao.countAllInactive()
        Log.i(
            SEED_AUDIT_LOG_TAG,
            "SeedAudit: total=$total active=$active inactive=$inactive inserted=$insertedThisRun skipped=$skipped"
        )
    }

    private fun finalizeMat6PipelineDiagnostics(
        normalized: List<QuestionEntity>,
        deduped: List<QuestionEntity>
    ) {
        val m6Before = normalized.count { it.grade == 6 && it.subject == "mat" }
        val m6After = deduped.count { it.grade == 6 && it.subject == "mat" }
        lastMat6PipelineDiagnostics = Mat6PipelineDiagnostics(
            generatedMat6Total = Mat6PipelineStats.generatedJsonQuestions,
            parsedMat6Total = Mat6PipelineStats.parsedEntitiesMat6,
            mat6DedupDropped = (m6Before - m6After).coerceAtLeast(0),
            parseMissingFields = Mat6PipelineStats.parseMissingFields,
            parseInvalidSchema = Mat6PipelineStats.parseInvalidSchema,
            parseOther = Mat6PipelineStats.parseOther
        )
    }

    private fun updateDbCheckDiagnostics(
        totalRows: Int,
        invalidRows: Int,
        validRows: Int,
        first5: List<String>
    ) {
        val prev = lastSeedDiagnosticsSnapshot ?: SeedDiagnosticsSnapshot()
        val samples = first5.take(5).filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
        lastSeedDiagnosticsSnapshot = prev.copy(
            dbCheckTotalRows = totalRows,
            dbCheckInvalidRows = invalidRows,
            dbCheckValidRows = validRows,
            dbCheckSampleRows = samples
        )
    }

    /** Pack asset name pattern: grade{G}_{subject}.json under assets/packs (and subdirs). */
    private val PACK_FILE_REGEX = Regex(
        pattern = "^grade(1|2|3|4|5|6|7)_(mat|turkce|fen|sosyal|ing)\\.json$",
        option = RegexOption.IGNORE_CASE
    )

    /** Root-level GENERAL question JSON files (array or wrapped { "questions": [] }). */
    private val ROOT_GENERAL_QUESTION_FILES = listOf("questions_tr.json", "import_template.json")

    /** Root-level pack JSONs matching [PACK_FILE_REGEX] (same as assets/packs/). */
    private val ROOT_PACK_FILES = listOf(GRADE6_MAT_SOURCE_PACK)

    /** Desteklenen ders anahtarları (DB'ye bu kısa kodlarla yazılır). */
    private val SUBJECT_KEYS = listOf("mat", "turkce", "fen", "sosyal", "ing")

    suspend fun seedIfNeeded(context: Context): Boolean = withContext(Dispatchers.IO) {
        seedIfNeededMutex.withLock {
            lastSeedDiagnosticsSnapshot = null
            Log.i(TAG, "seedIfNeeded entered (CURRENT_DB_SEED_VERSION=$CURRENT_DB_SEED_VERSION)")
            val db = DatabaseProvider.get(context)
            val meta = db.appMetaDao()
            val questionDao = db.questionDao()

            if (BuildConfig.DEBUG && DEBUG_FORCE_RESEED) {
                Log.w(TAG, "seedIfNeeded DEBUG: DEBUG_FORCE_RESEED=true — running forceReseed")
                return@withLock forceReseed(context)
            }

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
                        logSeedAudit(questionDao, insertedThisRun = 0, skipped = true)
                        return@withLock false
                    }
                    val before = count
                    try {
                        questionDao.insertAllIgnore(toInsert)
                        val after = questionDao.countAll()
                        meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
                        meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
                        Log.i(TAG, "Seed recovery complete: attempted=${toInsert.size} DB before=$before after=$after (added=${after - before})")
                        logSeedAudit(questionDao, insertedThisRun = (after - before), skipped = false)
                        return@withLock (after > before)
                    } catch (e: Exception) {
                        Log.e(TAG, "Seed recovery failed; existing DB preserved", e)
                        return@withLock false
                    }
                }
                Log.d(TAG, "Seed already up to date (version=$storedVersion), skip")
                logSeedAudit(questionDao, insertedThisRun = 0, skipped = true)
                return@withLock false
            }
            if (storedVersion >= CURRENT_DB_SEED_VERSION && count == 0) {
                Log.d(TAG, "Seed forced because DB is empty (version=$storedVersion)")
            }

            Log.i(TAG, "performSeed will run (storedVersion=$storedVersion CURRENT=$CURRENT_DB_SEED_VERSION count=$count)")
            return@withLock performSeed(db, meta, context)
        }
    }

    /**
     * DEBUG: Tüm soru tablosunu temizleyip, asset ve import edilmiş JSON'lardan
     * seeding işlemini baştan çalıştırır.
     *
     * Aynı pipeline: loadFromAssets + loadFromImported (içinde buildSeedQuestions) → deleteAll → insertAll.
     *
     * Kullanım senaryosu:
     * - Yeni paketler eklendikten sonra uygulamayı yeniden yüklemeden havuzu tazelemek.
     */
    suspend fun forceReseed(context: Context): Boolean = withContext(Dispatchers.IO) {
        lastSeedDiagnosticsSnapshot = null
        val db = DatabaseProvider.get(context)
        val meta = db.appMetaDao()
        val questionDao = db.questionDao()

        val countBefore = try { questionDao.countAll() } catch (_: Exception) { -1 }
        Log.w(TAG, "FORCE_RESEED_START: countBefore=$countBefore — loading assets now")

        val toInsert = buildSeedQuestions(context)
        Log.w(TAG, "FORCE_RESEED_LOADED: assetCount=${toInsert.size} lgsCount=${toInsert.count { it.examType == "LGS" }} activeCount=${toInsert.count { it.isActive }}")

        if (toInsert.isEmpty()) {
            Log.e(TAG, "FORCE_RESEED_ABORTED: loaded=0, preserving existing DB (countBefore=$countBefore)")
            try {
                logSeedAudit(questionDao, insertedThisRun = 0, skipped = true)
            } catch (_: Exception) { }
            return@withContext false
        }

        return@withContext try {
            var inserted = 0
            db.withTransaction {
                questionDao.deleteAll()
                val countAfterDelete = questionDao.countAll()
                Log.w(TAG, "FORCE_RESEED_WIPED: countAfterDelete=$countAfterDelete (expected 0)")
                questionDao.insertAll(toInsert)
                meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
                meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
                inserted = toInsert.size
            }
            val countAfter = try { questionDao.countAll() } catch (_: Exception) { -1 }
            val activeAfter = try { questionDao.countAllActive() } catch (_: Exception) { -1 }
            val inactiveAfter = countAfter - activeAfter
            val lgsActive = try { questionDao.countActiveLgsQuestions() } catch (_: Exception) { -1 }
            Log.w(TAG, "FORCE_RESEED_DONE: inserted=$inserted countAfter=$countAfter activeAfter=$activeAfter inactiveAfter=$inactiveAfter lgsActive=$lgsActive")
            // DEBUG: verify actual DB grades after reseed.
            try {
                val allRows = questionDao.getAllQuestions()
                updateDbCheckDiagnostics(
                    totalRows = allRows.size,
                    invalidRows = allRows.count { it.grade !in 1..7 },
                    validRows = allRows.count { it.grade in 1..7 },
                    first5 = allRows.take(5).map { "grade=${it.grade} examType=${it.examType} isActive=${it.isActive}" }
                )
            } catch (e: Exception) {
                Log.e("DB_CHECK", "Failed to read back questions after forceReseed: ${e.message}", e)
            }
            logSeedAudit(questionDao, insertedThisRun = inserted, skipped = false)
            true
        } catch (e: Exception) {
            Log.e(TAG, "FORCE_RESEED_FAILED: old database may be preserved — ${e.message}", e)
            false
        }
    }

    /**
     * DEBUG: Sadece GENERAL havuzunu temizleyip yeniden seed eder; LGS soruları korunur.
     * Root GENERAL dosyaları, packs/ ve lgs_import/ altındaki sınıf paketleri (GENERAL) yeniden yüklenir.
     */
    suspend fun forceReseedGeneralBanks(context: Context): Boolean = withContext(Dispatchers.IO) {
        lastSeedDiagnosticsSnapshot = null
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
            val countBeforeTotal = questionDao.countAll()
            db.withTransaction {
                // Keep LGS rows intact; replace only GENERAL.
                questionDao.deleteGeneralQuestions()
                questionDao.insertAll(toInsert.filter { (it.examType ?: "GENERAL") != "LGS" })
                meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
                meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
            }
            val countAfterTotal = questionDao.countAll()
            val delta = (countAfterTotal - countBeforeTotal).coerceAtLeast(0)
            Log.i(TAG, "Force reseed GENERAL successful: batchNonLgs=${toInsert.count { (it.examType ?: "GENERAL") != "LGS" }} DB total before=$countBeforeTotal after=$countAfterTotal")
            logSeedAudit(questionDao, insertedThisRun = delta, skipped = false)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Force reseed GENERAL failed, old database preserved", e)
            false
        }
    }

    /**
     * Full seed pipeline: merge [rawFromAssets] with imports, fallback, normalize, dedupe.
     * @param rawFromAssets result of [loadFromAssets] only (no imports).
     */
    private fun buildSeedQuestions(context: Context, rawFromAssets: List<QuestionEntity>): List<QuestionEntity> {
        val questions = rawFromAssets.toMutableList()
        try {
            val imported = loadFromImported(context)
            val existingIds = questions.map { it.id }.toSet()
            imported.filter { it.id !in existingIds }.forEach { questions.add(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Seed load error", e)
        }
        if (questions.isEmpty()) {
            questions.addAll(getFallbackEntities())
        }
        val normalized = questions.map { q ->
            val fixedExamType = q.examType ?: "GENERAL"

            val fixedGrade = when {
                fixedExamType == "LGS" -> 7
                q.grade in 1..7 -> q.grade
                else -> 6
            }

            q.copy(
                grade = fixedGrade,
                examType = fixedExamType
            )
        }

        var deduped = normalized.distinctBy(::dedupKey)

        Log.e("SEED_DEBUG", "normalized_count=${normalized.size} invalid_after=${normalized.count { it.grade !in 1..7 }}")

        finalizeMat6PipelineDiagnostics(normalized, deduped)

        // TemplateQualityDetector disabled: it sets isActive=false + unservableReason="TRIVIAL"
        // on clusters of similar questions. Pre-curated asset questions must not be suppressed
        // this way. Re-enable once asset quality is managed separately from algorithmic filters.
        // deduped = TemplateQualityDetector.applyShellClustering(deduped)

        // Belt-and-suspenders: even though parse functions now produce isActive=true/
        // unservableReason=null directly, enforce it here one final time before insert.
        // This catches any edge case (future parse path, fallback entities, etc.) that
        // might slip through with suppression fields set.
        val suppressedCount = deduped.count { !it.isActive || it.unservableReason != null }
        if (suppressedCount > 0) {
            Log.w(TAG, "SEED_ACTIVE_REPAIR: $suppressedCount questions had isActive=false or unservableReason≠null — forcing clean before insert")
        }
        deduped = deduped.map { q ->
            if (q.isActive && q.unservableReason == null) q
            else q.copy(isActive = true, deactivationReason = null, unservableReason = null)
        }
        Log.w(TAG, "SEED_ACTIVE_VERIFY: total=${deduped.size} inactive=${deduped.count { !it.isActive }} unservable=${deduped.count { it.unservableReason != null }} (both must be 0)")

        // Placeholder filter: hard-drop any question whose parsed options still contain
        // a placeholder (e.g. "Seçenek A") — these survived the per-question gate only if
        // parsing produced them synthetically. Belt-and-suspenders before DB insert.
        val placeholderRegexSeed = Regex(
            "^(Se[çc]enek|Option|Cevap|[Şş][ıi]k)\\s*[A-Ea-e]$",
            RegexOption.IGNORE_CASE
        )
        val beforePlaceholderFilter = deduped.size
        deduped = deduped.filter { q ->
            val arr = try { org.json.JSONArray(q.optionsJson) } catch (_: Exception) { return@filter true }
            val opts = (0 until arr.length()).map { arr.optString(it, "").trim() }
            val hasPlaceholder = opts.any { placeholderRegexSeed.matches(it) }
            if (hasPlaceholder) {
                Log.w(TAG, "[SEED_PLACEHOLDER_FILTER] Dropped id=${q.id} grade=${q.grade} subj=${q.subject} opts=${opts.take(4)}")
            }
            !hasPlaceholder
        }
        val placeholderDropped = beforePlaceholderFilter - deduped.size
        if (placeholderDropped > 0) Log.w(TAG, "[SEED_PLACEHOLDER_FILTER] Dropped $placeholderDropped question(s) with placeholder options before insert")

        // Store pre-insert diagnostics for in-app debug UI.
        val invalidAfter = normalized.count { it.grade !in 1..7 }
        lastSeedDiagnosticsSnapshot = SeedDiagnosticsSnapshot(
            loadedRootGeneral = lastSeedSourceCounts.loaded_root_general,
            loadedPacks = lastSeedSourceCounts.loaded_packs,
            loadedGradeBased = lastSeedSourceCounts.loaded_grade_based,
            loadedLgsExam = lastSeedSourceCounts.loaded_lgs_exam,
            loadedSynthetic = lastSeedSourceCounts.loaded_synthetic,
            discoveredGradeBasedDirs = lastDiscoveredGradeBasedDirs,
            discoveredGradeBasedJsonFiles = lastDiscoveredGradeBasedJsonFiles,
            totalBeforeNormalize = questions.size,
            totalAfterNormalize = normalized.size,
            invalidGradeBeforeNormalize = questions.count { it.grade !in 1..7 },
            invalidGradeAfterNormalize = invalidAfter,
            finalInserted = deduped.size,
            normalizationApplied = true,
            invalidAfterNormalize = invalidAfter,
            dbCheckTotalRows = null,
            dbCheckInvalidRows = null,
            dbCheckValidRows = null,
            dbCheckSampleRows = null
        )

        return deduped
    }

    /** Loads assets, then runs [buildSeedQuestions] (import + fallback + normalize + dedupe). */
    private fun buildSeedQuestions(context: Context): List<QuestionEntity> =
        buildSeedQuestions(context, loadFromAssets(context))

    /**
     * Ortak seeding uygulaması: assets + imported JSON + sentetik grade 6 paketleri.
     * Hem ilk kurulum hem de DEBUG force-resede tarafından kullanılır.
     */
    private suspend fun performSeed(
        db: EdumioDatabase,
        meta: AppMetaDao,
        context: Context
    ): Boolean {
        Log.i(TAG, "performSeed started")
        val raw = loadFromAssets(context)
        val dedupedList = buildSeedQuestions(context, raw)
        Log.i(TAG, "performSeed: pipeline complete deduped.size=${dedupedList.size}")

        val questionDao = db.questionDao()
        val countBefore = questionDao.countAll()
        Log.i(TAG, "performSeed: wiping DB (countBefore=$countBefore) then inserting dedupedList.size=${dedupedList.size}")
        db.withTransaction {
            questionDao.deleteAll()
            questionDao.insertAll(dedupedList)
        }
        val countAfter = questionDao.countAll()
        val inserted = countAfter  // full wipe + reinsert: countAfter == rows inserted
        logSeedAudit(questionDao, insertedThisRun = inserted, skipped = false)
        // DEBUG: verify actual DB grades after seeding.
        try {
            val all = questionDao.getAllQuestions()
            val totalRows = all.size
            val invalidRows = all.count { it.grade !in 1..7 }
            val validRows = all.count { it.grade in 1..7 }
            val first5 = all.take(5).map { "grade=${it.grade} examType=${it.examType}" }
            updateDbCheckDiagnostics(
                totalRows = totalRows,
                invalidRows = invalidRows,
                validRows = validRows,
                first5 = first5
            )
        } catch (e: Exception) {
            Log.e("DB_CHECK", "Failed to read back questions after performSeed: ${e.message}", e)
        }
        meta.set(AppMetaEntity(KEY_DB_SEEDED, "true"))
        meta.set(AppMetaEntity(KEY_DB_SEED_VERSION, CURRENT_DB_SEED_VERSION.toString()))
        Log.i(TAG, "performSeed done: wiped=$countBefore inserted=${dedupedList.size} DB after=$countAfter (clean reset complete)")

        // Import sonrası havuz doğrulama
        try {
            validatePoolCoverage(questionDao)
        } catch (e: Exception) {
            Log.w(TAG, "Pool validation failed: ${e.message}")
        }

        // Quality proof scan: log evidence that no placeholder/duplicate-option
        // questions made it into the live DB. Runs async-safe after insert.
        try {
            runQualityProofScan(questionDao)
        } catch (e: Exception) {
            Log.w(TAG, "[QUALITY_PROOF] scan failed: ${e.message}")
        }
        return true
    }

    /**
     * Post-seed proof scan: reads every inserted question and verifies that
     * no placeholder options, duplicate options, or missing-answer cases survived.
     * Results are logged at WARN level so they are visible in prod logcat.
     */
    private suspend fun runQualityProofScan(dao: QuestionDao) {
        val placeholderPattern = Regex(
            "^(Se[çc]enek|Option|Cevap|[Şş][ıi]k)\\s*[A-Ea-e]$",
            RegexOption.IGNORE_CASE
        )
        val all = dao.getAllQuestions()
        val active = all.filter { it.isActive && it.unservableReason.isNullOrBlank() }

        var badPlaceholder = 0
        var badDuplicate = 0
        var badTooFew = 0
        var badAnswerIdx = 0

        for (q in active) {
            try {
                val arr = org.json.JSONArray(q.optionsJson)
                val opts = (0 until arr.length()).map { arr.optString(it, "").trim() }
                val real = opts.filter { it.isNotBlank() && it != "-" }
                if (real.size < 4) { badTooFew++; continue }
                if (real.take(4).any { placeholderPattern.matches(it) }) badPlaceholder++
                if (real.take(4).map { it.trim() }.distinct().size < 4) badDuplicate++
                if (q.answerIndex < 0 || q.answerIndex >= opts.size) badAnswerIdx++
            } catch (_: Exception) {}
        }

        Log.w(TAG, "[QUALITY_PROOF] ===== POST-SEED DB QUALITY SCAN =====")
        Log.w(TAG, "[QUALITY_PROOF] totalDB=${all.size} servable=${active.size}")
        Log.w(TAG, "[QUALITY_PROOF] badPlaceholder=$badPlaceholder (TARGET: 0)")
        Log.w(TAG, "[QUALITY_PROOF] badDuplicate=$badDuplicate (TARGET: 0)")
        Log.w(TAG, "[QUALITY_PROOF] badTooFew=$badTooFew (TARGET: 0)")
        Log.w(TAG, "[QUALITY_PROOF] badAnswerIdx=$badAnswerIdx (TARGET: 0)")

        val sample = active.shuffled().take(50)
        Log.w(TAG, "[QUALITY_PROOF] ===== 50 SAMPLE INSERTED QUESTIONS =====")
        sample.forEachIndexed { i, q ->
            try {
                val arr = org.json.JSONArray(q.optionsJson)
                val opts = (0 until arr.length()).map { arr.optString(it, "").trim() }
                val correct = opts.getOrNull(q.answerIndex) ?: "?"
                Log.w(TAG, "[QUALITY_PROOF] Q$i sub=${q.subject} gr=${q.grade} " +
                    "stem='${q.questionText.take(70)}' " +
                    "opts=[${opts.take(4).joinToString(" | ")}] " +
                    "ansIdx=${q.answerIndex} correct='$correct'")
            } catch (_: Exception) {}
        }
        Log.w(TAG, "[QUALITY_PROOF] ========================================")
    }

    private fun loadFromAssets(context: Context): List<QuestionEntity> {
        Mat6PipelineStats.reset()
        lastGrade6MatFileIngest = null
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

        // 1b) Root-level pack files matching PACK_FILE_REGEX (e.g. grade6_mat.json).
        for (assetName in ROOT_PACK_FILES) {
            if (!PACK_FILE_REGEX.matches(assetName)) continue
            try {
                val json = context.assets.open(assetName).use { input ->
                    input.readBytes().toString(Charset.forName("UTF-8"))
                }
                val parsed = parsePackFileContent(assetName, json)
                all += parsed
                if (parsed.isNotEmpty()) {
                    Log.i(TAG, "Loaded root pack file: $assetName (${parsed.size} questions)")
                }
            } catch (e: Exception) {
                if (assetName.equals(GRADE6_MAT_SOURCE_PACK, ignoreCase = true)) {
                    lastGrade6MatFileIngest = Grade6MatFileIngestDiagnostics(
                        fileSeen = false,
                        lastParsedCount = 0,
                        lastLoadError = e.message
                    )
                }
                Log.w(TAG, "Root pack file $assetName error: ${e.message}")
            }
        }

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
                if (assetPath.substringAfterLast('/').equals(GRADE6_MAT_SOURCE_PACK, ignoreCase = true)) {
                    lastGrade6MatFileIngest = Grade6MatFileIngestDiagnostics(
                        fileSeen = false,
                        lastParsedCount = 0,
                        lastLoadError = e.message
                    )
                }
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
        lastSeedSourceCounts = SeedSourceCounts(
            loaded_root_general = rootCount,
            loaded_packs = packCount,
            loaded_grade_based = lgsImportCount,
            loaded_lgs_exam = lgsImportRootDirsCount,
            loaded_synthetic = syntheticCount
        )
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
                            parseJsonArrayWithDefaultsStrict(
                                JSONArray(json),
                                lgsGradePlaceholder,
                                subjectKey,
                                sourceLabel = assetPath
                            )
                        }
                        else -> emptyList()
                    }
                    if (entities.isNotEmpty()) {
                        // Force LGS-root mode: examType=LGS; grade must be 1..7 (use placeholder).
                        // Also force isActive=true: lgs_exam/* questions are curated/pre-vetted;
                        // the general quality gate (designed for grade-based content) must not
                        // deactivate them at parse time. LGS picker relies on these being active.
                        val lgsEntities = entities.map {
                            it.copy(
                                examType = "LGS",
                                grade = lgsGradePlaceholder,
                                isActive = true,
                                deactivationReason = null,
                                unservableReason = null  // must clear: gate may have set "TRIVIAL"; candidate-pool queries filter on this
                            )
                        }
                        Log.i(TAG, "lgs_exam/$folder/$assetPath: loaded=${lgsEntities.size} all forced isActive=true unservableReason=null")
                        out += lgsEntities
                        loadedForFolder += lgsEntities.size
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "lgs_exam load failed for $assetPath: ${e.message}", e)
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
    private val LGS_PATH_GRADE_SUBJECT_REGEX = Regex(
        "^(mat|turkce|fen|sosyal|english|hayat|din|inkilap)[^0-9]*(\\d+)$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Derives (grade, subjectKey) from an lgs_import path segment.
     * E.g. "fen3" -> (3, "fen"), "english2" -> (2, "ing"), "inkilap7" -> (7, "inkilap").
     * Returns null if the path does not match a known grade-based folder.
     */
    private fun parseGradeAndSubjectFromLgsPath(assetPath: String): Pair<Int, String>? {
        val relative = assetPath.removePrefix("grade_based/").trimStart('/')
        val segments = relative.split('/', '\\').filter { it.isNotBlank() }
        for (seg in segments.asReversed()) {
            val match = LGS_PATH_GRADE_SUBJECT_REGEX.find(seg) ?: continue
            val (subjectPart, gradePart) = match.destructured
            val grade = gradePart.toIntOrNull()?.coerceIn(1, 8) ?: continue
            val subjectKey = when (subjectPart.lowercase()) {
                "mat" -> "mat"
                "turkce" -> "turkce"
                "fen" -> "fen"
                "sosyal" -> "sosyal"
                "english" -> "ing"
                "hayat" -> "hayat"
                "din" -> "din"
                "inkilap" -> "inkilap"
                else -> continue
            }
            return grade to subjectKey
        }
        return null
    }

    /**
     * Fallback when folder names don't match [LGS_PATH_GRADE_SUBJECT_REGEX] (e.g. unusual layouts).
     * Typical committed packs: `lgs_eng2_pack_035.json`, `lgs_fen3_pack_029.json`.
     */
    private val PACK_FILENAME_GRADE_SUBJECT_REGEX = Regex(
        "(?i)lgs_(mat|turkce|fen|sosyal|eng|din|hayat|inkilap)(\\d+)_pack",
    )

    private fun parseGradeSubjectFromPackFilenameSegment(fileName: String): Pair<Int, String>? {
        val m = PACK_FILENAME_GRADE_SUBJECT_REGEX.find(fileName) ?: return null
        val sub = m.groupValues[1].lowercase()
        val g = m.groupValues[2].toIntOrNull()?.coerceIn(1, 8) ?: return null
        val subjectKey = when (sub) {
            "mat" -> "mat"
            "turkce" -> "turkce"
            "fen" -> "fen"
            "sosyal" -> "sosyal"
            "eng" -> "ing"
            "din" -> "din"
            "hayat" -> "hayat"
            "inkilap" -> "inkilap"
            else -> return null
        }
        return g to subjectKey
    }

    private fun resolveGradeSubjectForGradeBasedAsset(assetPath: String): Pair<Int, String> {
        parseGradeAndSubjectFromLgsPath(assetPath)?.let { return it }
        parseGradeSubjectFromPackFilenameSegment(assetPath.substringAfterLast('/'))?.let { return it }
        val g = deriveGradeFromAssetPath(assetPath)?.coerceIn(1, 7)
        if (g != null) {
            Log.w(TAG, "resolveGradeSubject: using path-derived grade=$g only, subject default mat path=$assetPath")
            return g to "mat"
        }
        Log.w(TAG, "resolveGradeSubject: could not derive grade/subject; using 6/mat path=$assetPath")
        return 6 to "mat"
    }

    private fun loadFromLgsGradePacksAsGeneral(context: Context): List<QuestionEntity> {
        val assets = context.assets
        val out = mutableListOf<QuestionEntity>()

        // Auto-discover: assets/grade_based/{subject}{1..7}/
        // Only folders matching the allowed pattern are scanned; nothing else is treated as grade banks.
        val allowed = Regex(
            "^(hayat|mat|fen|turkce|english|din|sosyal|inkilap)\\D*[1-7]$",
            RegexOption.IGNORE_CASE
        )
        val topEntries = try {
            assets.list("grade_based")?.sorted().orEmpty()
        } catch (e: Exception) {
            Log.w(TAG, "Asset list failed for grade_based/: ${e.message}")
            emptyList()
        }
        val allowedGradeBasedDirs = topEntries.filter { allowed.matches(it) }.map { "grade_based/$it" }
        val gradeBasedDirs = if (allowedGradeBasedDirs.isNotEmpty()) {
            allowedGradeBasedDirs
        } else {
            // Fallback: scan the entire grade_based root recursively to avoid root/path mismatches.
            // This is required for runtime verification when top-level directory naming differs.
            listOf("grade_based")
        }

        // Runtime discovery audit (for debug UI).
        var discoveredJsonFiles = 0
        for (dir in gradeBasedDirs) {
            discoveredJsonFiles += discoverJsonAssetFilesRecursive(assets, dir).size
        }
        lastDiscoveredGradeBasedDirs = gradeBasedDirs.size
        lastDiscoveredGradeBasedJsonFiles = discoveredJsonFiles

        for (dir in gradeBasedDirs) {
            val assetPaths = discoverJsonAssetFilesRecursive(assets, dir)
            for (assetPath in assetPaths) {
                val (pathGrade, pathSubject) = resolveGradeSubjectForGradeBasedAsset(assetPath)
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
                            parseJsonArrayWithDefaults(
                                org.json.JSONArray(json),
                                pathGrade,
                                pathSubject,
                                sourceLabel = "grade_based_defaults|grade=$pathGrade|subject=$pathSubject|$assetPath"
                            )
                        }
                        else -> emptyList()
                    }
                    val normalizedGrade = pathGrade.coerceIn(1, 7)
                    val normalized = entities.map {
                        it.copy(
                            grade = normalizedGrade,
                            subject = pathSubject,
                            examType = "GENERAL"
                        )
                    }
                    if (normalized.isNotEmpty()) out += normalized
                    Log.i(
                        TAG,
                        "Loaded $assetPath as GENERAL pack: ${normalized.size} questions (grade=$normalizedGrade subject=$pathSubject)"
                    )
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
            trimmed.startsWith("[") -> parseJsonArray(org.json.JSONArray(json), sourceLabel = "root_json_array|$assetName")
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
        val sourcePackTag =
            if (fileName.equals(GRADE6_MAT_SOURCE_PACK, ignoreCase = true)) GRADE6_MAT_SOURCE_PACK else null
        val trimmed = json.trimStart()
        val result = when {
            trimmed.startsWith("[") -> parsePackJsonArray(
                org.json.JSONArray(json),
                forcedGrade,
                forcedSubject,
                assetPath,
                sourcePackTag
            )
            trimmed.startsWith("{") -> {
                val root = org.json.JSONObject(json)
                val arr = root.optJSONArray("questions")
                if (arr == null) emptyList()
                else parsePackWrappedQuestions(arr, forcedGrade, forcedSubject, assetPath, sourcePackTag)
            }
            else -> emptyList()
        }
        if (fileName.equals(GRADE6_MAT_SOURCE_PACK, ignoreCase = true)) {
            try {
                when {
                    trimmed.startsWith("{") -> {
                        val root = JSONObject(json)
                        Mat6PipelineStats.generatedJsonQuestions += (root.optJSONArray("questions")?.length() ?: 0)
                    }
                    trimmed.startsWith("[") -> {
                        Mat6PipelineStats.generatedJsonQuestions += org.json.JSONArray(json).length()
                    }
                }
            } catch (_: Exception) { }
            Mat6PipelineStats.parsedEntitiesMat6 += result.size
            lastGrade6MatFileIngest = Grade6MatFileIngestDiagnostics(
                fileSeen = true,
                lastParsedCount = result.size,
                lastLoadError = null
            )
        }
        return result
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
    private fun parsePackJsonArray(
        arr: JSONArray,
        forcedGrade: Int,
        forcedSubject: String,
        sourceLabel: String,
        sourcePackTag: String? = null
    ): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val combined = org.json.JSONObject(o.toString())
                combined.put("grade", forcedGrade)
                combined.put("subject", forcedSubject)
                if (!combined.has("examType")) combined.put("examType", "GENERAL")
                out.add(parseQuestionObject(combined, i, sourceLabel = sourceLabel, sourcePackTag = sourcePackTag))
            } catch (e: Exception) {
                Log.w(TAG, "Pack parse failed $sourceLabel index $i: ${e.message}")
            }
        }
        return out
    }

    private fun parsePackWrappedQuestions(
        arr: JSONArray,
        forcedGrade: Int,
        forcedSubject: String,
        sourceLabel: String,
        sourcePackTag: String? = null
    ): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val q = arr.getJSONObject(i)
                val combined = org.json.JSONObject(q.toString())
                combined.put("grade", forcedGrade)
                combined.put("subject", forcedSubject)
                if (!combined.has("examType")) combined.put("examType", "GENERAL")
                out.add(parseQuestionObject(combined, i, sourceLabel = sourceLabel, sourcePackTag = sourcePackTag))
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
                out.add(parseQuestionObject(combined, i, sourceLabel = sourceLabel))
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed $sourceLabel index $i: ${e.message}")
            }
        }
        return out
    }

    /**
     * Same as [parseWrappedQuestionArray] but records mat6 parse-failure categories for DEBUG PoolStatus.
     */
    private fun parseWrappedQuestionArrayMat6Instrumented(
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
                out.add(parseQuestionObject(combined, i, sourceLabel = sourceLabel))
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed $sourceLabel index $i: ${e.message}")
                when (e) {
                    is IllegalArgumentException -> Mat6PipelineStats.parseMissingFields++
                    is JSONException -> Mat6PipelineStats.parseInvalidSchema++
                    else -> Mat6PipelineStats.parseOther++
                }
            }
        }
        return out
    }

    /** Parse JSON array when each item may lack grade/subject; use path-derived defaults. */
    private fun parseJsonArrayWithDefaults(
        arr: JSONArray,
        defaultGrade: Int,
        defaultSubject: String,
        sourceLabel: String
    ): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                val combined = org.json.JSONObject(o.toString())
                val qGrade = o.optInt("grade", -1)
                val qSubject = o.optString("subject", "").trim().lowercase()
                combined.put("grade", if (qGrade in 1..8) qGrade else defaultGrade)
                combined.put("subject", if (qSubject.isNotBlank()) qSubject else defaultSubject)
                out.add(parseQuestionObject(combined, i, sourceLabel = sourceLabel))
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
            parseJsonArray(JSONArray(json), sourceLabel = "imported_json_array")
        } catch (e: Exception) {
            Log.e(TAG, "imported load error", e)
            emptyList()
        }
    }

    private fun parseJsonArray(arr: JSONArray, sourceLabel: String): List<QuestionEntity> {
        val out = mutableListOf<QuestionEntity>()
        for (i in 0 until arr.length()) {
            try {
                val o = arr.getJSONObject(i)
                out.add(parseQuestionObject(o, i, sourceLabel = sourceLabel))
            } catch (e: Exception) {
                Log.w(TAG, "Parse failed index $i: ${e.message}")
            }
        }
        return out
    }

    private fun parseJsonArrayWithDefaultsStrict(
        arr: JSONArray,
        defaultGrade: Int,
        defaultSubject: String,
        sourceLabel: String
    ): List<QuestionEntity> {
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
                out.add(parseQuestionObject(combined, i, sourceLabel = sourceLabel))
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
                out.add(parseQuestionObject(combined, i, sourceLabel = sourceLabel))
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
    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Same alias order as [com.edumio.app.quiz.QuestionPackImporter.optImageAsset]. */
    private fun optImageAssetFromJson(o: JSONObject): String? =
        sequenceOf("imageAsset", "visualAsset", "graphicAsset", "tableAsset")
            .mapNotNull { key ->
                o.optString(key, "").takeIf { s -> s.isNotBlank() && s.lowercase() != "null" }
            }
            .firstOrNull()

    private fun parseQuestionObject(
        o: JSONObject,
        index: Int,
        sourceLabel: String = "unknown",
        sourcePackTag: String? = null
    ): QuestionEntity {
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

        // Gate 1: must have exactly 4 options
        if (rawOptions.size < 4) {
            throw IllegalArgumentException("Not enough options (${rawOptions.size}/4) at index=$index")
        }
        val padded = rawOptions.take(4)

        // Gate 2: no placeholder options
        val placeholderRegexParser = Regex(
            "^(Se[çc]enek|Option|Cevap|[Şş][ıi]k)\\s*[A-Ea-e]$",
            RegexOption.IGNORE_CASE
        )
        val placeholderOpt = padded.firstOrNull { placeholderRegexParser.matches(it.trim()) }
        if (placeholderOpt != null) {
            throw IllegalArgumentException("Placeholder option '$placeholderOpt' at index=$index")
        }

        // Gate 3: all 4 options must be case-sensitively distinct (after trim)
        if (padded.map { it.trim() }.distinct().size < 4) {
            throw IllegalArgumentException("Duplicate options at index=$index: ${padded.joinToString("|")}")
        }

        // answer index: answerIndex (yeni) veya correctIndex (eski)
        val rawAnswerIndex = if (o.has("answerIndex")) {
            o.optInt("answerIndex", 0)
        } else {
            o.optInt("correctIndex", 0)
        }
        val answerIndex = rawAnswerIndex.coerceIn(0, padded.size - 1)

        // Gate 4: telegraphed answer or weak numeric distractors
        val telegraphed = com.edumio.app.quiz.QuizOutputGuard.isHardTelegraphed(padded, answerIndex)
        if (telegraphed) {
            throw IllegalArgumentException("Telegraphed answer at index=$index answerIdx=$answerIndex opts=${padded.joinToString("|")}")
        }
        val weakNumeric = com.edumio.app.quiz.QuizOutputGuard.isWeakNumericDistractors(padded, answerIndex)
        if (weakNumeric) {
            throw IllegalArgumentException("Weak numeric distractors at index=$index opts=${padded.joinToString("|")}")
        }

        val optionsJson = JSONArray(padded).toString()

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
        val imageAsset = optImageAssetFromJson(o)

        // id: varsa kullan, yoksa grade+subject+index tabanlı üret
        val explicitId = o.optString("id", "").takeIf { it.isNotBlank() }
        val rawId = explicitId ?: "${grade}_${subjectKey}_${hash.take(16)}_${(index + 1).toString().padStart(4, '0')}"
        // Source scoped: prevent collisions across files that reuse the same `id` or index fallback.
        val id = sha1("$sourceLabel|$rawId")

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
            difficulty = difficulty,
            answerIndex = answerIndex
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
            // Asset-sourced questions are always active — the quality gate classifies
            // but must not suppress pre-curated content. isActive/deactivationReason/
            // unservableReason are forced to their safe values here at parse time so
            // no later stage needs to "repair" them.
            isActive = true,
            questionType = gate.questionType,
            skillsJson = gate.skillsJson,
            deactivationReason = null,
            version = 1,
            examType = examType,
            imageAsset = imageAsset,
            type = diversityType,
            skill = diversitySkill,
            stemNormalized = stemNorm,
            stemHash = hash,
            sourcePack = sourcePackTag,
            qualityTier = gate.qualityTier,
            reasoningLevel = gate.reasoningLevel,
            reasoningScore = gate.reasoningScore,
            distractorQualityScore = gate.distractorQualityScore,
            contextComplexityScore = gate.contextComplexityScore,
            qualityFlagsJson = gate.qualityFlagsJson,
            unservableReason = null,
        )
    }

    private fun dedupKey(e: QuestionEntity): String = QuestionStemHash.contentDedupKey(e)

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
            difficulty = normalizedDifficulty,
            answerIndex = answerIndex
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
            isActive = true,
            questionType = gate.questionType,
            skillsJson = gate.skillsJson,
            deactivationReason = null,
            version = 1,
            examType = "GENERAL",
            imageAsset = null,
            type = diversityType,
            skill = diversitySkill,
            stemNormalized = stemNorm,
            stemHash = hash,
            qualityTier = gate.qualityTier,
            reasoningLevel = gate.reasoningLevel,
            reasoningScore = gate.reasoningScore,
            distractorQualityScore = gate.distractorQualityScore,
            contextComplexityScore = gate.contextComplexityScore,
            qualityFlagsJson = gate.qualityFlagsJson,
            unservableReason = null,
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
                    val distractor2 = if (kept + 2 == boughtTotal) kept + 3 else kept + 2
                    SyntheticQuestionSpec(
                        stem = stem,
                        options = listOf(
                            kept.toString(),
                            boughtTotal.toString(),
                            distractor2.toString(),
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
