package com.mioacademy.app.ui

import android.app.Activity
import android.content.Intent
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mioacademy.app.R
import com.mioacademy.app.core.StudyAreaManager

/**
 * Compact bottom-sheet for switching the active study area from Home.
 * Tapping an area switches immediately; a footer opens the full manager.
 */
object AreaSwitcher {

    fun show(activity: Activity, onSwitched: () -> Unit) {
        val dp = activity.resources.displayMetrics.density
        fun dpi(v: Float) = (v * dp + 0.5f).toInt()

        val dialog = BottomSheetDialog(activity)

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(16f), dpi(20f), dpi(20f))
        }

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.area_switcher_title)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(activity.resources.getColor(R.color.emeraldDark, activity.theme))
            letterSpacing = 0.12f
            setPadding(0, 0, 0, dpi(12f))
        })

        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = dpi(8f) }
        }
        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)

        StudyAreaManager.getAreas(activity).forEach { area ->
            list.addView(TextView(activity).apply {
                text = if (area.isActive) "${area.emoji}  ${area.displayName}   ✓" else "${area.emoji}  ${area.displayName}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                setTypeface(null, if (area.isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                setTextColor(activity.resources.getColor(
                    if (area.isActive) R.color.emeraldDark else R.color.textPrimary, activity.theme))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dpi(8f), dpi(14f), dpi(8f), dpi(14f))
                background = tappableBg(activity)
                setOnClickListener {
                    if (!area.isActive) {
                        StudyAreaManager.setActiveArea(activity, area.id)
                        onSwitched()
                    }
                    dialog.dismiss()
                }
            })
        }
        root.addView(scroll)

        root.addView(TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpi(1f)
            ).also { it.topMargin = dpi(4f); it.bottomMargin = dpi(4f) }
            setBackgroundColor(activity.resources.getColor(R.color.divider, activity.theme))
        })

        root.addView(TextView(activity).apply {
            text = activity.getString(R.string.area_switcher_manage)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(activity.resources.getColor(R.color.emeraldDark, activity.theme))
            setPadding(dpi(8f), dpi(14f), dpi(8f), dpi(8f))
            background = tappableBg(activity)
            setOnClickListener {
                activity.startActivity(Intent(activity, StudyAreasActivity::class.java))
                dialog.dismiss()
            }
        })

        dialog.setContentView(root)
        dialog.show()
    }

    private fun tappableBg(activity: Activity): android.graphics.drawable.Drawable? {
        val outValue = android.util.TypedValue()
        activity.theme.resolveAttribute(
            android.R.attr.selectableItemBackground, outValue, true)
        return androidx.core.content.ContextCompat.getDrawable(activity, outValue.resourceId)
    }
}
