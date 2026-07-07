package com.mioacademy.app.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mioacademy.app.R
import com.mioacademy.app.analytics.AnalyticsProvider

/**
 * Free lead-magnet tools (top-of-funnel), all from real / public rules — no fabricated
 * numbers. Reachable from Study without a paywall so it can be shared into communities:
 *  - IMAT score simulator (exact: +1.5 / -0.4, max 90)
 *  - Cost & DSU scholarship info (honest bands + disclaimer, not a false-precision calculator)
 *  - Eligibility / denklik checklist
 *
 * Phase-1 Turkish-only, so copy is inline (kept out of strings.xml intentionally for velocity).
 */
class ToolsActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density
    private fun dpi(v: Float) = (v * dp + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AnalyticsProvider.track("tools_screen_viewed")

        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.white)); isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(16f), dpi(20f), dpi(28f))
        }
        scroll.addView(col); setContentView(scroll)

        col.addView(text("‹", 26f, R.color.textSecondary, bold = true).apply {
            setPadding(dpi(4f), dpi(4f), dpi(16f), dpi(8f)); minWidth = dpi(48f); minHeight = dpi(48f)
            isClickable = true; isFocusable = true
            contentDescription = getString(R.string.cd_back)
            setOnClickListener { finish() }
        })
        col.addView(text("Ücretsiz Araçlar", 24f, R.color.textPrimary, bold = true))
        col.addView(text("İtalya yolculuğun için hızlı, gerçek hesaplar. Ücretsiz.",
            14f, R.color.textSecondary, topMargin = dpi(6f), lineMultiplier = 1.4f))

        buildImatScore(col)
        buildCostInfo(col)
        buildEligibility(col)

        col.addView(text(
            "Bilgiler kamuya açık kaynaklara dayanır ve tahminidir; kesin şartlar üniversiteye, " +
                "bölgeye, ISEE'ne ve yıla göre değişir. Resmi başvurunu her zaman doğrula.",
            11f, R.color.textSecondary, topMargin = dpi(24f), lineMultiplier = 1.4f))
    }

    // ── Tool 1: IMAT score simulator ────────────────────────────────────────────
    private fun buildImatScore(parent: LinearLayout) {
        parent.addView(sectionLabel("IMAT SKOR SİMÜLATÖRÜ"))
        val body = paddedCol()
        body.addView(text("60 soru · doğru +1,5 · yanlış −0,4 · boş 0 · en fazla 90 puan.",
            12f, R.color.textSecondary, lineMultiplier = 1.35f))

        body.addView(fieldLabel("Doğru sayısı (0–60)"))
        val correct = numberField()
        body.addView(correct)
        body.addView(fieldLabel("Yanlış sayısı (0–60)"))
        val wrong = numberField()
        body.addView(wrong)

        val result = text("", 15f, R.color.emeraldDark, bold = true, topMargin = dpi(14f), lineMultiplier = 1.4f).apply {
            visibility = TextView.GONE
        }
        body.addView(MaterialButton(this).apply {
            text = "Puanı Hesapla"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpi(48f)
            ).also { it.topMargin = dpi(14f) }
            cornerRadius = dpi(12f)
            setOnClickListener {
                val d = correct.text.toString().toIntOrNull()
                val y = wrong.text.toString().toIntOrNull()
                result.visibility = TextView.VISIBLE
                when {
                    d == null || y == null -> {
                        result.setTextColor(getColor(R.color.warning_text))
                        result.text = "Lütfen doğru ve yanlış sayısını gir."
                    }
                    d < 0 || y < 0 || d + y > 60 -> {
                        result.setTextColor(getColor(R.color.warning_text))
                        result.text = "Doğru + yanlış toplamı 60'ı geçemez."
                    }
                    else -> {
                        val blank = 60 - d - y
                        val score = (d * 1.5 - y * 0.4).coerceIn(0.0, 90.0)
                        result.setTextColor(getColor(R.color.emeraldDark))
                        result.text = "Tahmini puanın: %.1f / 90\n".format(score) +
                            "Doğru: $d (+%.1f) · Yanlış: $y (−%.1f) · Boş: $blank".format(d * 1.5, y * 0.4) +
                            "\n\nBu bir simülasyondur. Gerçek yerleştirme, her yıl değişen üniversite " +
                            "kesim puanlarına ve non-AB (yurt dışı) sıralamasına göre belirlenir."
                        AnalyticsProvider.track("tool_imat_score_computed")
                    }
                }
            }
        })
        body.addView(result)
        parent.addView(cardWrap(body))
    }

    // ── Tool 2: Cost & DSU scholarship (honest info) ────────────────────────────
    private fun buildCostInfo(parent: LinearLayout) {
        parent.addView(sectionLabel("MALİYET & BURS (DSU)"))
        val body = paddedCol()
        listOf(
            "Devlet üniversitesi harcı gelire göre ölçeklenir: yıllık yaklaşık €156–€4.000 (ISEE'ne göre).",
            "DSU (Diritto allo Studio) bursu: harç muafiyeti + yılda ~€5.000–€8.000 yaşam desteği + yurt/yemek imkânı. Gelir/ISEE şartlı; başvuru genelde Haziran–Ağustos.",
            "Örnek bir öğrencinin tüm masrafı ~€12.000/yıl olabildi — Türkiye'deki bir özel üniversitenin (~₺1.000.000/yıl) yaklaşık yarısı.",
            "IMAT sınav ücreti ~€130; kayıt Universitaly üzerinden yapılır."
        ).forEach { body.addView(bullet(it)) }
        body.addView(text(
            "Bu bir tahmindir, resmi teklif değildir. Kesin harç/burs için üniversitenin ve DSU/ISEE " +
                "başvurunun resmi sonucuna bak.",
            11f, R.color.textSecondary, topMargin = dpi(10f), lineMultiplier = 1.4f))
        parent.addView(cardWrap(body))
    }

    // ── Tool 3: Eligibility / denklik checklist ─────────────────────────────────
    private fun buildEligibility(parent: LinearLayout) {
        parent.addView(sectionLabel("UYGUNLUK / DENKLİK KONTROLÜ"))
        val body = paddedCol()
        val items = listOf(
            "Lise diplomamı aldım (veya bu yıl alacağım).",
            "TYT/AYT'ye girdim/gireceğim (konsolosluk diploma denkliği için genelde gerekir).",
            "İngilizce yeterliliğim var/olacak (programlar genelde IELTS ~5,0–5,5 ister; IMAT İngilizce'dir).",
            "IMAT'a kaydolmayı planlıyorum (Universitaly üzerinden, sınav Eylül'de)."
        )
        val boxes = items.map { label ->
            CheckBox(this).apply {
                text = label
                setTextColor(getColor(R.color.textPrimary))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(dpi(6f), dpi(6f), 0, dpi(6f))
            }.also { body.addView(it) }
        }
        val result = text("", 13f, R.color.emeraldDark, bold = true, topMargin = dpi(10f), lineMultiplier = 1.4f).apply {
            visibility = TextView.GONE
        }
        body.addView(MaterialButton(this).apply {
            text = "Değerlendir"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpi(48f)
            ).also { it.topMargin = dpi(12f) }
            cornerRadius = dpi(12f)
            setOnClickListener {
                val checked = boxes.count { it.isChecked }
                result.visibility = TextView.VISIBLE
                result.text = when {
                    checked == boxes.size -> "Temel şartları karşılıyor görünüyorsun. Sıradaki adım: IMAT'a hazırlan ve başvuru takvimini takip et."
                    checked >= 2 -> "İyi yoldasın ($checked/${boxes.size}). Eksik maddeleri tamamlayarak ilerle."
                    else -> "Başlangıç aşamasındasın. Önce lise/denklik ve İngilizce adımlarını planla."
                } + "\n\nKesin şartlar için İtalyan konsolosluğunun ve hedef üniversitenin resmi kaynağını kontrol et."
                AnalyticsProvider.track("tool_eligibility_evaluated")
            }
        })
        body.addView(result)
        parent.addView(cardWrap(body))
    }

    // ── builders ────────────────────────────────────────────────────────────────
    private fun numberField(): EditText = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setTextColor(getColor(R.color.textPrimary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
        background = GradientDrawable().apply {
            cornerRadius = dpi(12f).toFloat()
            setColor(getColor(R.color.white)); setStroke(dpi(1f), getColor(R.color.border))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(6f) }
    }

    private fun fieldLabel(t: String) = text(t, 13f, R.color.textSecondary, bold = true, topMargin = dpi(14f))
    private fun bullet(t: String) = text("•  $t", 13f, R.color.textPrimary, topMargin = dpi(8f), lineMultiplier = 1.45f)
    private fun sectionLabel(t: String) = text(t, 11f, R.color.emeraldDark, bold = true, topMargin = dpi(24f), letterSpacing = 0.1f)

    private fun cardWrap(child: android.view.View): MaterialCardView = MaterialCardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(10f) }
        radius = 16 * dp; cardElevation = 0f; strokeWidth = dpi(1f)
        setStrokeColor(getColor(R.color.border)); setCardBackgroundColor(getColor(R.color.white))
        addView(child)
    }

    private fun paddedCol() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun text(
        t: String, size: Float, colorRes: Int, bold: Boolean = false,
        topMargin: Int = 0, lineMultiplier: Float = 1.15f, letterSpacing: Float = 0f
    ): TextView = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(getColor(colorRes))
        if (bold) setTypeface(null, Typeface.BOLD)
        if (letterSpacing > 0) this.letterSpacing = letterSpacing
        setLineSpacing(0f, lineMultiplier)
        gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = topMargin }
    }
}
