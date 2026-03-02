package com.brainbuddy.app.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.brainbuddy.app.R
import com.brainbuddy.app.core.BlockedAppsStore
import com.brainbuddy.app.core.SocialPresetPackages
import com.brainbuddy.app.ui.BlockedAppsActivity

/**
 * When a social media app is installed, notifies parent to consider blocking.
 * Does NOT auto-block; parent must explicitly choose "Engelle" or "Yoksay".
 */
class SocialAppInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_PACKAGE_ADDED) return
        if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return

        val pkg = intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() } ?: return
        if (pkg == context.packageName) return
        if (pkg !in SocialPresetPackages.packageNames) return

        runCatching {
            val blocked = BlockedAppsStore(context).isBlocked(pkg)
            if (blocked) return@runCatching

            val appName = SocialPresetPackages.getAppLabel(context, pkg)
            showNotification(context, pkg, appName)
        }
    }

    private fun showNotification(context: Context, pkg: String, appName: String) {
        val channelId = "bb_new_social_app"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notif_channel_social_preset),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                ?.createNotificationChannel(channel)
        }

        val notifId = NOTIF_BASE_ID + (pkg.hashCode() and 0xFFFF)

        val openAppList = Intent(context, BlockedAppsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(BlockedAppsActivity.EXTRA_QUICK_BLOCK_PACKAGE, pkg)
            putExtra(BlockedAppsActivity.EXTRA_HIGHLIGHT_PACKAGE, pkg)
        }
        val contentPending = PendingIntent.getActivity(
            context,
            pkg.hashCode() and 0x7FFFFFFF,
            openAppList,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val blockIntent = Intent(context, BlockAppFromNotificationReceiver::class.java).apply {
            action = BlockAppFromNotificationReceiver.ACTION_BLOCK
            putExtra(BlockAppFromNotificationReceiver.EXTRA_PACKAGE, pkg)
            putExtra(BlockAppFromNotificationReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val blockPending = PendingIntent.getBroadcast(
            context,
            (pkg + "_block").hashCode() and 0x7FFFFFFF,
            blockIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, DismissSocialNotifReceiver::class.java).apply {
            action = DismissSocialNotifReceiver.ACTION_DISMISS
            putExtra(DismissSocialNotifReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            (pkg + "_dismiss").hashCode() and 0x7FFFFFFF,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notif_new_social_title)
        val text = context.getString(R.string.notif_new_social_text, appName)
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, context.getString(R.string.notif_action_block), blockPending)
            .addAction(0, context.getString(R.string.notif_action_dismiss), dismissPending)
            .build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
            ?.notify(notifId, notification)
    }

    companion object {
        private const val NOTIF_BASE_ID = 2100
    }
}
