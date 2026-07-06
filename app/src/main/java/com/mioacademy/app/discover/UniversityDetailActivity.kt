package com.mioacademy.app.discover

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView
import com.mioacademy.app.R

/**
 * Full detail for one [University]. Fetches by id from [Discovery.repository] so
 * nothing needs to be Parcelable. Leaves an explicit empty hero-image slot for a
 * future photo, and the structure supports later "student experience" blocks.
 */
class UniversityDetailActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "extra_university_id" }

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
        val uni = id?.let { Discovery.repository.getUniversity(it) }
        val container = findViewById<LinearLayout>(R.id.contentContainer)
        if (uni == null) {
            container.addView(body(getString(R.string.discover_empty), 14f, R.color.textSecondary,
                topMargin = dpi(24f)))
            return
        }

        // Title + location
        container.addView(body(uni.name, 24f, R.color.textPrimary, bold = true, lineMultiplier = 1.2f))
        container.addView(body("📍 ${uni.city}, ${uni.region} · ${uni.country}", 13f, R.color.textSecondary,
            topMargin = dpi(6f)))

        // Key facts card
        container.addView(card(topMargin = dpi(16f)) {
            addView(factRow(getString(R.string.discover_detail_program), uni.programName))
            addView(factRow(getString(R.string.discover_detail_degree), uni.degreeType))
            addView(factRow(getString(R.string.discover_detail_type),
                if (uni.isPublic) getString(R.string.discover_public) else getString(R.string.discover_private)))
            addView(factRow(getString(R.string.discover_detail_language), uni.language))
            addView(factRow(getString(R.string.discover_detail_exam), uni.admissionExam.ifBlank { "—" }))
        })

        // Description
        if (uni.shortDescription.isNotBlank()) {
            container.addView(card(topMargin = dpi(12f)) {
                addView(blockTitle(getString(R.string.discover_detail_about)))
                addView(body(uni.shortDescription, 14f, R.color.textPrimary, lineMultiplier = 1.5f))
                if (uni.highlights.isNotEmpty()) {
                    addView(body("• " + uni.highlights.joinToString("\n• "), 13f, R.color.textSecondary,
                        topMargin = dpi(10f), lineMultiplier = 1.5f))
                }
            })
        }

        // Tuition + scholarship
        container.addView(card(topMargin = dpi(12f)) {
            addView(blockTitle(getString(R.string.discover_detail_cost)))
            addView(body(getString(R.string.discover_detail_tuition), 12f, R.color.textSecondary, bold = true))
            addView(body(uni.tuitionNote.ifBlank { "—" }, 14f, R.color.textPrimary, topMargin = dpi(2f), lineMultiplier = 1.45f))
            addView(body(getString(R.string.discover_detail_scholarship), 12f, R.color.textSecondary,
                bold = true, topMargin = dpi(10f)))
            addView(body(uni.scholarshipNote.ifBlank { "—" }, 14f, R.color.textPrimary, topMargin = dpi(2f), lineMultiplier = 1.45f))
        })

        // Neutral caveat — we intentionally do NOT link out to the school site.
        container.addView(body(getString(R.string.discover_detail_notice), 12f, R.color.textSecondary,
            topMargin = dpi(16f), lineMultiplier = 1.45f))
    }

    // ── builders ──
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
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f)
        })
        return row
    }

    private fun body(
        text: String, size: Float, colorRes: Int,
        bold: Boolean = false, topMargin: Int = 0, bottomMargin: Int = 0,
        lineMultiplier: Float = 1.2f
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
