package com.edumio.app.privacy

/**
 * GDPR / privacy data-rights boundary: export, local delete, cloud delete, account
 * deletion, and session revocation. [LocalDataRightsService] does everything that is
 * genuinely possible on-device today (real export, real local wipe) and is honest
 * about what needs the backend (cloud deletion). A Firebase-backed implementation
 * fulfils the same contract — a Cloud Function erases the user's Firestore data and
 * Firebase Auth account — so no UI changes when it goes live.
 *
 * Never fakes backend deletion: cloud operations return [DeletionResult.LocalDoneCloudPending]
 * until a backend is connected.
 */
interface DataRightsService {
    /** Export My Data — serialises all local user data to a JSON file. */
    suspend fun exportData(): ExportResult

    /** Delete all local user data (progress/settings/session); keeps bundled content. */
    suspend fun deleteLocalData(): DeletionResult

    /** Delete the user's cloud data (backend only). */
    suspend fun deleteCloudData(userId: String?): DeletionResult

    /** Full account deletion: local wipe + auth account + (later) cloud data. */
    suspend fun deleteAccount(userId: String?): DeletionResult

    /** Sign out / revoke the current session (all devices once backend is connected). */
    fun revokeSessions()
}

sealed class ExportResult {
    data class Success(val filePath: String, val sizeBytes: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}

sealed class DeletionResult {
    /** Everything requested was completed locally (no cloud involved). */
    object LocalDone : DeletionResult()
    /** Local data was deleted; cloud deletion is queued for when the backend is connected. */
    object LocalDoneCloudPending : DeletionResult()
    data class Error(val message: String) : DeletionResult()
}
