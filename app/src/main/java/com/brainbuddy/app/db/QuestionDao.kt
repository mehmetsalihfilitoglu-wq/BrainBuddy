package com.brainbuddy.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuestionEntity>)

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
}
