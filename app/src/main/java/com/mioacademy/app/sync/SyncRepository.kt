package com.mioacademy.app.sync

/**
 * The cloud boundary. [LocalMirrorSyncRepository] implements it on-device so the
 * whole sync engine runs and is testable today; a `FirestoreSyncRepository` will
 * implement the same contract (per-user document collections, server timestamps)
 * with no change to [SyncEngine].
 *
 * Contract:
 *  - [pull] returns records changed strictly after [since] for this user.
 *  - [push] upserts records, honouring last-write-wins by [SyncRecord.updatedAt].
 *  - [serverTimeMs] is the authoritative clock the client stores as its next cursor.
 */
interface SyncRepository {
    suspend fun pull(userId: String, since: Long): List<SyncRecord>
    suspend fun push(userId: String, records: List<SyncRecord>): PushOutcome
    suspend fun serverTimeMs(): Long
}

data class PushOutcome(val acceptedAt: Long, val accepted: Int)
