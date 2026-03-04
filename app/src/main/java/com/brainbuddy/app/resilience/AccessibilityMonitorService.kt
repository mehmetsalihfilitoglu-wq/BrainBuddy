package com.brainbuddy.app.resilience

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.brainbuddy.app.R
import com.brainbuddy.app.core.LockModeDataStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.accessibility.AccessibilityUtils
import com.brainbuddy.app.accessibility.ForegroundAppBlockerService
import com.brainbuddy.app.ui.PinLockActivity

/**
 * B) ForegroundService: periyodik olarak AccessibilityService durumunu kontrol eder (5-10 sn).
 * Kapalıysa LockModeDataStore'a lockModeEnabled yazar.
 */
class AccessibilityMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            val notification = createNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isRunning = true
            scheduleNextCheck()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(checkRunnable)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private val checkRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            checkAccessibility()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    private fun scheduleNextCheck() {
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, POLL_INTERVAL_MS)
    }

    private fun checkAccessibility() {
        try {
            val prefs = ProtectionPrefs(this)
            if (!prefs.isProtectionEnabledRaw()) return

            val enabled = AccessibilityUtils.isServiceEnabled(this, ForegroundAppBlockerService::class.java)
            val lockStore = LockModeDataStore(this)
            lockStore.setLastKnownServiceEnabledSync(enabled)

            if (!enabled) {
                lockStore.setLockModeEnabledSync(true)
                prefs.setUserLocked(true)
                prefs.setPermissionDisabledLockReason("accessibility_disabled")
                try { com.brainbuddy.app.core.TamperStore(this).logEvent(com.brainbuddy.app.core.TamperStore.TamperType.SERVICE_DISABLED) } catch (_: Exception) { }
                try { com.brainbuddy.app.core.ProtectionNotificationHelper.showProtectionOffNotification(this) } catch (_: Exception) { }
            }
        } catch (e: Exception) {
            Log.e(TAG, "checkAccessibility error", e)
        }
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_monitor),
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val intent = Intent(this, PinLockActivity::class.java).apply {
            putExtra(PinLockActivity.EXTRA_MODE, "verify")
            putExtra(PinLockActivity.EXTRA_TARGET, "PermissionsChecklistActivity")
        }
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_monitor_title))
            .setContentText(getString(R.string.notif_monitor_text))
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "AccessibilityMonitor"
        private const val CHANNEL_ID = "bb_accessibility_monitor"
        private const val NOTIFICATION_ID = 1002
        private const val POLL_INTERVAL_MS = 10_000L

        fun start(context: Context) {
            val intent = Intent(context, AccessibilityMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AccessibilityMonitorService::class.java))
        }
    }
}
