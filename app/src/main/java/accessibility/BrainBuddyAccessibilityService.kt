package com.brainbuddy.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class BrainBuddyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Şimdilik boş bırak (bloklama/timer mantığını sonra ekleyeceğiz)
    }

    override fun onInterrupt() {
        // Şimdilik boş
    }
}