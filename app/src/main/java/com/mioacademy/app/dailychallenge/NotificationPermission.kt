package com.mioacademy.app.dailychallenge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.mioacademy.app.core.NotificationPrefs

/**
 * Thin Android wrapper over [NotificationPermissionPolicy]. Resolves live grant state and the
 * "already asked once" flag ([NotificationPrefs]), and can open the OS notification settings for
 * the recovery path when the permission was denied.
 */
object NotificationPermission {

    fun isGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < NotificationPermissionPolicy.ANDROID_13) return true
        return ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun shouldAsk(context: Context): Boolean = NotificationPermissionPolicy.shouldAsk(
        sdkInt = Build.VERSION.SDK_INT,
        granted = isGranted(context),
        alreadyAsked = NotificationPrefs(context).wasPermissionRequested(),
    )

    fun shouldOfferSettings(context: Context): Boolean = NotificationPermissionPolicy.shouldOfferSettings(
        sdkInt = Build.VERSION.SDK_INT,
        granted = isGranted(context),
        alreadyAsked = NotificationPrefs(context).wasPermissionRequested(),
    )

    fun markAsked(context: Context) = NotificationPrefs(context).setPermissionRequested()

    /** Opens this app's OS notification settings (recovery path when the user denied the prompt). */
    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(android.net.Uri.fromParts("package", context.packageName, null))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(intent) } catch (_: Throwable) { /* no settings activity */ }
    }
}
