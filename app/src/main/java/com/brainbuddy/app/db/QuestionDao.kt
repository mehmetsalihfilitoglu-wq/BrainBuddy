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
    @Query("SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND subject = :subject")
    suspend fun getByGradeSubject(grade: Int, subject: String): List<QuestionEntity>

    /** Sınıf + ders + zorluk filtresi. */
    @Query("SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND subject = :subject AND difficulty = :difficulty")
    suspend fun getByGradeSubjectDifficulty(grade: Int, subject: String, difficulty: Int): List<QuestionEntity>

    /** Tüm sınıf havuzu (grade 2-8 için test oluşturma). */
    @Query("SELECT * FROM questions WHERE isActive = 1 AND grade = :grade AND grade > 0")
    suspend fun getByGrade(grade: Int): List<QuestionEntity>

    /**
     * Havuz doğrulama için grade+subject bazında COUNT.
     * Sadece aktif ve 2..8. sınıflar.
     */
    @Query(
        """
        SELECT grade, subject, COUNT(*) AS count
        FROM questions
        WHERE isActive = 1 AND grade BETWEEN 2 AND 8
        GROUP BY grade, subject
        """
    )
    suspend fun getCountsByGradeSubject(): List<GradeSubjectCount>

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

    /** 2..8 aralığı dışındaki tüm grade değerlerinin toplam sayısı (0,1,9+ vs). */
    @Query("SELECT COUNT(*) FROM questions WHERE grade < 2 OR grade > 8")
    suspend fun countInvalidGrades(): Int

    /** Geçersiz grade'leri (2..8 dışı) hedef grade'e taşımak için acil debug fix. */
    @Query("UPDATE questions SET grade = :target WHERE grade < 2 OR grade > 8")
    suspend fun fixInvalidGrades(target: Int)

    @Query("UPDATE questions SET isActive=1 WHERE isActive!=1")
    suspend fun forceActivateAll()

    @Query("UPDATE questions SET difficulty=2 WHERE difficulty>2")
    suspend fun clampDifficulty()

    /** Tüm soru tablosunu sil – DEBUG/RESET için kullanılır. */
    @Query("DELETE FROM questions")
    suspend fun deleteAll()
}
