package com.brainbuddy.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AppBlockAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // şimdilik boş (sonra app bloklamayı buraya ekleyeceğiz)
    }

    override fun onInterrupt() {
        // şimdilik boş
    }
}