package com.brainbuddy.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class WrongAnswerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(entity: WrongAnswerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(entities: List<WrongAnswerEntity>)

    /** Unlock a single wrong answer. Inserts or replaces (handles old tests without pre-seeded rows). */
    open suspend fun unlockWrongAnswer(testId: String, questionId: String) {
        insert(WrongAnswerEntity(testId = testId, questionId = questionId, isUnlocked = true, unlockedAt = System.currentTimeMillis()))
    }

    @Query("UPDATE wrong_answers SET isUnlocked = 1, unlockedAt = :unlockedAt WHERE testId = :testId")
    abstract suspend fun unlockAllWrongsForAttempt(testId: String, unlockedAt: Long)

    @Query("SELECT * FROM wrong_answers WHERE testId = :testId ORDER BY questionId")
    fun getWrongsForAttempt(testId: String): Flow<List<WrongAnswerEntity>>

    @Query("SELECT * FROM wrong_answers WHERE testId = :testId AND questionId = :questionId LIMIT 1")
    suspend fun get(testId: String, questionId: String): WrongAnswerEntity?
}
