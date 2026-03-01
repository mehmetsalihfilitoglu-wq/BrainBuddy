package com.brainbuddy.app.deviceadmin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin receiver for optional kiosk/DO integration.
 * Parent can enable from Parent area. Degrades gracefully if not available.
 */
class BrainBuddyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {}
    override fun onDisabled(context: Context, intent: Intent) {}
}
