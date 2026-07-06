package com.mioacademy.app.analytics

/**
 * The product-analytics event taxonomy — a single, stable vocabulary so events are
 * consistent from day one and a Firebase Analytics adapter can map them 1:1 later.
 * No vendor dependency lives in business logic; call sites use these constants via
 * [AnalyticsTracker].
 */
object AnalyticsEvents {
    // Lifecycle / onboarding
    const val APP_OPENED = "app_opened"
    const val ONBOARDING_STARTED = "onboarding_started"
    const val ONBOARDING_COMPLETED = "onboarding_completed"

    // Study areas
    const val STUDY_AREA_SELECTED = "study_area_selected"
    const val STUDY_AREA_SWITCHED = "study_area_switched"

    // Daily mission
    const val DAILY_MISSION_VIEWED = "daily_mission_viewed"
    const val DAILY_MISSION_STARTED = "daily_mission_started"
    const val DAILY_MISSION_COMPLETED = "daily_mission_completed"

    // Quiz / review
    const val QUIZ_STARTED = "quiz_started"
    const val QUIZ_COMPLETED = "quiz_completed"
    const val REVIEW_STARTED = "review_started"
    const val REVIEW_COMPLETED = "review_completed"
    const val WRONG_QUESTION_MASTERED = "wrong_question_mastered"

    // Progress / coach
    const val ACHIEVEMENT_UNLOCKED = "achievement_unlocked"
    const val EXAM_READINESS_VIEWED = "exam_readiness_viewed"
    const val PROGRESS_VIEWED = "progress_viewed"
    const val COACH_VIEWED = "coach_viewed"

    // Discovery
    const val UNIVERSITY_DISCOVERY_VIEWED = "university_discovery_viewed"
    const val UNIVERSITY_PROGRAM_OPENED = "university_program_opened"

    // Premium
    const val PREMIUM_PAYWALL_VIEWED = "premium_paywall_viewed"
    const val PREMIUM_CLICKED = "premium_clicked"
    const val PREMIUM_PURCHASED = "premium_purchased"

    // Reports / notifications
    const val WEEKLY_REPORT_VIEWED = "weekly_report_viewed"
    const val MONTHLY_REPORT_VIEWED = "monthly_report_viewed"
    const val NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
    const val NOTIFICATION_OPENED = "notification_opened"

    // Sync
    const val SYNC_STARTED = "sync_started"
    const val SYNC_COMPLETED = "sync_completed"
    const val SYNC_FAILED = "sync_failed"

    // Account / GDPR
    const val ACCOUNT_CREATED = "account_created"
    const val LOGIN_COMPLETED = "login_completed"
    const val LOGOUT = "logout"
    const val ACCOUNT_DELETED = "account_deleted"
    const val GDPR_EXPORT_REQUESTED = "gdpr_export_requested"

    // Account & Sync UX
    const val ACCOUNT_SCREEN_VIEWED = "account_screen_viewed"
    const val SIGN_UP_STARTED = "sign_up_started"
    const val SIGN_UP_COMPLETED = "sign_up_completed"
    const val SIGN_IN_STARTED = "sign_in_started"
    const val SIGN_IN_COMPLETED = "sign_in_completed"
    const val PASSWORD_RESET_REQUESTED = "password_reset_requested"
    const val MANUAL_SYNC_STARTED = "manual_sync_started"
    const val MANUAL_SYNC_COMPLETED = "manual_sync_completed"
    const val MANUAL_SYNC_FAILED = "manual_sync_failed"
    const val ACCOUNT_DELETE_STARTED = "account_delete_started"
    const val ACCOUNT_DELETE_COMPLETED = "account_delete_completed"

    // Common parameter keys
    const val PARAM_STUDY_AREA = "study_area"
    const val PARAM_SOURCE = "source"
    const val PARAM_COUNT = "count"
    const val PARAM_ACCURACY = "accuracy"
    const val PARAM_RESULT = "result"
}
