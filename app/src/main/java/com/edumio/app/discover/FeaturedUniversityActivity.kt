package com.edumio.app.discover

import android.content.Intent
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
import com.edumio.app.R

/**
 * The curated "special page" for a [FeaturedUniversity] (e.g. Politecnico di
 * Torino): a university header plus its hand-checked programs. Each program card
 * opens [FeaturedProgramDetailActivity]. Reuses the discover scaffold layout.
 */
class FeaturedUniversityActivity : AppCompatActivity() {

    companion object { const val EXTRA_ID = "extra_featured_id" }

    private val dp get() = resources.displayMetrics.density
    private fun dpi(v: Float) = (v * dp + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_discover)

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

        val u = intent.getStringExtra(EXTRA_ID)?.let { Discovery.repository.getFeaturedUniversity(it) }
        val container = findViewById<LinearLayout>(R.id.contentContainer)
        if (u == null) {
            container.addView(body(getString(R.string.discover_empty), 14f, R.color.textSecondary, topMargin = dpi(24f)))
            return
        }

        findViewById<TextView>(R.id.tvDiscoverTitle).text = u.city

        // Header
        container.addView(body(u.universityName, 22f, R.color.textPrimary, bold = true, lineMultiplier = 1.2f))
        container.addView(body("📍 ${u.city}, ${u.region} · ${u.country}", 13f, R.color.textSecondary, topMargin = dpi(6f)))
        container.addView(card(topMargin = dpi(16f)) {
            addView(factRow(getString(R.string.discover_detail_type),
                if (u.isPublic) getString(R.string.discover_public) else getString(R.string.discover_private)))
            addView(body(u.shortDescription, 14f, R.color.textPrimary, topMargin = dpi(10f), lineMultiplier = 1.5f))
            if (u.highlights.isNotEmpty()) {
                addView(body("• " + u.highlights.joinToString("\n• "), 13f, R.color.textSecondary,
                    topMargin = dpi(10f), lineMultiplier = 1.5f))
            }
        })
        // Programs
        container.addView(sectionLabel(getString(R.string.discover_section_programs, u.programs.size)))
        u.programs.forEach { container.addView(programCard(u.universityId, it)) }

        container.addView(body(getString(R.string.discover_detail_notice), 12f, R.color.textSecondary,
            topMargin = dpi(16f), lineMultiplier = 1.45f))
    }

    private fun programCard(universityId: String, p: UniversityProgram): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8f) }
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = dpi(1f)
            setStrokeColor(resources.getColor(R.color.border, theme))
            setCardBackgroundColor(resources.getColor(R.color.white, theme))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@FeaturedUniversityActivity, FeaturedProgramDetailActivity::class.java)
                    .putExtra(FeaturedProgramDetailActivity.EXTRA_UNI, universityId)
                    .putExtra(FeaturedProgramDetailActivity.EXTRA_PROGRAM, p.programId))
            }
        }
        val col = verticalPadded(dpi(16f))
        col.addView(body(p.programName, 15f, R.color.textPrimary, bold = true, lineMultiplier = 1.25f))

        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8f) }
        }
        badges.addView(pill(p.admissionInfo))
        badges.addView(pill(p.language, marginStart = dpi(6f)))
        col.addView(badges)

        col.addView(body("${getString(R.string.discover_detail_campus)}: ${p.campus}", 12f, R.color.textSecondary, topMargin = dpi(8f)))
        p.availableSeats?.let {
            col.addView(body("${getString(R.string.discover_detail_seats)}: $it", 12f, R.color.textSecondary, topMargin = dpi(2f)))
        }
        col.addView(body(getString(R.string.discover_view_details) + "  ›", 13f, R.color.emeraldDark,
            topMargin = dpi(10f), bold = true))
        card.addView(col)
        return card
    }

    // ── builders ──
    private fun sectionLabel(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setTextColor(resources.getColor(R.color.emeraldDark, theme))
        letterSpacing = 0.12f
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(24f); it.bottomMargin = dpi(12f) }
    }

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
        val col = verticalPadded(dpi(16f)).apply(build)
        c.addView(col)
        return c
    }

    private fun pill(text: String, marginStart: Int = 0): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTypeface(null, Typeface.BOLD)
        setTextColor(resources.getColor(R.color.emeraldDark, theme))
        setPadding(dpi(10f), dpi(4f), dpi(10f), dpi(4f))
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 100f
            setColor(resources.getColor(R.color.emeraldSoft, theme))
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.marginStart = marginStart }
    }

    private fun verticalPadded(pad: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(pad, pad, pad, pad)
    }

    private fun factRow(label: String, value: String): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
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
        })
        return row
    }

    private fun body(
        text: String, size: Float, colorRes: Int,
        bold: Boolean = false, topMargin: Int = 0, lineMultiplier: Float = 1.2f
    ): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(resources.getColor(colorRes, theme))
        if (bold) setTypeface(null, Typeface.BOLD)
        setLineSpacing(0f, lineMultiplier)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = topMargin }
    }
}
