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
}
