package com.edumio.app.dailychallenge

/**
 * Pure decision logic for the POST_NOTIFICATIONS runtime permission (no Android deps → testable).
 * We ask CONTEXTUALLY (after the user completes a challenge — a moment where reminders clearly help),
 * only on Android 13+, only when not already granted, and only once (respecting a prior denial).
 */
object NotificationPermissionPolicy {

    const val ANDROID_13 = 33

    /** The runtime permission only exists on Android 13+. Below that, notifications are always allowed. */
    fun isRuntimePermissionRequired(sdkInt: Int): Boolean = sdkInt >= ANDROID_13

    /** Whether to show the system permission prompt now. */
    fun shouldAsk(sdkInt: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
        isRuntimePermissionRequired(sdkInt) && !granted && !alreadyAsked

    /** Whether to surface a "reminders are off — open settings" path. */
    fun shouldOfferSettings(sdkInt: Int, granted: Boolean, alreadyAsked: Boolean): Boolean =
        isRuntimePermissionRequired(sdkInt) && !granted && alreadyAsked
}
