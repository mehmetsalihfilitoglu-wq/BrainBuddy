package com.edumio.app.discover

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

/** Full detail for one [UniversityProgram] on a [FeaturedUniversity] page. */
class FeaturedProgramDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_UNI = "extra_featured_uni_id"
        const val EXTRA_PROGRAM = "extra_featured_program_id"
    }

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

        val u = intent.getStringExtra(EXTRA_UNI)?.let { Discovery.repository.getFeaturedUniversity(it) }
        val p = u?.programs?.firstOrNull { it.programId == intent.getStringExtra(EXTRA_PROGRAM) }
        val container = findViewById<LinearLayout>(R.id.contentContainer)
        if (u == null || p == null) {
            container.addView(body(getString(R.string.discover_empty), 14f, R.color.textSecondary, topMargin = dpi(24f)))
            return
        }

        container.addView(body(p.programName, 22f, R.color.textPrimary, bold = true, lineMultiplier = 1.2f))
        container.addView(body("${u.universityName} · ${u.city}, ${u.country}", 13f, R.color.textSecondary, topMargin = dpi(6f)))

        container.addView(card(topMargin = dpi(16f)) {
            addView(factRow(getString(R.string.discover_detail_degree), p.degreeType))
            addView(factRow(getString(R.string.discover_detail_language), p.language))
            addView(factRow(getString(R.string.discover_detail_campus), p.campus))
            p.availableSeats?.let { addView(factRow(getString(R.string.discover_detail_seats), it.toString())) }
            addView(factRow(getString(R.string.discover_detail_exam), p.admissionInfo))
            addView(factRow(getString(R.string.discover_detail_type),
                if (u.isPublic) getString(R.string.discover_public) else getString(R.string.discover_private)))
        })

        if (p.shortDescription.isNotBlank()) {
            container.addView(card(topMargin = dpi(12f)) {
                addView(blockTitle(getString(R.string.discover_detail_about)))
                addView(body(p.shortDescription, 14f, R.color.textPrimary, lineMultiplier = 1.5f))
            })
        }

        // Cost / scholarship — short, stable university-level notes.
        container.addView(card(topMargin = dpi(12f)) {
            addView(blockTitle(getString(R.string.discover_detail_cost)))
            addView(body(getString(R.string.discover_detail_tuition), 12f, R.color.textSecondary, bold = true))
            addView(body(u.tuitionNote.ifBlank { "—" }, 14f, R.color.textPrimary, topMargin = dpi(2f), lineMultiplier = 1.45f))
            addView(body(getString(R.string.discover_detail_scholarship), 12f, R.color.textSecondary, bold = true, topMargin = dpi(10f)))
            addView(body(u.scholarshipNote.ifBlank { "—" }, 14f, R.color.textPrimary, topMargin = dpi(2f), lineMultiplier = 1.45f))
        })

        // Neutral caveat — no outbound link to the school site by design.
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
