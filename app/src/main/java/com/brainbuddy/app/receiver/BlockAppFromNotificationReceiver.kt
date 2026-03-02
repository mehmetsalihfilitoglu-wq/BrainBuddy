package com.brainbuddy.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.brainbuddy.app.core.BlockedAppsStore

/**
 * Handles "Engelle" action from new-app notification. Adds package to blocked list.
 * Parent-confirmed only; never auto-blocked.
 */
class BlockAppFromNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_BLOCK) return
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)?.trim() ?: return
        if (pkg.isNotEmpty() && pkg.length <= 256) {
            val store = BlockedAppsStore(context)
            val set = store.getBlockedPackages().toMutableSet()
            set.add(pkg)
            store.setBlockedPackages(set)
        }
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId >= 0) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)
                ?.cancel(notifId)
        }
    }

    companion object {
        const val ACTION_BLOCK = "com.brainbuddy.app.BLOCK_APP_FROM_NOTIF"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
