package com.mioacademy.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for app_meta table.
 * Key-value store: db_seeded, global_test_index, recent_test_1_<profileId>, etc.
 */
@Entity(tableName = "app_meta")
data class AppMetaEntity(
    @PrimaryKey
    val key: String,
    val value: String
)
