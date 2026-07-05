package com.mioacademy.app.report

import android.graphics.Color

/**
 * Central PDF theme and asset paths for BrainBuddy reports.
 * White background, emerald accents, Turkish labels.
 */
object PdfTheme {

    const val EMERALD = 0xFF12B5A6.toInt()
    const val EMERALD_DARK = 0xFF0FAE9A.toInt()
    const val EMERALD_LIGHT = 0xFFDFF7F4.toInt()
    val SURFACE = Color.parseColor("#F7F9FA")
    val DIVIDER = Color.parseColor("#E6ECEF")
    val TEXT_PRIMARY = Color.parseColor("#1F2A30")
    val TEXT_SECONDARY = Color.parseColor("#5F6B73")
    val WEAK_TINT = Color.parseColor("#FCEAEA")

    /** Font paths under assets. Use built-in if not present. */
    const val FONT_REGULAR = "fonts/Inter-Regular.ttf"
    const val FONT_BOLD = "fonts/Inter-Bold.ttf"
}
