package com.mioacademy.app.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mioacademy.app.R
import com.mioacademy.app.core.CareerPath
import com.mioacademy.app.core.StudyAreaManager

/**
 * "Çalışma Alanlarım" — the single management surface for study areas:
 * switch the active area, add a new one, or remove one. Each area's statistics
 * are isolated (see [StudyAreaManager]).
 */
class StudyAreasActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_study_areas)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        val content = findViewById<View>(R.id.scrollContent)
        val origBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, origBottom + navBottom)
            insets
        }

        findViewById<MaterialButton>(R.id.btnAddArea).setOnClickListener { showAddDialog() }
    }

    override fun onResume() {
        super.onResume()
        rebuildAreas()
    }

    private fun rebuildAreas() {
        val container = findViewById<LinearLayout>(R.id.areasContainer)
        container.removeAllViews()
        val areas = StudyAreaManager.getAreas(this)
        areas.forEachIndexed { index, area ->
            container.addView(buildAreaRow(area, canRemove = areas.size > 1, addTopMargin = index > 0))
        }
    }

    private fun buildAreaRow(
        area: StudyAreaManager.Area,
        canRemove: Boolean,
        addTopMargin: Boolean
    ): MaterialCardView {
        val dp = resources.displayMetrics.density
        fun dpi(v: Float) = (v * dp + 0.5f).toInt()

        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { if (addTopMargin) it.topMargin = dpi(8f) }
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = dpi(if (area.isActive) 1.5f else 1f)
            setStrokeColor(
                resources.getColor(if (area.isActive) R.color.emerald else R.color.border, theme)
            )
            setCardBackgroundColor(
                resources.getColor(if (area.isActive) R.color.emeraldSoft else R.color.white, theme)
            )
        }

        val row = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        }

        val emoji = TextView(this).apply {
            text = area.emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }

        val textCol = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12f), 0, dpi(12f), 0)
        }
        val title = TextView(this).apply {
            text = area.displayName
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(resources.getColor(R.color.textPrimary, theme))
        }
        val sub = TextView(this).apply {
            text = area.career.examType.fullNameIt
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(resources.getColor(R.color.textSecondary, theme))
        }
        textCol.addView(title)
        textCol.addView(sub)

        row.addView(emoji)
        row.addView(textCol)

        if (area.isActive) {
            val badge = TextView(this).apply {
                text = getString(R.string.areas_active_badge)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.emeraldDark, theme))
            }
            row.addView(badge)
        } else {
            val setActive = TextView(this).apply {
                text = getString(R.string.areas_set_active)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(resources.getColor(R.color.emeraldDark, theme))
                setPadding(dpi(8f), dpi(8f), dpi(8f), dpi(8f))
                setOnClickListener {
                    StudyAreaManager.setActiveArea(this@StudyAreasActivity, area.id)
                    rebuildAreas()
                }
            }
            row.addView(setActive)
        }

        if (canRemove) {
            val remove = TextView(this).apply {
                text = "✕"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(resources.getColor(R.color.text_tertiary, theme))
                setPadding(dpi(8f), dpi(8f), dpi(4f), dpi(8f))
                setOnClickListener { confirmRemove(area) }
            }
            row.addView(remove)
        }

        card.addView(row)
        return card
    }

    private fun confirmRemove(area: StudyAreaManager.Area) {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.areas_remove_confirm_title))
            .setMessage(getString(R.string.areas_remove_confirm_msg, area.displayName))
            .setPositiveButton(getString(R.string.areas_remove)) { _, _ ->
                if (!StudyAreaManager.removeArea(this, area.id)) {
                    toast(getString(R.string.areas_min_one))
                }
                rebuildAreas()
            }
            .setNegativeButton(getString(R.string.quiz_abandon_cancel), null)
            .show()
    }

    private fun showAddDialog() {
        val existing = StudyAreaManager.getAreas(this).map { it.career }.toSet()
        val available = CareerPath.values().filter { it !in existing }
        if (available.isEmpty()) {
            toast(getString(R.string.areas_all_added))
            return
        }
        val labels = available.map { "${it.emoji}  ${it.displayNameTr} · ${it.examType.code}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.areas_add_dialog_title))
            .setItems(labels) { _, which ->
                StudyAreaManager.addArea(this, available[which])
                rebuildAreas()
            }
            .show()
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
}
