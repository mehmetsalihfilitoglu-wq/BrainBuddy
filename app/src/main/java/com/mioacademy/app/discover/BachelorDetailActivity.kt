package com.mioacademy.app.discover

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mioacademy.app.R

/** Full detail for one [BachelorProgram]; reuses the generic detail layout. */
class BachelorDetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "extra_program_id" }

    private val dp get() = resources.displayMetrics.density
    private fun dpi(v: Float) = (v * dp + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_university_detail)

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

        val id = intent.getStringExtra(EXTRA_ID)
        val p = id?.let { Discovery.repository.getBachelorProgram(it) }
        val container = findViewById<LinearLayout>(R.id.contentContainer)
        if (p == null) {
            container.addView(body(getString(R.string.discover_empty), 14f, R.color.textSecondary, topMargin = dpi(24f)))
            return
        }

        container.addView(body(p.programName, 22f, R.color.textPrimary, bold = true, lineMultiplier = 1.2f))
        container.addView(body("${p.universityName} · ${p.city}, ${p.country}", 13f, R.color.textSecondary, topMargin = dpi(6f)))

        container.addView(card(topMargin = dpi(16f)) {
            addView(factRow(getString(R.string.discover_detail_field), p.fieldCategory.displayTr))
            addView(factRow(getString(R.string.discover_detail_exam), p.admissionExam.ifBlank { "—" }))
            if (p.admissionRoute.isNotBlank() && p.admissionRoute != p.admissionExam) {
                addView(factRow(getString(R.string.discover_detail_route), p.admissionRoute))
            }
            addView(factRow(getString(R.string.discover_detail_type),
                if (p.isPublic) getString(R.string.discover_public) else getString(R.string.discover_private)))
        })

        container.addView(card(topMargin = dpi(12f)) {
            addView(blockTitle(getString(R.string.discover_detail_language)))
            addView(body(p.languageRequirement, 14f, R.color.textPrimary, lineMultiplier = 1.45f))
        })

        container.addView(card(topMargin = dpi(12f)) {
            addView(blockTitle(getString(R.string.discover_detail_cost)))
            addView(body(p.tuitionNote, 14f, R.color.textPrimary, lineMultiplier = 1.45f))
            addView(body(getString(R.string.discover_detail_opening), 12f, R.color.textSecondary, bold = true, topMargin = dpi(10f)))
            addView(body(p.openingDate.ifBlank { "—" }, 14f, R.color.textPrimary, topMargin = dpi(2f)))
            addView(body(getString(R.string.discover_detail_deadline), 12f, R.color.textSecondary, bold = true, topMargin = dpi(10f)))
            addView(body(p.deadlineNote, 14f, R.color.textPrimary, topMargin = dpi(2f), lineMultiplier = 1.45f))
        })

        if (p.admissionNotes.isNotBlank() || p.extraNotes.isNotBlank()) {
            container.addView(card(topMargin = dpi(12f)) {
                addView(blockTitle(getString(R.string.discover_detail_notes)))
                if (p.admissionNotes.isNotBlank())
                    addView(body(p.admissionNotes, 13f, R.color.textPrimary, lineMultiplier = 1.5f))
                if (p.extraNotes.isNotBlank())
                    addView(body(p.extraNotes, 12f, R.color.textSecondary, topMargin = dpi(8f), lineMultiplier = 1.5f))
            })
        }

        if (p.infoUrl.isNotBlank()) {
            container.addView(MaterialButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpi(52f)
                ).also { it.topMargin = dpi(16f) }
                text = getString(R.string.discover_detail_moreinfo)
                textSize = 15f
                setTextColor(resources.getColor(R.color.white, theme))
                cornerRadius = dpi(16f)
                backgroundTintList = android.content.res.ColorStateList.valueOf(resources.getColor(R.color.emerald, theme))
                setOnClickListener { openUrl(p.infoUrl) }
            })
        }

        container.addView(body(getString(R.string.discover_detail_notice), 12f, R.color.textSecondary,
            topMargin = dpi(16f), lineMultiplier = 1.45f))
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, getString(R.string.discover_no_browser), Toast.LENGTH_SHORT).show() }
    }

    // ── builders (self-contained) ──
    private fun card(topMargin: Int = 0, build: LinearLayout.() -> Unit): MaterialCardView {
        val c = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = topMargin }
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = dpi(1f)
            setStrokeColor(resources.getColor(R.color.border, theme))
            setCardBackgroundColor(resources.getColor(R.color.white, theme))
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        }.apply(build)
        c.addView(col)
        return c
    }

    private fun blockTitle(text: String): TextView =
        body(text, 15f, R.color.textPrimary, bold = true, bottomMargin = dpi(8f))

    private fun factRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(6f) }
        }
        row.addView(TextView(this).apply {
            this.text = label
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(resources.getColor(R.color.textSecondary, theme))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(this).apply {
            this.text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(resources.getColor(R.color.textPrimary, theme))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f)
        })
        return row
    }

    private fun body(
        text: String, size: Float, colorRes: Int,
        bold: Boolean = false, topMargin: Int = 0, bottomMargin: Int = 0, lineMultiplier: Float = 1.2f
    ): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(resources.getColor(colorRes, theme))
        if (bold) setTypeface(null, Typeface.BOLD)
        setLineSpacing(0f, lineMultiplier)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = topMargin; it.bottomMargin = bottomMargin }
    }
}
