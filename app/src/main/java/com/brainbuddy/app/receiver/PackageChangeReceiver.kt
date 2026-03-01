package com.brainbuddy.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** Notify when packages change - BlockedAppsActivity refreshes app list. */
class PackageChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false) && intent.action == Intent.ACTION_PACKAGE_REMOVED) return
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_APP_LIST_DIRTY, true)
                    .apply()
            }
        }
    }

    companion object {
        private const val PREFS = "bb_package_refresh"
        const val KEY_APP_LIST_DIRTY = "app_list_dirty"

        fun clearDirtyFlag(context: Context) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_APP_LIST_DIRTY, false)
                .apply()
        }

        fun isDirty(context: Context): Boolean =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_APP_LIST_DIRTY, false)
    }
}
