package com.mioacademy.app.remote

/**
 * Server-controlled configuration so behaviour can change without shipping an app
 * update — paywall variants, feature flags, minimum supported version, maintenance
 * mode, etc. [LocalRemoteConfig] serves the bundled defaults today; a Firebase
 * Remote Config adapter implements the same contract later.
 */
interface RemoteConfig {
    fun getString(key: String, default: String): String
    fun getBoolean(key: String, default: Boolean): Boolean
    fun getLong(key: String, default: Long): Long

    /** Fetches the latest values from the server. No-op locally; returns whether values changed. */
    suspend fun refresh(): Boolean
}

/** The known configuration keys + their safe defaults (single source of truth). */
object RemoteConfigKeys {
    const val WEEKLY_REPORTS_ENABLED = "weekly_reports_enabled"
    const val MONTHLY_REPORTS_ENABLED = "monthly_reports_enabled"
    const val CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    const val PAYWALL_VARIANT = "paywall_variant"          // "value" | "trial" | ...
    const val PREMIUM_MONTHLY_PRICE = "premium_monthly_price"
    const val MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"
    const val MAINTENANCE_MODE = "maintenance_mode"

    val defaults: Map<String, Any> = mapOf(
        WEEKLY_REPORTS_ENABLED to true,
        MONTHLY_REPORTS_ENABLED to true,
        CLOUD_SYNC_ENABLED to false,          // off until a real backend is live
        PAYWALL_VARIANT to "value",
        PREMIUM_MONTHLY_PRICE to "€4,99",
        MIN_SUPPORTED_VERSION_CODE to 1L,
        MAINTENANCE_MODE to false
    )
}
