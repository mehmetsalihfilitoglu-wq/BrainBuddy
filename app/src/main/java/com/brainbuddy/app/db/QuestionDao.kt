package com.brainbuddy.app.db

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
    val qualityScore: Int
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
    val skill: String
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

@Dao
interface QuestionDao {

    /** Genel amaçlı insert - import vb. için REPLACE davranışı korunur. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

    /** İlk seed sırasında id bazlı INSERT IGNORE. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(questions: List<QuestionEntity>): LongArray

    @Query("SELECT * FROM questions WHERE isActive = 1 ORDER BY id")
    suspend fun getActiveQuestions(): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (:ids) AND isActive = 1")
    suspend fun getQuestionsByIds(ids: List<String>): List<QuestionEntity>

    /** Sınıf bazlı havuz: grade ve subject'e göre (index kullanır). */
    @Query("SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND subject = :subject")
    suspend fun getByGradeSubject(grade: Int, subject: String): List<QuestionEntity>

    /** Sınıf + ders + zorluk filtresi. */
    @Query("SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND subject = :subject AND difficulty = :difficulty")
    suspend fun getByGradeSubjectDifficulty(grade: Int, subject: String, difficulty: Int): List<QuestionEntity>

    /**
     * Quiz picker candidate pool (LIMIT 200).
     * Only a lightweight subset of columns is fetched for fast in-memory filtering.
     */
    @Query(
        """
        SELECT id, subject, difficulty, grade, stemHash, stemNormalized, type, skill
        FROM questions
        WHERE grade = :grade
        AND subject = :subject
        AND difficulty = :difficulty
        AND isActive = 1
        LIMIT 2000
        """
    )
    suspend fun getCandidatePoolByGradeSubjectDifficulty(
        grade: Int,
        subject: String,
        difficulty: Int
    ): List<QuestionCandidateRow>

    /**
     * Candidate pool per subject (LIMIT 200). Any difficulty; partition in memory.
     * Used by grade-based picker to avoid scanning the entire database.
     */
    @Query(
        """
        SELECT id, subject, difficulty, grade, stemHash, stemNormalized, type, skill
        FROM questions
        WHERE grade = :grade
        AND subject = :subject
        AND isActive = 1
        LIMIT 2000
        """
    )
    suspend fun getCandidatePoolByGradeSubject(
        grade: Int,
        subject: String
    ): List<QuestionCandidateRow>

    /**
     * LGS pool: questions with examType = 'LGS' and given subject.
     * Used by LGS picker - does NOT use grade.
     */
    @Query(
        """
        SELECT id, subject, difficulty, grade, stemHash, stemNormalized, type, skill
        FROM questions
        WHERE COALESCE(examType, 'GENERAL') = 'LGS'
        AND subject = :subject
        AND isActive = 1
        LIMIT 2000
        """
    )
    suspend fun getCandidatePoolByLgsSubject(subject: String): List<QuestionCandidateRow>

    /**
     * LGS pool with qualityScore for blueprint-based planner.
     * Ordered by qualityScore DESC to prefer highest-quality items.
     */
    @Query(
        """
        SELECT id, subject, difficulty, grade, stemHash, stemNormalized, type, skill,
               COALESCE(qualityScore, 0) AS qualityScore
        FROM questions
        WHERE COALESCE(examType, 'GENERAL') = 'LGS'
        AND subject = :subject
        AND isActive = 1
        ORDER BY qualityScore DESC
        LIMIT 300
        """
    )
    suspend fun getLgsCandidatePoolWithQuality(subject: String): List<LgsCandidateRow>

    /** Tüm sınıf havuzu (grade 1-7 için test oluşturma). */
    @Query("SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND grade > 0")
    suspend fun getByGrade(grade: Int): List<QuestionEntity>

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

    /** Tüm sorularda ders bazında toplam sayı (reseed özeti). */
    @Query("SELECT subject, COUNT(*) AS count FROM questions GROUP BY subject ORDER BY subject")
    suspend fun getCountsBySubject(): List<SubjectCount>

    /** Import sonrası debug: grade/subject/difficulty bazında ACTIVE sayıları. */
    @Query(
        """
        SELECT grade, subject, difficulty, COUNT(*) AS count
        FROM questions
        WHERE isActive = 1 AND grade BETWEEN 1 AND 7
        GROUP BY grade, subject, difficulty
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

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade")
    suspend fun countByGrade(grade: Int): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND isActive = 1")
    suspend fun countActiveByGrade(grade: Int): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND subject = :subject")
    suspend fun countByGradeSubject(grade: Int, subject: String): Int

    @Query("SELECT COUNT(*) FROM questions WHERE grade = :grade AND subject = :subject AND isActive = 1")
    suspend fun countActiveByGradeSubject(grade: Int, subject: String): Int

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
}
