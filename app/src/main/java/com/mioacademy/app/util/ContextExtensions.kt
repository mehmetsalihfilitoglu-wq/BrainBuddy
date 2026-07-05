package com.mioacademy.app.util

import android.app.Activity
import android.content.Context

/**
 * Get the nearest Activity from this Context (for Compose/UI usage).
 * Use with LocalContext.current in Compose to obtain Activity for ad show calls.
 */
fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
