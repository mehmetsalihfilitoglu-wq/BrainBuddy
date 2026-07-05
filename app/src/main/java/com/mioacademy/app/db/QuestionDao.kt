package com.mioacademy.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Aggregate row for grade+subject havuz doğrulama.
 */
data class GradeSubjectCount(
    val grade: Int,
    val subject: String,
    val count: Int
)

/** Total rows per grade (all active + inactive). */
data class GradeCountRow(
    val grade: Int,
    val count: Int
)

/** grade+subject+difficulty bazında ACTIVE soru sayıları (import debug ekranı). */
data class GradeSubjectDifficultyCount(
    val grade: Int,
    val subject: String,
    val difficulty: Int,
    val count: Int
)

/** LGS candidate row with qualityScore for blueprint-based planning. */
data class LgsCandidateRow(
    val id: String,
    val subject: String,
    val difficulty: Int,
    val grade: Int,
    val stemHash: String,
    val stemNormalized: String,
    val type: String,
    val skill: String,
    val qualityScore: Int,
    val qualityTier: String,
    val reasoningLevel: Int,
)

/**
 * Lightweight candidate row for quiz picking.
 * Used to avoid scanning/decoding full QuestionEntity payloads during selection.
 */
data class QuestionCandidateRow(
    val id: String,
    val subject: String,
    val difficulty: Int,
    val grade: Int,
    val stemHash: String,
    val stemNormalized: String,
    val type: String,
    val skill: String,
    val topic: String,        // for within-quiz topic diversity
    val qualityTier: String,
    val reasoningLevel: Int,
)

/** Pool Status: Aynı grade+subject içinde en çok tekrar eden stemHash. */
data class DuplicateStemHashRow(
    val grade: Int,
    val subject: String,
    val stemHash: String,
    val cnt: Int
)

/** LGS pool: subject bazında ACTIVE soru sayıları. */
data class LgsSubjectCount(val subject: String, val count: Int)

/** LGS pool: difficulty bazında ACTIVE soru sayıları. */
data class LgsDifficultyCount(val difficulty: Int, val count: Int)

/** LGS quality debug: subject, avgQualityScore (rounded). */
data class LgsAvgQualityBySubject(val subject: String, val avgQualityScore: Double)

/** LGS quality debug: subject, newGen count, total count. */
data class LgsNewGenCountBySubject(val subject: String, val newGenCount: Int, val totalCount: Int)

/** LGS pool: questionType bazında ACTIVE soru sayıları (subject filtresi için). */
data class LgsQuestionTypeCount(val questionType: String, val count: Int)

/** Tüm sorularda ders bazında toplam sayı (debug özeti). */
data class SubjectCount(val subject: String, val count: Int)

/** PoolStatus DEBUG: inactive reason bucket (nullable reason → empty string). */
data class DeactivationReasonCountRow(val reason: String?, val cnt: Int)

/** Debug: counts per content quality tier (active rows). */
data class QualityTierCountRow(val qualityTier: String, val count: Int)

@Dao
interface QuestionDao {

    /** Genel amaçlı insert - import vb. için REPLACE davranışı korunur. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    /** İlk seed sırasında id bazlı INSERT IGNORE. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(questions: List<QuestionEntity>)

    @Query("SELECT * FROM questions WHERE isActive = 1 ORDER BY id")
    suspend fun getActiveQuestions(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (:ids) AND isActive = 1")
    suspend fun getQuestionsByIds(ids: List<String>): List<QuestionEntity>

    /** Sınıf bazlı havuz: grade ve subject'e göre (index kullanır). */
    @Query(
        """
        SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND subject = :subject
        AND COALESCE(examType, 'GENERAL') != 'LGS'
        """
    )
    suspend fun getByGradeSubject(grade: Int, subject: String): List<QuestionEntity>

    /** Sınıf + ders + zorluk filtresi. */
    @Query(
        """
        SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND subject = :subject AND difficulty = :difficulty
        AND COALESCE(examType, 'GENERAL') != 'LGS'
        """
    )
    suspend fun getByGradeSubjectDifficulty(grade: Int, subject: String, difficulty: Int): List<QuestionEntity>

    /**
     * Quiz picker candidate pool (LIMIT 200).
     * Only a lightweight subset of columns is fetched for fast in-memory filtering.
     */
    @Query(
        """
        SELECT id, subject, difficulty, grade, stemHash, stemNormalized, type, skill
        , COALESCE(topic, 'OTHER') AS topic
        , COALESCE(qualityTier, 'MEDIUM') AS qualityTier
        , COALESCE(reasoningLevel, 2) AS reasoningLevel
        FROM questions
        WHERE grade = :grade
        AND subject = :subject
        AND difficulty = :difficulty
        AND isActive = 1
        AND COALESCE(examType, 'GENERAL') != 'LGS'
        AND (unservableReason IS NULL OR unservableReason = '')
        AND COALESCE(qualityTier, 'MEDIUM') IN (:allowedTiers)
        LIMIT 2000
        """
    )
    suspend fun getCandidatePoolByGradeSubjectDifficulty(
        grade: Int,
        subject: String,
        difficulty: Int,
        allowedTiers: List<String>,
    ): List<QuestionCandidateRow>

    /**
     * Candidate pool per subject (LIMIT 2000). Any difficulty; partition in memory.
     * [allowedTiers] typically MEDIUM+HARD for playable pool.
     */
    @Query(
        """
        SELECT id, subject, difficulty, grade, stemHash, stemNormalized, type, skill
        , COALESCE(topic, 'OTHER') AS topic
        , COALESCE(qualityTier, 'MEDIUM') AS qualityTier
        , COALESCE(reasoningLevel, 2) AS reasoningLevel
        FROM questions
        WHERE grade = :grade
        AND subject = :subject
        AND isActive = 1
        AND COALESCE(examType, 'GENERAL') != 'LGS'
        AND (unservableReason IS NULL OR unservableReason = '')
        AND COALESCE(qualityTier, 'MEDIUM') IN (:allowedTiers)
        LIMIT 2000
        """
    )
    suspend fun getCandidatePoolByGradeSubject(
        grade: Int,
        subject: String,
        allowedTiers: List<String>,
    ): List<QuestionCandidateRow>

    /**
     * LGS pool: questions with examType = 'LGS' and given subject.
     * Used by LGS picker - does NOT use grade.
     */
    @Query(
        """
        SELECT id, subject, difficulty, grade, stemHash, stemNormalized, type, skill
        , COALESCE(topic, 'OTHER') AS topic
        , COALESCE(qualityTier, 'MEDIUM') AS qualityTier
        , COALESCE(reasoningLevel, 2) AS reasoningLevel
        FROM questions
        WHERE COALESCE(examType, 'GENERAL') = 'LGS'
        AND subject = :subject
        AND isActive = 1
        AND (unservableReason IS NULL OR unservableReason = '')
        AND COALESCE(qualityTier, 'MEDIUM') IN (:allowedTiers)
        LIMIT 2000
        """
    )
    suspend fun getCandidatePoolByLgsSubject(subject: String, allowedTiers: List<String>): List<QuestionCandidateRow>

    /**
     * LGS pool with qualityScore for blueprint-based planner.
     * Ordered by qualityScore DESC to prefer highest-quality items.
     */
    @Query(
        """
        SELECT id, subject, difficulty, grade, stemHash, stemNormalized, type, skill,
               COALESCE(qualityScore, 0) AS qualityScore,
               COALESCE(qualityTier, 'MEDIUM') AS qualityTier,
               COALESCE(reasoningLevel, 2) AS reasoningLevel
        FROM questions
        WHERE COALESCE(examType, 'GENERAL') = 'LGS'
        AND subject = :subject
        AND isActive = 1
        AND (unservableReason IS NULL OR unservableReason = '')
        AND COALESCE(qualityTier, 'MEDIUM') IN (:allowedTiers)
        ORDER BY qualityScore DESC
        LIMIT 300
        """
    )
    suspend fun getLgsCandidatePoolWithQuality(subject: String, allowedTiers: List<String>): List<LgsCandidateRow>

    /** Tüm sınıf havuzu (grade 1-7 için test oluşturma). */
    @Query(
        """
        SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND grade > 0
        AND COALESCE(examType, 'GENERAL') != 'LGS'
        """
    )
    suspend fun getByGrade(grade: Int): List<QuestionEntity>

    @Query(
        """
        UPDATE questions SET unservableReason = :reason, qualityTier = :tier
        WHERE id = :id
        """
    )
    suspend fun updateQuarantineFlags(id: String, reason: String, tier: String)

    /**
     * Havuz doğrulama için grade+subject bazında COUNT.
     * Sadece aktif ve 1..7. sınıflar.
     */
    @Query(
        """
        SELECT grade, subject, COUNT(*) AS count
        FROM questions
        WHERE isActive = 1 AND grade BETWEEN 1 AND 7
        GROUP BY grade, subject
        """
    )
    suspend fun getCountsByGradeSubject(): List<GradeSubjectCount>

    /** All rows: grade × subject counts (includes inactive). */
    @Query(
        """
        SELECT grade, subject, COUNT(*) AS count
        FROM questions
        GROUP BY grade, subject
        ORDER BY grade, subject
        """
    )
    suspend fun getAllGroupedByGradeSubject(): List<GradeSubjectCount>

    /** All rows: count per grade (includes inactive). */
    @Query(
        """
        SELECT grade, COUNT(*) AS count
        FROM questions
        GROUP BY grade
        ORDER BY grade
        """
    )
    suspend fun getCountsGroupedByGrade(): List<GradeCountRow>

    /** Tüm sorularda ders bazında toplam sayı (reseed özeti). */
    @Query("SELECT subject, COUNT(*) AS count FROM questions GROUP BY subject ORDER BY subject")
    suspend fun getCountsBySubject(): List<SubjectCount>

    /** Import sonrası debug: grade/subject/difficulty bazında ACTIVE sayıları. */
    @Query(
        """
        SELECT grade, LOWER(subject) AS subject, difficulty, COUNT(*) AS count
        FROM questions
        WHERE isActive = 1 AND grade BETWEEN 1 AND 7
        GROUP BY grade, LOWER(subject), difficulty
        ORDER BY grade, subject, difficulty
        """
    )
    suspend fun getActiveCountsByGradeSubjectDifficulty(): List<GradeSubjectDifficultyCount>

    /** Tüm sorular – kalite raporu ve özetler için. */
    @Query("SELECT * FROM questions")
    suspend fun getAllQuestions(): List<QuestionEntity>

    // ---- Debug/diagnostic COUNT API'leri ----

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE isActive = 1")
    suspend fun countAllActive(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE isActive = 0")
    suspend fun countAllInactive(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE COALESCE(examType,'GENERAL') = 'LGS'")
    suspend fun countLgsQuestions(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE COALESCE(examType,'GENERAL') = 'LGS' AND isActive = 1")
    suspend fun countActiveLgsQuestions(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade")
    suspend fun countByGrade(grade: Int): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND isActive = 1")
    suspend fun countActiveByGrade(grade: Int): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND subject = :subject")
    suspend fun countByGradeSubject(grade: Int, subject: String): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND subject = :subject AND isActive = 1")
    suspend fun countActiveByGradeSubject(grade: Int, subject: String): Int

    /** Pasif sorular (kalite gate vb.) — kota teşhisi için. */
    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND subject = :subject AND isActive = 0")
    suspend fun countInactiveByGradeSubject(grade: Int, subject: String): Int

    /**
     * Inactive rows whose [QuestionEntity.deactivationReason] matches [com.mioacademy.app.quiz.QuestionQualityGate]
     * (parse-time deactivation). Per core (grade, subject) effectiveness.
     */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE grade = :grade AND subject = :subject AND isActive = 0
        AND COALESCE(deactivationReason, '') IN (
            'too_trivial', 'too_short', 'too_basic', 'too_simple_math', 'too_memorization'
        )
        """
    )
    suspend fun countInactiveQualityGateByGradeSubject(grade: Int, subject: String): Int

    /** Aktif LGS satırları (examType=LGS) — grade 7 havuz karışımı notu için. */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE grade = :grade AND subject = :subject
        AND isActive = 1 AND COALESCE(examType, 'GENERAL') = 'LGS'
        """
    )
    suspend fun countActiveLgsByGradeSubject(grade: Int, subject: String): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND subject = :subject AND difficulty = :difficulty")
    suspend fun countByGradeSubjectDifficulty(grade: Int, subject: String, difficulty: Int): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND subject = :subject AND difficulty = :difficulty AND isActive = 1")
    suspend fun countActiveByGradeSubjectDifficulty(grade: Int, subject: String, difficulty: Int): Int

    // ---- Grade-only dağılım ve geçersiz grade teşhisi ----

    /** Belirli bir grade için toplam soru sayısı (aktif/pasif fark etmeksizin). */
    @Query("SELECT COUNT(*) FROM questions WHERE grade = :g")
    suspend fun countByGradeOnly(g: Int): Int

    /** Belirli bir grade için aktif soru sayısı (isActive=1). */
    @Query("SELECT COUNT(*) FROM questions WHERE grade = :g AND isActive = 1")
    suspend fun countActiveByGradeOnly(g: Int): Int

    /** 1..7 aralığı dışındaki tüm grade değerlerinin toplam sayısı (0,8,9+ vs). */
    @Query("SELECT COUNT(*) FROM questions WHERE grade < 1 OR grade > 7")
    suspend fun countInvalidGrades(): Int

    /** Geçersiz grade'leri (1..7 dışı) hedef grade'e taşımak için acil debug fix. */
    @Query("UPDATE questions SET grade = :target WHERE grade < 1 OR grade > 7")
    suspend fun fixInvalidGrades(target: Int)

    @Query("UPDATE questions SET isActive=1 WHERE isActive!=1")
    suspend fun forceActivateAll()

    @Query("UPDATE questions SET difficulty=2 WHERE difficulty>2")
    suspend fun clampDifficulty()

    /** Zorluk aralığı dışındaki soru sayısı (0-2 dışı). */
    @Query("SELECT COUNT(*) FROM questions WHERE difficulty < 0 OR difficulty > 2")
    suspend fun countDifficultyOutOfRange(): Int

    /** difficulty < 0 olanları 0 yap. */
    @Query("UPDATE questions SET difficulty = 0 WHERE difficulty < 0")
    suspend fun fixDifficultyTooLow(): Int

    /** difficulty > 2 olanları 2 yap. */
    @Query("UPDATE questions SET difficulty = 2 WHERE difficulty > 2")
    suspend fun fixDifficultyTooHigh(): Int

    /** Aynı grade+subject içinde en çok tekrar eden 20 stemHash (ACTIVE). */
    @Query(
        """
        SELECT grade, subject, stemHash, COUNT(*) AS cnt
        FROM questions
        WHERE isActive = 1 AND grade BETWEEN 1 AND 7
        GROUP BY grade, subject, stemHash
        HAVING cnt > 1
        ORDER BY cnt DESC
        LIMIT 20
        """
    )
    suspend fun getTopDuplicateStemHashes(): List<DuplicateStemHashRow>

    /** Tüm soru tablosunu sil – DEBUG/RESET için kullanılır. */
    @Query("DELETE FROM questions")
    suspend fun deleteAll()

    /** GENERAL havuzunu sil; LGS (examType='LGS') satırları korunur. Force reseed GENERAL için. */
    @Query("DELETE FROM questions WHERE COALESCE(examType, 'GENERAL') != 'LGS'")
    suspend fun deleteGeneralQuestions()

    // ---- LGS pool diagnostics ----

    /** Toplam aktif LGS soru sayısı (examType=LGS). */
    @Query("SELECT COUNT(*) FROM questions WHERE isActive = 1 AND COALESCE(examType, 'GENERAL') = 'LGS'")
    suspend fun countLgsActive(): Int

    /** LGS havuzunda ders bazında ACTIVE soru sayıları. */
    @Query(
        """
        SELECT subject, COUNT(*) AS count
        FROM questions
        WHERE isActive = 1 AND COALESCE(examType, 'GENERAL') = 'LGS'
        GROUP BY subject
        ORDER BY subject
        """
    )
    suspend fun getLgsCountsBySubject(): List<LgsSubjectCount>

    /** LGS havuzunda zorluk bazında ACTIVE soru sayıları. */
    @Query(
        """
        SELECT difficulty, COUNT(*) AS count
        FROM questions
        WHERE isActive = 1 AND COALESCE(examType, 'GENERAL') = 'LGS'
        GROUP BY difficulty
        ORDER BY difficulty
        """
    )
    suspend fun getLgsCountsByDifficulty(): List<LgsDifficultyCount>

    // ---- LGS quality debug ----

    /** LGS inactive due to low quality. */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE COALESCE(examType, 'GENERAL') = 'LGS'
        AND isActive = 0
        AND COALESCE(deactivationReason, '') = 'low_lgs_quality'
        """
    )
    suspend fun countLgsInactiveLowQuality(): Int

    /** LGS avg qualityScore by subject (all LGS questions). */
    @Query(
        """
        SELECT subject, AVG(qualityScore) AS avgQualityScore
        FROM questions
        WHERE COALESCE(examType, 'GENERAL') = 'LGS'
        GROUP BY subject
        """
    )
    suspend fun getLgsAvgQualityBySubject(): List<LgsAvgQualityBySubject>

    /** LGS new-generation-like count and total by subject. */
    @Query(
        """
        SELECT subject,
            SUM(CASE WHEN isNewGenerationLike = 1 THEN 1 ELSE 0 END) AS newGenCount,
            COUNT(*) AS totalCount
        FROM questions
        WHERE COALESCE(examType, 'GENERAL') = 'LGS'
        GROUP BY subject
        """
    )
    suspend fun getLgsNewGenCountBySubject(): List<LgsNewGenCountBySubject>

    /** LGS difficulty counts for a single subject (e.g. mat). */
    @Query(
        """
        SELECT difficulty, COUNT(*) AS count
        FROM questions
        WHERE isActive = 1 AND COALESCE(examType, 'GENERAL') = 'LGS' AND subject = :subject
        GROUP BY difficulty
        ORDER BY difficulty
        """
    )
    suspend fun getLgsCountsByDifficultyForSubject(subject: String): List<LgsDifficultyCount>

    /** LGS questionType counts for a single subject (e.g. mat). */
    @Query(
        """
        SELECT COALESCE(type, 'UNKNOWN') AS questionType, COUNT(*) AS count
        FROM questions
        WHERE isActive = 1 AND COALESCE(examType, 'GENERAL') = 'LGS' AND subject = :subject
        GROUP BY type
        ORDER BY count DESC
        """
    )
    suspend fun getLgsCountsByQuestionTypeForSubject(subject: String): List<LgsQuestionTypeCount>

    /** LGS inactive (low quality) count for a single subject. */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE COALESCE(examType, 'GENERAL') = 'LGS'
        AND subject = :subject
        AND isActive = 0
        AND COALESCE(deactivationReason, '') = 'low_lgs_quality'
        """
    )
    suspend fun countLgsInactiveLowQualityBySubject(subject: String): Int

    // ---- DEBUG: grade 6 mat GENERAL pipeline diagnostics ----

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE grade = 6 AND LOWER(subject) = 'mat' AND COALESCE(examType, 'GENERAL') = 'GENERAL'
        """
    )
    suspend fun countGrade6MatGeneral(): Int

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE grade = 6 AND LOWER(subject) = 'mat' AND COALESCE(examType, 'GENERAL') = 'GENERAL' AND isActive = 1
        """
    )
    suspend fun countGrade6MatGeneralActive(): Int

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE grade = 6 AND LOWER(subject) = 'mat' AND COALESCE(examType, 'GENERAL') = 'GENERAL' AND isActive = 0
        """
    )
    suspend fun countGrade6MatGeneralInactive(): Int

    /** Inactive g6 mat where QuestionQualityGate-style reasons apply. */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE grade = 6 AND LOWER(subject) = 'mat' AND COALESCE(examType, 'GENERAL') = 'GENERAL' AND isActive = 0
        AND COALESCE(deactivationReason, '') IN (
            'too_trivial', 'too_short', 'too_basic', 'too_simple_math', 'too_memorization'
        )
        """
    )
    suspend fun countGrade6MatInactiveQualityGateReasons(): Int

    /** Top inactive reasons for g6 mat GENERAL (DEBUG). */
    @Query(
        """
        SELECT COALESCE(deactivationReason, '') AS reason, COUNT(*) AS cnt FROM questions
        WHERE grade = 6 AND LOWER(subject) = 'mat' AND COALESCE(examType, 'GENERAL') = 'GENERAL' AND isActive = 0
        GROUP BY deactivationReason
        ORDER BY cnt DESC
        LIMIT 5
        """
    )
    suspend fun getGrade6MatTopInactiveReasons(): List<DeactivationReasonCountRow>

    /** Duplicate stemHash groups for g6 mat GENERAL (active+inactive). */
    @Query(
        """
        SELECT grade, subject, stemHash, COUNT(*) AS cnt FROM questions
        WHERE grade = 6 AND LOWER(subject) = 'mat' AND COALESCE(examType, 'GENERAL') = 'GENERAL'
        GROUP BY stemHash
        HAVING cnt > 1
        ORDER BY cnt DESC
        LIMIT 10
        """
    )
    suspend fun getGrade6MatDuplicateStemGroups(): List<DuplicateStemHashRow>

    @Query(
        """
        SELECT id FROM questions
        WHERE grade = 6 AND LOWER(subject) = 'mat' AND stemHash = :stemHash
        LIMIT 3
        """
    )
    suspend fun getSampleIdsForMat6StemHash(stemHash: String): List<String>

    // ---- DEBUG: rows ingested from assets grade6_mat.json (sourcePack tag) ----

    @Query("SELECT COUNT(*) FROM questions WHERE sourcePack = :pack")
    suspend fun countBySourcePack(pack: String): Int

    @Query("SELECT COUNT(*) FROM questions WHERE sourcePack = :pack AND isActive = 1")
    suspend fun countActiveBySourcePack(pack: String): Int

    @Query("SELECT COUNT(*) FROM questions WHERE sourcePack = :pack AND isActive = 0")
    suspend fun countInactiveBySourcePack(pack: String): Int

    @Query("SELECT COUNT(DISTINCT stemHash) FROM questions WHERE sourcePack = :pack")
    suspend fun countDistinctStemHashBySourcePack(pack: String): Int

    /** Inactive rows deactivated by QuestionQualityGate-style reasons (parse-time). */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE sourcePack = :pack AND isActive = 0
        AND COALESCE(deactivationReason, '') IN (
            'too_trivial', 'too_short', 'too_basic', 'too_simple_math', 'too_memorization'
        )
        """
    )
    suspend fun countRejectedQualityGateBySourcePack(pack: String): Int

    /**
     * Inactive rows deactivated by [QuestionQualityGate] (parse-time reasons), core subjects only.
     */
    @Query(
        """
        SELECT * FROM questions
        WHERE isActive = 0
        AND COALESCE(deactivationReason, '') IN (
            'too_short', 'too_trivial', 'too_basic', 'too_simple_math', 'too_memorization'
        )
        AND grade BETWEEN 1 AND 7
        AND LOWER(subject) IN ('mat','turkce','fen','sosyal','ing')
        """
    )
    suspend fun getGateFailedInactiveCoreSubjects(): List<QuestionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE isActive = 0
        AND COALESCE(deactivationReason, '') IN (
            'too_short', 'too_trivial', 'too_basic', 'too_simple_math', 'too_memorization'
        )
        AND grade BETWEEN 1 AND 7
        AND LOWER(subject) IN ('mat','turkce','fen','sosyal','ing')
        """
    )
    suspend fun countInactiveGateReasonsCore(): Int

    // ---- Content quality tier (debug / audit) ----

    @Query(
        """
        SELECT COALESCE(qualityTier, 'MEDIUM') AS qualityTier, COUNT(*) AS count
        FROM questions WHERE isActive = 1
        GROUP BY COALESCE(qualityTier, 'MEDIUM')
        """
    )
    suspend fun countActiveByQualityTier(): List<QualityTierCountRow>

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE isActive = 1 AND grade = :grade AND LOWER(subject) = LOWER(:subject)
        AND COALESCE(qualityTier, 'MEDIUM') IN ('MEDIUM', 'HARD', 'BORDERLINE')
        """
    )
    suspend fun countActiveMediumHardByGradeSubject(grade: Int, subject: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE isActive = 1 AND grade = :grade AND LOWER(subject) = LOWER(:subject)
        AND COALESCE(qualityTier, 'MEDIUM') = 'EASY'
        """
    )
    suspend fun countActiveEasyByGradeSubject(grade: Int, subject: String): Int

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE isActive = 1 AND qualityFlagsJson LIKE '%weak_distractors%'
        """
    )
    suspend fun countActiveWithWeakDistractorFlag(): Int

    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE isActive = 0 AND qualityFlagsJson LIKE '%weak_template_cluster%'
        """
    )
    suspend fun countInactiveWeakTemplateFlag(): Int

    /** Strict playable pool (matches candidate picker filters). */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE isActive = 1
        AND (unservableReason IS NULL OR unservableReason = '')
        AND COALESCE(qualityTier, 'MEDIUM') IN ('MEDIUM', 'HARD', 'BORDERLINE')
        """
    )
    suspend fun countPlayableStrictPool(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE COALESCE(unservableReason, '') = 'TRIVIAL'")
    suspend fun countRejectedTrivial(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE COALESCE(unservableReason, '') = 'LOW_REASONING'")
    suspend fun countRejectedLowReasoning(): Int

    @Query("SELECT COUNT(*) FROM questions WHERE COALESCE(unservableReason, '') = 'WEAK_DISTRACTORS'")
    suspend fun countRejectedWeakDistractors(): Int

    // ---- DataIntegrityChecker: bulk SQL-side detection ----

    @Query(
        """
        UPDATE questions SET unservableReason = 'DATA_CORRUPT_PLACEHOLDER'
        WHERE unservableReason IS NULL AND isActive = 1
        AND (
          optionsJson LIKE '%Se\u00e7enek A%' OR optionsJson LIKE '%Se\u00e7enek B%'
          OR optionsJson LIKE '%Se\u00e7enek C%' OR optionsJson LIKE '%Se\u00e7enek D%'
          OR optionsJson LIKE '%Option A%' OR optionsJson LIKE '%Option B%'
          OR optionsJson LIKE '%Option C%' OR optionsJson LIKE '%Option D%'
          OR optionsJson LIKE '%Cevap A%' OR optionsJson LIKE '%Cevap B%'
          OR optionsJson LIKE '%Cevap C%' OR optionsJson LIKE '%Cevap D%'
          OR optionsJson LIKE '%\u015e\u0131k A%' OR optionsJson LIKE '%\u015e\u0131k B%'
          OR optionsJson LIKE '%\u015e\u0131k C%' OR optionsJson LIKE '%\u015e\u0131k D%'
        )
        """
    )
    suspend fun markPlaceholderOptions(): Int

    /** Hard delete questions whose options contain any placeholder pattern. */
    @Query(
        """
        DELETE FROM questions
        WHERE (
          optionsJson LIKE '%Se\u00e7enek A%' OR optionsJson LIKE '%Se\u00e7enek B%'
          OR optionsJson LIKE '%Se\u00e7enek C%' OR optionsJson LIKE '%Se\u00e7enek D%'
          OR optionsJson LIKE '%Option A%' OR optionsJson LIKE '%Option B%'
          OR optionsJson LIKE '%Option C%' OR optionsJson LIKE '%Option D%'
          OR optionsJson LIKE '%Cevap A%' OR optionsJson LIKE '%Cevap B%'
          OR optionsJson LIKE '%Cevap C%' OR optionsJson LIKE '%Cevap D%'
          OR optionsJson LIKE '%\u015e\u0131k A%' OR optionsJson LIKE '%\u015e\u0131k B%'
          OR optionsJson LIKE '%\u015e\u0131k C%' OR optionsJson LIKE '%\u015e\u0131k D%'
        )
        """
    )
    suspend fun deleteQuestionsWithPlaceholderOptions(): Int

    /** Hard delete questions whose optionsJson has fewer than 4 meaningful entries (too few options). */
    @Query(
        """
        DELETE FROM questions
        WHERE (optionsJson = '[]' OR length(trim(optionsJson)) < 10)
        """
    )
    suspend fun deleteQuestionsWithTooFewOptions(): Int


    @Query(
        """
        UPDATE questions SET unservableReason = 'DATA_CORRUPT_SHORT_STEM'
        WHERE unservableReason IS NULL AND isActive = 1
        AND length(trim(questionText)) < 15
        """
    )
    suspend fun markShortStems(): Int

    @Query(
        """
        UPDATE questions SET unservableReason = 'DATA_CORRUPT_TOO_FEW_OPTIONS'
        WHERE unservableReason IS NULL AND isActive = 1
        AND (optionsJson = '[]' OR length(trim(optionsJson)) < 10)
        """
    )
    suspend fun markEmptyOptions(): Int

    @Query(
        """
        SELECT grade, subject, COUNT(*) AS count FROM questions
        WHERE isActive = 1 AND (unservableReason IS NULL OR unservableReason = '')
        GROUP BY grade, subject ORDER BY grade, subject
        """
    )
    suspend fun getServableCountsByGradeSubject(): List<GradeSubjectCount>

    @Query("SELECT COUNT(*) FROM questions WHERE unservableReason LIKE 'DATA_CORRUPT%'")
    suspend fun countDataCorrupt(): Int

    /** Servable count for a specific grade (all subjects). */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE isActive = 1 AND grade = :grade
        AND (unservableReason IS NULL OR unservableReason = '')
        AND COALESCE(examType, 'GENERAL') != 'LGS'
        """
    )
    suspend fun countServableByGrade(grade: Int): Int

    /** Servable LGS count (all subjects). */
    @Query(
        """
        SELECT COUNT(*) FROM questions
        WHERE isActive = 1
        AND COALESCE(examType, 'GENERAL') = 'LGS'
        AND (unservableReason IS NULL OR unservableReason = '')
        """
    )
    suspend fun countServableLgs(): Int

    /** Count questions with a specific unservableReason (for diagnostics). */
    @Query("SELECT COUNT(*) FROM questions WHERE unservableReason = :reason")
    suspend fun countByUnservableReason(reason: String): Int

    /** Count by examType (for diagnostics). */
    @Query("SELECT COUNT(*) FROM questions WHERE COALESCE(examType, 'GENERAL') = :examType")
    suspend fun countByExamType(examType: String): Int

    /**
     * Repair pool damage from progressive runtime quarantine:
     * clear LOW_QUALITY_QUARANTINED and DATA_CORRUPT_WEAK_DISTRACTOR marks
     * so these questions return to the servable pool.
     */
    @Query(
        """
        UPDATE questions SET unservableReason = NULL
        WHERE unservableReason IN ('LOW_QUALITY_QUARANTINED', 'DATA_CORRUPT_WEAK_DISTRACTOR')
        """
    )
    suspend fun clearProgressiveQuarantineDamage(): Int
}
