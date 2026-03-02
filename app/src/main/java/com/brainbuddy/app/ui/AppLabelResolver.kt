package com.brainbuddy.app.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Resolves package names to user-friendly app labels.
 * Falls back to shortened package name if unavailable.
 */
object AppLabelResolver {

    private val cache = mutableMapOf<String, String>()

    fun getLabel(context: Context, packageName: String): String {
        if (packageName.isBlank()) return "Bilinmeyen"
        cache[packageName]?.let { return it }
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val label = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getApplicationLabel(appInfo).toString()
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
            }
            val result = label.ifBlank { shortenPackageName(packageName) }
            cache[packageName] = result
            result
        } catch (_: PackageManager.NameNotFoundException) {
            shortenPackageName(packageName)
        } catch (_: Exception) {
            shortenPackageName(packageName)
        }
    }

    private fun shortenPackageName(pkg: String): String {
        val last = pkg.substringAfterLast('.')
        return if (last.length >= 4) last.take(12) else pkg.takeLast(12)
    }

    fun clearCache() = cache.clear()
}
