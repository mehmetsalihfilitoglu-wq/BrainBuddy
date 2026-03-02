package com.brainbuddy.app.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: TestSnapshotEntity)

    @Query("SELECT * FROM test_snapshots ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getLastSnapshots(limit: Int = 20): List<TestSnapshotEntity>

    @Query("SELECT * FROM test_snapshots WHERE testId = :testId LIMIT 1")
    suspend fun getSnapshot(testId: String): TestSnapshotEntity?
}
