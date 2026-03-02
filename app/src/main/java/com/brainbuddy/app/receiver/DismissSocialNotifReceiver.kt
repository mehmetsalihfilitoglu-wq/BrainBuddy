package com.brainbuddy.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Handles "Yoksay" action from new-app notification. Dismisses the notification only.
 */
class DismissSocialNotifReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_DISMISS) return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, -1)
        if (notifId >= 0) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager)
                ?.cancel(notifId)
        }
    }

    companion object {
        const val ACTION_DISMISS = "com.brainbuddy.app.DISMISS_SOCIAL_NOTIF"
        const val EXTRA_NOTIF_ID = "notif_id"
    }
}
