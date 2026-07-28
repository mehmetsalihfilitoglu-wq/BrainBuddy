package com.edumio.app.privacy

/**
 * GDPR / privacy data-rights boundary: export only.
 *
 * v1 ships no account, no cloud sync and no server-side user data, so there is deliberately no
 * deletion operation in this contract — on-device data is removed by uninstalling the app.
 * [LocalDataRightsService] does the one thing that is genuinely possible on-device today (a real
 * export) and never fakes a backend operation.
 */
interface DataRightsService {
    /** Export My Data — serialises all local user data to a JSON file. */
    suspend fun exportData(): ExportResult
}

sealed class ExportResult {
    data class Success(val filePath: String, val sizeBytes: Int) : ExportResult()
    data class Error(val message: String) : ExportResult()
}
