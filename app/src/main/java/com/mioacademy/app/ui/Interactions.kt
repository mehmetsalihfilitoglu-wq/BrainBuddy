package com.mioacademy.app.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.OvershootInterpolator

/**
 * Small, consistent microinteractions that make Mioitalia feel deliberate and
 * premium — not a toy. A tap gives a light haptic confirmation plus a restrained
 * press-bounce; a "success" (correct answer, milestone, goal met) uses a
 * slightly firmer haptic. Everything respects the device's system haptic
 * setting — we never force feedback the user has turned off.
 */
object Interactions {

    /** Light confirm for a normal tap: subtle bounce + soft haptic. */
    fun tap(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        bounce(view, 0.96f)
    }

    /** Firmer feedback for a meaningful moment (correct, unlock, goal met). */
    fun success(view: View) {
        val const = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        view.performHapticFeedback(const)
    }

    /** Just the haptic, no motion (e.g. selecting a quiz option). */
    fun lightTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun bounce(v: View, down: Float) {
        v.animate().cancel()
        v.scaleX = 1f; v.scaleY = 1f
        v.animate().scaleX(down).scaleY(down).setDuration(80).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(140)
                .setInterpolator(OvershootInterpolator(2f)).start()
        }.start()
    }
}

/** Click listener with the standard tap microinteraction baked in. */
fun View.onTap(block: (View) -> Unit) {
    setOnClickListener {
        Interactions.tap(it)
        block(it)
    }
}
