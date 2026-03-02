package com.brainbuddy.app.core

import android.app.Activity
import android.content.Intent
import com.brainbuddy.app.ui.PinLockActivity

/**
 * Central guard for Parent-only routes.
 * If current mode is not ParentMode, redirect to ParentPinScreen (PinLockActivity).
 */
object ParentAccessGuard {

    /**
     * Parent-only Activities that require PIN before entry.
     */
    val PARENT_ONLY_ACTIVITIES = setOf(
        "com.brainbuddy.app.ui.SettingsActivity",
        "com.brainbuddy.app.reward.RewardContractActivity",
        "com.brainbuddy.app.ui.BlockedAppsActivity",
        "com.brainbuddy.app.ui.TimeLimitsActivity",
        "com.brainbuddy.app.ui.QuizSettingsActivity",
        "com.brainbuddy.app.ui.ParentHubActivity",
        "com.brainbuddy.app.ui.ProtectionInactiveActivity",
        "com.brainbuddy.app.ui.PermissionsChecklistActivity",
        "com.brainbuddy.app.ui.ReportsActivity",
        "com.brainbuddy.app.ui.SchedulesActivity",
        "com.brainbuddy.app.ui.ProfileManageActivity",
        "com.brainbuddy.app.ui.SystemHealthActivity",
        "com.brainbuddy.app.junior.JuniorSettingsActivity",
        "com.brainbuddy.app.junior.JuniorReportActivity"
    )

    /**
     * Call from onCreate of Parent-only Activity.
     * Redirects to PinLockActivity; on success user will land on targetActivity.
     * @return true if access is allowed, false if redirected to PIN screen (caller should finish()).
     */
    fun checkAndRedirect(activity: Activity, targetActivity: Class<*>? = null): Boolean {
        if (AppModeManager.isParentMode()) return true

        val target = targetActivity ?: activity::class.java
        val intent = Intent(activity, PinLockActivity::class.java).apply {
            putExtra(PinLockActivity.EXTRA_MODE, "verify")
            putExtra(PinLockActivity.EXTRA_TARGET, target.simpleName)
            activity.intent?.extras?.let { extras ->
                putExtra(PinLockActivity.EXTRA_TARGET_EXTRAS, extras)
            }
        }
        activity.startActivity(intent)
        activity.finish()
        return false
    }
}
