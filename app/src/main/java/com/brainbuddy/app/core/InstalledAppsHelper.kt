package com.brainbuddy.app.core

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.brainbuddy.app.R

/**
 * Helper for working with installed launcher apps only.
 * Never uses hardcoded package names. Never crashes on missing apps.
 */
object InstalledAppsHelper {

    fun getInstalledApps(pm: PackageManager): List<ApplicationInfo> {
        return try {
            // Use flag 0 instead of GET_META_DATA — metadata parsing is extremely expensive
            // and unnecessary for listing apps. GET_META_DATA forces full manifest parse
            // for every app, adding 2-3 seconds on devices with 100+ packages.
            pm.getInstalledApplications(0)
                .filter { app ->
                    val flags = app.flags
                    val isSystem = (flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    val isUpdatedSystem = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                    !isSystem || isUpdatedSystem
                }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Fast path: get only launchable apps via launcher intent query.
     * Returns package names of apps that appear in the launcher.
     * Much faster than getInstalledApps + filter because the system
     * resolves the intent without parsing full metadata.
     */
    fun getLaunchablePackages(pm: PackageManager): List<ResolveInfo> {
        return try {
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(launcherIntent, 0)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Resolve package name to app label. Returns package name on failure. */
    fun getAppLabel(pm: PackageManager, app: ApplicationInfo): String {
        return try {
            pm.getApplicationLabel(app).toString()
        } catch (e: Exception) {
            app.packageName
        }
    }

    /** Resolve label from ResolveInfo — faster than ApplicationInfo when available. */
    fun getAppLabel(pm: PackageManager, ri: ResolveInfo): String {
        return try {
            ri.loadLabel(pm).toString()
        } catch (e: Exception) {
            ri.activityInfo?.packageName ?: ""
        }
    }

    /** Resolve package name to app icon. Returns generic fallback on failure. */
    fun getAppIcon(pm: PackageManager, packageName: String, context: Context): Drawable? {
        return try {
            pm.getApplicationIcon(packageName)
        } catch (e: Exception) {
            ContextCompat.getDrawable(context, R.drawable.ic_default_app)
        }
    }

    /** Get icon from ApplicationInfo. Returns generic fallback on failure. */
    fun getAppIcon(pm: PackageManager, app: ApplicationInfo, context: Context): Drawable? {
        return try {
            pm.getApplicationIcon(app)
        } catch (e: Exception) {
            ContextCompat.getDrawable(context, R.drawable.ic_default_app)
        }
    }

    /** Check if package is installed and has launcher intent. */
    fun isInstalledWithLauncher(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, 0)
            pm.getLaunchIntentForPackage(packageName) != null
        } catch (_: Exception) {
            false
        }
    }
}
