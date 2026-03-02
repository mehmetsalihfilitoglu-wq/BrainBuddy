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
}
