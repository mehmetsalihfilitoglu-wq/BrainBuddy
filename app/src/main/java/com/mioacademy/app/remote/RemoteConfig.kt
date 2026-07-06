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
    // Reports
    const val WEEKLY_REPORTS_ENABLED = "weekly_reports_enabled"
    const val MONTHLY_REPORTS_ENABLED = "monthly_reports_enabled"
    const val WEEKLY_REPORT_HOUR = "weekly_report_hour"          // local hour, 0-23
    const val WEEKLY_REPORT_DOW = "weekly_report_day_of_week"    // Calendar.SUNDAY = 1
    const val MONTHLY_REPORT_DOM = "monthly_report_day_of_month" // 1

    // Sync / platform
    const val CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    const val MAINTENANCE_MODE = "maintenance_mode"
    const val MIN_SUPPORTED_VERSION_CODE = "min_supported_version_code"

    // Premium / paywall
    const val PAYWALL_VARIANT = "paywall_variant"               // "value" | "trial" | ...
    const val PAYWALL_HEADLINE = "paywall_headline"
    const val PAYWALL_SUBTEXT = "paywall_subtext"
    const val PREMIUM_MONTHLY_PRICE = "premium_monthly_price"

    // Campaigns / banners
    const val CAMPAIGN_BANNER_ENABLED = "campaign_banner_enabled"
    const val CAMPAIGN_BANNER_TEXT = "campaign_banner_text"
    const val DISCOVERY_BANNER_ENABLED = "discovery_banner_enabled"
    const val DISCOVERY_BANNER_TEXT = "discovery_banner_text"

    // Weekly challenge
    const val WEEKLY_CHALLENGE_ENABLED = "weekly_challenge_enabled"
    const val WEEKLY_CHALLENGE_TARGET = "weekly_challenge_target"

    // Daily mission
    const val DAILY_MISSION_QUESTION_COUNT = "daily_mission_question_count"
    const val DAILY_MISSION_MINUTES = "daily_mission_minutes_estimate"

    // Notification timing
    const val DAILY_REMINDER_HOUR = "daily_reminder_hour"
    const val STREAK_WARNING_HOUR = "streak_warning_hour"

    // Feature flags
    const val FEATURE_DISCOVERY = "feature_discovery_enabled"
    const val FEATURE_LEAGUE = "feature_league_enabled"

    // A/B testing
    const val AB_TEST_BUCKET = "ab_test_bucket"                 // "control" | "variant_a" | ...

    // Content versions (cache-busting for CMS)
    const val CMS_CONTENT_VERSION = "cms_content_version"
    const val EXAM_GUIDE_VERSION = "exam_guide_version"
    const val QUESTION_BANK_VERSION = "question_bank_version"

    val defaults: Map<String, Any> = mapOf(
        WEEKLY_REPORTS_ENABLED to true,
        MONTHLY_REPORTS_ENABLED to true,
        WEEKLY_REPORT_HOUR to 20L,
        WEEKLY_REPORT_DOW to 1L,          // Sunday
        MONTHLY_REPORT_DOM to 1L,
        CLOUD_SYNC_ENABLED to false,      // off until a real backend is live
        MAINTENANCE_MODE to false,
        MIN_SUPPORTED_VERSION_CODE to 1L,
        PAYWALL_VARIANT to "value",
        PAYWALL_HEADLINE to "Premium: senin kişisel sınav koçun",
        PAYWALL_SUBTEXT to "Daha akıllı hazırlan — daha fazlası değil, daha iyisi.",
        PREMIUM_MONTHLY_PRICE to "€4,99",
        CAMPAIGN_BANNER_ENABLED to false,
        CAMPAIGN_BANNER_TEXT to "",
        DISCOVERY_BANNER_ENABLED to false,
        DISCOVERY_BANNER_TEXT to "",
        WEEKLY_CHALLENGE_ENABLED to false,
        WEEKLY_CHALLENGE_TARGET to 50L,
        DAILY_MISSION_QUESTION_COUNT to 10L,
        DAILY_MISSION_MINUTES to 10L,
        DAILY_REMINDER_HOUR to 19L,
        STREAK_WARNING_HOUR to 20L,
        FEATURE_DISCOVERY to true,
        FEATURE_LEAGUE to true,
        AB_TEST_BUCKET to "control",
        CMS_CONTENT_VERSION to 1L,
        EXAM_GUIDE_VERSION to 1L,
        QUESTION_BANK_VERSION to 1L
    )
}
