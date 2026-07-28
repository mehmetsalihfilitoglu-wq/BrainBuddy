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
import com.edumio.app.core.StudyAreaManager

/**
 * "Keşfet" — university & program discovery, scoped to the active study area's
 * exam. Renders the exam guide, the matching university cards and an FAQ. The
 * section container is built at runtime so future blocks (city/life/scholarship
 * guides) slot in without a layout rewrite.
 */
class DiscoverActivity : AppCompatActivity() {

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

        val examType = StudyAreaManager.getActiveArea(this).career.examType
        val repo = Discovery.repository
        val guide = repo.getGuide(examType)
        val universities = repo.getUniversities(examType)
        val featured = repo.getFeaturedUniversities(examType)
        val programs = repo.getBachelorPrograms(examType)

        // Title reflects the active exam, e.g. "IMAT · Keşfet"; generic otherwise.
        findViewById<TextView>(R.id.tvDiscoverTitle).text =
            if (repo.hasContent(examType)) "${examType.code} · ${getString(R.string.discover_title)}"
            else getString(R.string.discover_title)

        val container = findViewById<LinearLayout>(R.id.contentContainer)

        if (guide == null && universities.isEmpty() && programs.isEmpty() && featured.isEmpty()) {
            container.addView(emptyState())
            return
        }

        guide?.let { renderGuide(container, it) }
        renderFeaturedUniversities(container, featured)
        renderUniversities(container, universities)
        renderBachelorPrograms(container, programs)
        guide?.faq?.takeIf { it.isNotEmpty() }?.let { renderFaq(container, it) }
    }

    private fun renderFeaturedUniversities(container: LinearLayout, featured: List<FeaturedUniversity>) {
        if (featured.isEmpty()) return
        container.addView(sectionLabel(getString(R.string.discover_section_featured)))
        featured.forEach { container.addView(featuredCard(it)) }
    }

    private fun featuredCard(u: FeaturedUniversity): MaterialCardView {
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8f) }
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = dpi(1.5f)
            setStrokeColor(resources.getColor(R.color.emerald, theme))
            setCardBackgroundColor(resources.getColor(R.color.emeraldSoft, theme))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                startActivity(Intent(this@DiscoverActivity, FeaturedUniversityActivity::class.java)
                    .putExtra(FeaturedUniversityActivity.EXTRA_ID, u.universityId))
            }
        }
        val col = verticalPadded(dpi(16f))
        col.addView(body("⭐ ${getString(R.string.discover_featured_badge)}", 11f, R.color.emeraldDark, bold = true))
        col.addView(body(u.universityName, 17f, R.color.textPrimary, bold = true, topMargin = dpi(4f), lineMultiplier = 1.2f))
        col.addView(body("📍 ${u.city}, ${u.country}", 12f, R.color.textSecondary, topMargin = dpi(2f)))
        col.addView(body(
            getString(R.string.discover_featured_programs_count, u.programs.size), 13f,
            R.color.textPrimary, topMargin = dpi(8f), bold = true))
        col.addView(body(getString(R.string.discover_view_details) + "  ›", 13f, R.color.emeraldDark,
            topMargin = dpi(10f), bold = true))
        card.addView(col)
        return card
    }

    private fun renderBachelorPrograms(container: LinearLayout, programs: List<BachelorProgram>) {
        if (programs.isEmpty()) return
        container.addView(sectionLabel(getString(R.string.discover_section_programs, programs.size)))
        // Group by field so the list reads as coherent sub-sections.
        programs.groupBy { it.fieldCategory }
            .toList()
            .sortedByDescending { it.second.size }
            .forEach { (field, list) ->
                container.addView(body(field.displayTr, 13f, R.color.textPrimary,
                    bold = true, topMargin = dpi(12f)))
                list.forEach { container.addView(programCard(it)) }
            }
    }

    private fun programCard(p: BachelorProgram): MaterialCardView {
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
                startActivity(Intent(this@DiscoverActivity, BachelorDetailActivity::class.java)
                    .putExtra(BachelorDetailActivity.EXTRA_ID, p.id))
            }
        }
        val col = verticalPadded(dpi(16f))
        col.addView(body(p.programName, 15f, R.color.textPrimary, bold = true, lineMultiplier = 1.25f))
        col.addView(body("${p.universityName} · ${p.city}", 12f, R.color.textSecondary, topMargin = dpi(2f)))

        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8f) }
        }
        badges.addView(pill(if (p.isPublic) getString(R.string.discover_public) else getString(R.string.discover_private)))
        badges.addView(pill(p.admissionExam, marginStart = dpi(6f)))
        col.addView(badges)

        col.addView(body(getString(R.string.discover_view_details) + "  ›", 13f, R.color.emeraldDark,
            topMargin = dpi(10f), bold = true))
        card.addView(col)
        return card
    }

    // ── Section renderers ─────────────────────────────────────────────────────

    private fun renderGuide(container: LinearLayout, g: ExamGuide) {
        container.addView(sectionLabel(getString(R.string.discover_section_guide)))

        // Overview + subtitle
        container.addView(card {
            addView(body(g.subtitle, 13f, R.color.emeraldDark, bold = true))
            addView(body(g.overview, 14f, R.color.textPrimary, topMargin = dpi(8f), lineMultiplier = 1.4f))
        })

        // At-a-glance exam summary (icon cards)
        g.summary?.let { renderSummary(container, it) }

        // Exam structure (only when the guide has structured content)
        if (g.examStructure.isNotEmpty() || g.subjects.isNotEmpty()) {
            container.addView(card(topMargin = dpi(12f)) {
                addView(blockTitle(getString(R.string.discover_exam_structure)))
                g.examStructure.forEach { addView(factRow(it.label, it.value)) }
                if (g.subjects.isNotEmpty()) {
                    addView(body(getString(R.string.discover_subjects), 12f, R.color.textSecondary,
                        topMargin = dpi(12f), bold = true))
                    addView(body("• " + g.subjects.joinToString("\n• "), 14f, R.color.textPrimary,
                        topMargin = dpi(4f), lineMultiplier = 1.5f))
                }
            })
        }

        // Scoring
        if (g.scoring.isNotEmpty()) {
            container.addView(card(topMargin = dpi(12f)) {
                addView(blockTitle(getString(R.string.discover_scoring)))
                g.scoring.forEach { addView(factRow(it.label, it.value)) }
            })
        }

        // Timeline + application
        if (g.timeline.isNotBlank() || g.applicationNotes.isNotBlank() || g.requiredDocuments.isNotEmpty()) {
            container.addView(card(topMargin = dpi(12f)) {
                addView(blockTitle(getString(R.string.discover_timeline)))
                if (g.timeline.isNotBlank())
                    addView(body(g.timeline, 14f, R.color.textPrimary, lineMultiplier = 1.4f))
                if (g.applicationNotes.isNotBlank()) {
                    addView(body(getString(R.string.discover_application), 12f, R.color.textSecondary,
                        topMargin = dpi(12f), bold = true))
                    addView(body(g.applicationNotes, 14f, R.color.textPrimary, topMargin = dpi(4f), lineMultiplier = 1.4f))
                }
                if (g.requiredDocuments.isNotEmpty()) {
                    addView(body(getString(R.string.discover_documents), 12f, R.color.textSecondary,
                        topMargin = dpi(12f), bold = true))
                    addView(body("• " + g.requiredDocuments.joinToString("\n• "), 14f, R.color.textPrimary,
                        topMargin = dpi(4f), lineMultiplier = 1.5f))
                }
            })
        }

        // Official notice (accented)
        container.addView(noticeCard(g.officialNoticeNote))
    }

    /** The standard "Sınav Özeti" — one icon card per set field. */
    private fun renderSummary(container: LinearLayout, s: ExamSummary) {
        container.addView(sectionLabel(getString(R.string.discover_summary_section)))
        if (s.whenHeld.isNotBlank())
            container.addView(summaryCard("📅", getString(R.string.discover_summary_when), s.whenHeld))
        if (s.attempts.isNotBlank())
            container.addView(summaryCard("🔁", getString(R.string.discover_summary_attempts), s.attempts))
        if (s.resultValidity.isNotBlank())
            container.addView(summaryCard("📊", getString(R.string.discover_summary_validity), s.resultValidity))
        if (s.importantNote.isNotBlank())
            container.addView(summaryCard("ℹ️", getString(R.string.discover_summary_important), s.importantNote))
    }

    private fun summaryCard(emoji: String, title: String, text: String): MaterialCardView {
        val c = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8f) }
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = dpi(1f)
            setStrokeColor(resources.getColor(R.color.border, theme))
            setCardBackgroundColor(resources.getColor(R.color.white, theme))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        }
        row.addView(TextView(this).apply {
            this.text = emoji
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        })
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginStart = dpi(12f) }
        }
        col.addView(body(title, 12f, R.color.emeraldDark, bold = true))
        col.addView(body(text, 14f, R.color.textPrimary, topMargin = dpi(2f), lineMultiplier = 1.4f))
        row.addView(col)
        c.addView(row)
        return c
    }

    private fun renderUniversities(container: LinearLayout, unis: List<University>) {
        if (unis.isEmpty()) return
        container.addView(sectionLabel(getString(R.string.discover_section_universities, unis.size)))
        unis.forEach { container.addView(universityCard(it)) }
    }

    private fun renderFaq(container: LinearLayout, faq: List<FaqItem>) {
        container.addView(sectionLabel(getString(R.string.discover_section_faq)))
        faq.forEach { item ->
            container.addView(card(topMargin = dpi(8f)) {
                addView(body(item.question, 15f, R.color.textPrimary, bold = true, lineMultiplier = 1.3f))
                addView(body(item.answer, 13f, R.color.textSecondary, topMargin = dpi(6f), lineMultiplier = 1.5f))
            })
        }
    }

    // ── University card ───────────────────────────────────────────────────────

    private fun universityCard(u: University): MaterialCardView {
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
            setOnClickListener { openDetail(u.id) }
        }
        val col = verticalPadded(dpi(16f))

        col.addView(body(u.name, 16f, R.color.textPrimary, bold = true, lineMultiplier = 1.25f))
        col.addView(body("📍 ${u.city}, ${u.country}", 12f, R.color.textSecondary, topMargin = dpi(4f)))

        // Badge row: public/private + language
        val badges = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(8f) }
        }
        badges.addView(pill(if (u.isPublic) getString(R.string.discover_public) else getString(R.string.discover_private)))
        badges.addView(pill(u.language, marginStart = dpi(6f)))
        col.addView(badges)

        col.addView(body(u.programName, 13f, R.color.textPrimary, topMargin = dpi(8f), bold = true))
        col.addView(body(u.shortDescription, 13f, R.color.textSecondary, topMargin = dpi(4f), lineMultiplier = 1.45f))

        col.addView(body(getString(R.string.discover_view_details) + "  ›", 13f, R.color.emeraldDark,
            topMargin = dpi(10f), bold = true))

        card.addView(col)
        return card
    }

    private fun openDetail(id: String) {
        startActivity(Intent(this, UniversityDetailActivity::class.java)
            .putExtra(UniversityDetailActivity.EXTRA_ID, id))
    }

    // ── View builders ─────────────────────────────────────────────────────────

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

    private fun blockTitle(text: String): TextView =
        body(text, 15f, R.color.textPrimary, bold = true, bottomMargin = dpi(8f))

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

    private fun noticeCard(text: String): MaterialCardView {
        val c = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(12f) }
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(resources.getColor(R.color.emeraldSoft, theme))
        }
        val col = verticalPadded(dpi(16f))
        col.addView(body("ℹ️  " + getString(R.string.discover_notice_label), 12f, R.color.emeraldDark, bold = true))
        col.addView(body(text, 13f, R.color.textPrimary, topMargin = dpi(4f), lineMultiplier = 1.45f))
        c.addView(col)
        return c
    }

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
        })
        return row
    }

    private fun pill(text: String, marginStart: Int = 0): TextView = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        setTypeface(null, Typeface.BOLD)
        setTextColor(resources.getColor(R.color.emeraldDark, theme))
        setPadding(dpi(10f), dpi(4f), dpi(10f), dpi(4f))
        background = pillBg()
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.marginStart = marginStart }
    }

    private fun pillBg(): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = 100f
            setColor(resources.getColor(R.color.emeraldSoft, theme))
        }

    private fun verticalPadded(pad: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPadding(pad, pad, pad, pad)
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

    private fun emptyState(): TextView = body(
        getString(R.string.discover_empty), 14f, R.color.textSecondary,
        topMargin = dpi(32f), lineMultiplier = 1.5f
    ).apply { gravity = Gravity.CENTER }
}
