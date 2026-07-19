package com.edumio.app.firebase

/**
 * Minimal cloud profile stored at `users/{uid}` (Phase 1). **No question or solution content is ever
 * uploaded** — this holds identity + preferences only. `createdAt`/`updatedAt` are Firestore server
 * timestamps set at write time; `uid`, `email`, `emailVerified`, `accountStatus` are server/derived and are
 * not client-writable (enforced by `firestore.rules`).
 */
data class UserProfile(
    val uid: String = "",
    val email: String? = null,
    val emailVerified: Boolean = false,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val authProvider: String = "EMAIL",
    val selectedExam: String? = null,
    val locale: String? = null,
    val timezone: String? = null,
    val accountStatus: String = STATUS_ACTIVE,
    val schemaVersion: Int = SCHEMA_VERSION,
) {
    companion object {
        const val COLLECTION = "users"
        const val SCHEMA_VERSION = 1
        const val STATUS_ACTIVE = "ACTIVE"
        const val STATUS_DELETION_PENDING = "DELETION_PENDING"

        /** Fields the client may set/update. Mirrors the whitelist in `firestore.rules`. */
        val CLIENT_WRITABLE_FIELDS = setOf(
            "displayName", "photoUrl", "authProvider", "selectedExam", "locale", "timezone",
            "schemaVersion", "updatedAt",
        )
    }
}
