package com.brainbuddy.app.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
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
