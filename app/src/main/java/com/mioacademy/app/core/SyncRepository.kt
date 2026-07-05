package com.mioacademy.app.core

/**
 * Cloud sync interface. No-op default; later can connect to Firebase/API.
 * Must not break app if cloud absent.
 */
interface SyncRepository {
    suspend fun syncProfiles(): Result<Unit> = Result.success(Unit)
    suspend fun syncBlockedApps(): Result<Unit> = Result.success(Unit)
    suspend fun syncSettings(): Result<Unit> = Result.success(Unit)
}

class DefaultSyncRepository : SyncRepository {
    override suspend fun syncProfiles() = Result.success(Unit)
    override suspend fun syncBlockedApps() = Result.success(Unit)
    override suspend fun syncSettings() = Result.success(Unit)
}
