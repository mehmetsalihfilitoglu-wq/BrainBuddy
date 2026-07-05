package com.brainbuddy.app.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Builds a professional multi-page PDF performance report using Android PdfDocument.
 * White background, emerald accents, Turkish labels, footer on every page.
 */
class PdfReportBuilder(
    private val context: Context,
    private val result: ReportStatsCalculator.ReportResult,
    private val wrongAnswerRecords: List<WrongAnswerRecord>,
    private val lineChartBitmap: Bitmap?,
    private val barChartBitmap: Bitmap?,
    private val isPremium: Boolean
) {

    data class WrongAnswerRecord(
        val subject: String,
        val stem: String,
        val choices: List<String>,
        val userAnswer: String,
        val correctAnswer: String,
        val category: String?,
        val dateMs: Long
    )

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 44f
        // BrainBuddy color system (strict)
        private val EMERALD = Color.parseColor("#12B5A6")
        private val EMERALD_DARK = Color.parseColor("#0FAE9A")
        private val EMERALD_LIGHT = Color.parseColor("#DFF7F4")
        private val SURFACE = Color.parseColor("#F7F9FA")
        private val DIVIDER = Color.parseColor("#E6ECEF")
        private val TEXT_PRIMARY = Color.parseColor("#1F2A30")
        private val TEXT_SECONDARY = Color.parseColor("#5F6B73")
        private val WEAK_TINT = Color.parseColor("#FCEAEA")
        private val CATEGORIES = listOf("Matematik", "Türkçe", "İngilizce", "Sosyal Bilgiler", "Fen Bilimleri")
    }

    private val dateFormat = SimpleDateFormat("d MMMM yyyy HH:mm", Locale("tr"))
    private val dateFormatShort = SimpleDateFormat("d MMM", Locale("tr"))

    @Throws(IOException::class)
    fun build(outFile: File) {
        val document = PdfDocument()
        val totalPages = computeTotalPages()
        var pageNum = 1
        try {
            addPage1Cover(document, pageNum, totalPages)
            pageNum++
            addPage2Charts(document, pageNum, totalPages)
            pageNum++
            addPage3Analysis(document, pageNum, totalPages)
            pageNum += addWrongAnswersPages(document, pageNum, totalPages)
            addPage5Distraction(document, pageNum, totalPages)
            FileOutputStream(outFile).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun computeTotalPages(): Int {
        var n = 5 // Cover + Dashboard + Subject Analysis + Wrong (min 1) + Distraction
        if (wrongAnswerRecords.isNotEmpty()) {
            val approxQuestionsPerPage = 3
            n = 4 + ((wrongAnswerRecords.size + approxQuestionsPerPage - 1) / approxQuestionsPerPage)
        }
        return n.coerceAtLeast(5)
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int, totalPages: Int) {
        val footerPaint = Paint().apply {
            color = TEXT_SECONDARY
            textSize = 8f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val footerY = PAGE_HEIGHT - 20f
        canvas.drawText("Bu rapor Barjin tarafından otomatik oluşturulmuştur.", MARGIN, footerY, footerPaint)
        val pageStr = "Sayfa $pageNum / $totalPages"
        canvas.drawText(pageStr, PAGE_WIDTH - MARGIN - footerPaint.measureText(pageStr), footerY, footerPaint)
    }

    private fun drawRoundedCard(canvas: Canvas, rect: RectF, paint: Paint, shadow: Boolean = true) {
        if (shadow) {
            val shadowRect = RectF(rect.left + 2, rect.top + 2, rect.right + 2, rect.bottom + 2)
            val shadowPaint = Paint().apply {
                color = Color.argb(25, 0, 0, 0)
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            canvas.drawRoundRect(shadowRect, 12f, 12f, shadowPaint)
        }
        canvas.drawRoundRect(rect, 12f, 12f, paint)
    }

    private fun addPage1Cover(document: PdfDocument, pageNum: Int, totalPages: Int) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        // Emerald header bar
        val headerH = 56f
        val headerPaint = Paint().apply {
            color = EMERALD
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(0f, 0f, PAGE_WIDTH.toFloat(), headerH, 0f, 0f, headerPaint)
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), headerH, headerPaint)

        val headerTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        canvas.drawText("Barjin Performans Raporu", MARGIN, headerH - 18f, headerTextPaint)
        drawLogoPlaceholder(canvas, PAGE_WIDTH - MARGIN - 52, 6f)

        var y = headerH + 28f

        // Summary card (rounded white with soft shadow)
        val summaryCard = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 118f)
        val cardPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val strokePaint = Paint().apply {
            color = DIVIDER
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        drawRoundedCard(canvas, summaryCard, cardPaint)
        canvas.drawRoundRect(summaryCard, 12f, 12f, strokePaint)

        val paintReg = Paint().apply {
            color = TEXT_PRIMARY
            textSize = 12f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintSec = Paint().apply {
            color = TEXT_SECONDARY
            textSize = 10f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        y += 20f
        paintSec.textSize = 9f
        canvas.drawText("ÖĞRENCİ BİLGİLERİ", MARGIN + 18f, y, paintSec)
        y += 18f
        paintReg.textSize = 13f
        canvas.drawText("Öğrenci: ${result.profileName}", MARGIN + 18f, y, paintReg)
        y += 20f
        paintReg.textSize = 11f
        canvas.drawText("Rapor Dönemi: ${result.range.labelTr}", MARGIN + 18f, y, paintReg)
        y += 16f
        canvas.drawText("Oluşturulma: ${dateFormat.format(Date())}", MARGIN + 18f, y, paintSec)
        y += 36f

        // Big success block
        val successStr = if (result.totals.noGradedAnswers) "—" else "%.0f%%".format(result.totals.accuracyPercent)
        val successBlock = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 90f)
        val successBgPaint = Paint().apply {
            color = EMERALD_LIGHT
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        drawRoundedCard(canvas, successBlock, successBgPaint)
        val bigLabel = Paint().apply {
            color = TEXT_SECONDARY
            textSize = 12f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val bigValue = Paint().apply {
            color = EMERALD_DARK
            textSize = 36f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("BAŞARI ORANI", (MARGIN + PAGE_WIDTH - MARGIN) / 2f - bigLabel.measureText("BAŞARI ORANI") / 2f, y + 28f, bigLabel)
        canvas.drawText(successStr, (PAGE_WIDTH) / 2f, y + 62f, bigValue)
        y += 100f

        // Metric chips (emerald light background)
        val t = result.totals
        val chipLabels = listOf("Test: ${t.testCount}", "Doğru: ${t.correct}", "Yanlış: ${t.wrong}", "Boş: ${t.blank}")
        val chipPaint = Paint().apply {
            color = EMERALD_LIGHT
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val chipTextPaint = Paint().apply {
            color = TEXT_PRIMARY
            textSize = 10f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        var chipX = MARGIN
        chipLabels.forEach { label ->
            val w = chipTextPaint.measureText(label) + 20f
            val chipRect = RectF(chipX, y, chipX + w, y + 24f)
            canvas.drawRoundRect(chipRect, 12f, 12f, chipPaint)
            canvas.drawText(label, chipX + 10f, y + 16f, chipTextPaint)
            chipX += w + 8f
        }
        y += 40f

        // Trend indicator
        val trendArrow = when (result.trendDirection) {
            1 -> "↑"
            -1 -> "↓"
            else -> "→"
        }
        val trendText = "Trend: $trendArrow %+.0f%% — Son 3 test vs önceki 3 test".format(result.trendDelta)
        val trendPaint = Paint().apply {
            color = EMERALD
            textSize = 11f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        canvas.drawText(trendText, MARGIN, y, trendPaint)

        drawFooter(canvas, pageNum, totalPages)
        document.finishPage(page)
    }

    private fun drawLogoPlaceholder(canvas: Canvas, x: Float, y: Float) {
        val logoRes = context.resources.getIdentifier("logo_placeholder", "drawable", context.packageName)
        if (logoRes != 0) {
            try {
                val d = context.getDrawable(logoRes)
                d?.setBounds(x.toInt(), y.toInt(), (x + 48).toInt(), (y + 48).toInt())
                d?.draw(canvas)
                return
            } catch (_: Exception) { }
        }
        val circlePaint = Paint().apply {
            color = EMERALD
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawCircle(x + 24, y + 24, 24f, circlePaint)
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("B", x + 24, y + 32, textPaint)
    }

    private fun addPage2Charts(document: PdfDocument, pageNum: Int, totalPages: Int) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val paintBold = Paint().apply {
            color = TEXT_PRIMARY
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintReg = Paint().apply {
            color = TEXT_PRIMARY
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintSec = Paint().apply {
            color = TEXT_SECONDARY
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        var y = MARGIN
        drawSectionTitle(canvas, "Performans Analizi", y, paintBold)
        y += 36f

        // Card 1: Line chart
        val card1H = if (lineChartBitmap != null) 300f else 150f
        val card1Rect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + card1H)
        drawRoundedCard(canvas, card1Rect, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true })
        canvas.drawRoundRect(card1Rect, 12f, 12f, Paint().apply { color = DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true })

        paintBold.textSize = 13f
        paintBold.color = EMERALD_DARK
        canvas.drawText("Son 10 Test Başarı Oranı", MARGIN + 18f, y + 28f, paintBold)
        paintBold.color = TEXT_PRIMARY

        if (lineChartBitmap != null) {
            val imgW = PAGE_WIDTH - MARGIN * 2 - 36f
            val imgH = 180f
            canvas.drawBitmap(lineChartBitmap, null, RectF(MARGIN + 18f, y + 40f, MARGIN + 18f + imgW, y + 40f + imgH), null)
            y += 40f + imgH + 12f
        } else {
            y += 50f
            paintSec.textSize = 10f
            canvas.drawText("Veri yetersiz (en az 2 test gerekli)", MARGIN + 18f, y, paintSec)
            y += 20f
        }

        paintSec.textSize = 9f
        canvas.drawText("Her nokta 1 testi temsil eder. Başarı = doğru / (doğru + yanlış)", MARGIN + 18f, y, paintSec)
        y += 28f

        // Grafik Yorumu box (light emerald)
        val yorumBox = RectF(MARGIN + 18f, y, PAGE_WIDTH - MARGIN - 18f, y + 40f)
        val yorumPaint = Paint().apply {
            color = EMERALD_LIGHT
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(yorumBox, 8f, 8f, yorumPaint)
        paintReg.textSize = 10f
        val grafYorum = when (result.trendDirection) {
            1 -> "Son testlerde başarı oranında yükseliş gözlemlenmektedir."
            -1 -> "Son testlerde başarı oranında düşüş görülmektedir. Bazı testlerde ani düşüşler olabilir."
            else -> "Başarı oranı stabil seyretmektedir. Tutarlı performans devam ediyor."
        }
        canvas.drawText("Grafik Yorumu: $grafYorum", MARGIN + 26f, y + 24f, paintReg)
        y += 56f

        // Card 2: Subject performance bars
        paintBold.textSize = 13f
        paintBold.color = EMERALD_DARK
        canvas.drawText("Konulara Göre Başarı", MARGIN, y, paintBold)
        paintBold.color = TEXT_PRIMARY
        y += 22f

        if (barChartBitmap != null) {
            val imgW = PAGE_WIDTH - MARGIN * 2
            val imgH = kotlin.math.min(200f, barChartBitmap.height * (imgW / barChartBitmap.width))
            val card2Rect = RectF(MARGIN, y - 4f, PAGE_WIDTH - MARGIN, y + imgH + 20f)
            drawRoundedCard(canvas, card2Rect, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true })
            canvas.drawRoundRect(card2Rect, 12f, 12f, Paint().apply { color = DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true })
            canvas.drawBitmap(barChartBitmap, null, RectF(MARGIN + 12f, y + 4f, PAGE_WIDTH - MARGIN - 12f, y + 4f + imgH), null)
        }

        drawFooter(canvas, pageNum, totalPages)
        document.finishPage(page)
    }

    private fun addPage3Analysis(document: PdfDocument, pageNum: Int, totalPages: Int) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val paintBold = Paint().apply {
            color = TEXT_PRIMARY
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintReg = Paint().apply {
            color = TEXT_PRIMARY
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintSec = Paint().apply {
            color = TEXT_SECONDARY
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintEmerald = Paint().apply {
            color = EMERALD
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        var y = MARGIN
        drawSectionTitle(canvas, "Ders Analizi", y, paintBold)
        y += 40f

        val subjects = result.perSubject.entries
            .map { (name, tc) ->
                val graded = tc.correct + tc.wrong
                val successPct = if (graded > 0) 100f * tc.correct / graded else 0f
                Triple(name, tc, successPct)
            }
            .sortedByDescending { it.third }

        val strongest = subjects.maxByOrNull { it.third }?.first
        val weakest = subjects.minByOrNull { it.third }?.first

        if (subjects.isEmpty()) {
            paintReg.textSize = 11f
            canvas.drawText("Bu dönemde ders verisi yok.", MARGIN, y, paintReg)
        } else {
            for ((name, tc, successPct) in subjects) {
                val isWeak = name == weakest && successPct < 60f
                val cardBg = Paint().apply {
                    color = if (isWeak) WEAK_TINT else Color.WHITE
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val cardH = if (name == strongest || name == weakest) 68f else 54f
                val subjectCard = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + cardH)
                drawRoundedCard(canvas, subjectCard, cardBg)
                canvas.drawRoundRect(subjectCard, 12f, 12f, Paint().apply { color = DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true })

                paintBold.textSize = 13f
                canvas.drawText(name, MARGIN + 16f, y + 20f, paintBold)
                paintReg.textSize = 10f
                canvas.drawText("Başarı: %.0f%%  |  Doğru: ${tc.correct}  |  Yanlış: ${tc.wrong}".format(successPct), MARGIN + 16f, y + 36f, paintReg)

                when {
                    name == strongest -> {
                        val badgePaint = Paint().apply {
                            color = EMERALD
                            textSize = 9f
                            typeface = Typeface.DEFAULT
                            isAntiAlias = true
                        }
                        val badgeRect = RectF(PAGE_WIDTH - MARGIN - 95, y + 10f, PAGE_WIDTH - MARGIN - 16, y + 26f)
                        val badgeBg = Paint().apply { color = EMERALD_LIGHT; style = Paint.Style.FILL; isAntiAlias = true }
                        canvas.drawRoundRect(badgeRect, 6f, 6f, badgeBg)
                        canvas.drawText("★ En güçlü alan", PAGE_WIDTH - MARGIN - 90, y + 23f, badgePaint)
                    }
                    name == weakest && successPct < 60f -> {
                        val badgePaint = Paint().apply {
                            color = TEXT_SECONDARY
                            textSize = 9f
                            typeface = Typeface.DEFAULT
                            isAntiAlias = true
                        }
                        canvas.drawText("▲ Geliştirme gerekli", PAGE_WIDTH - MARGIN - 105, y + 23f, badgePaint)
                    }
                }
                y += cardH + 10f
            }
        }

        y += 12f
        // Recommendation box
        val recCard = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 60f)
        val recBg = Paint().apply {
            color = EMERALD_LIGHT
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        drawRoundedCard(canvas, recCard, recBg)
        paintBold.textSize = 11f
        paintBold.color = EMERALD_DARK
        canvas.drawText("Öneri", MARGIN + 16f, y + 18f, paintBold)
        paintBold.color = TEXT_PRIMARY
        paintReg.textSize = 10f
        val weakSubs = subjects.filter { (name, _, pct) -> pct < 60f }
        val recText = when {
            weakSubs.isEmpty() -> "Mevcut performans iyi. Düzenli çalışmaya devam edin."
            else -> "${weakSubs.joinToString(", ") { it.first }} başarı oranı düşük. Mini testlerle güçlendirilmesi önerilir."
        }
        canvas.drawText(recText, MARGIN + 16f, y + 38f, paintReg)
        y += 72f

        paintSec.textSize = 10f
        canvas.drawText("Toplam XP: ${result.totals.totalXp}", MARGIN, y, paintEmerald)

        drawFooter(canvas, pageNum, totalPages)
        document.finishPage(page)
    }

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString().ifBlank { packageName }
        } catch (_: Exception) {
            packageName
        }
    }

    private fun addWrongAnswersPages(document: PdfDocument, startPageNum: Int, totalPages: Int): Int {
        if (wrongAnswerRecords.isEmpty()) {
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, startPageNum).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            val paintBold = Paint().apply {
                color = TEXT_PRIMARY; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
            }
            drawSectionTitle(canvas, "Yanlış Yapılan Sorular", MARGIN + 28f, paintBold)
            val paintReg = Paint().apply {
                color = TEXT_SECONDARY; textSize = 11f; isAntiAlias = true
            }
            canvas.drawText("Bu dönemde yanlış cevap kaydı yok.", MARGIN + 18f, MARGIN + 72f, paintReg)
            drawFooter(canvas, startPageNum, totalPages)
            document.finishPage(page)
            return 1
        }

        val byCategory = linkedMapOf<String, List<WrongAnswerRecord>>()
        for (cat in CATEGORIES) {
            val list = wrongAnswerRecords.filter { it.subject == cat }
            if (list.isNotEmpty()) byCategory[cat] = list
        }
        val other = wrongAnswerRecords.filter { it.subject !in CATEGORIES }.groupBy { it.subject }
        other.forEach { (k, v) -> byCategory[k] = v }

        var pageNum = startPageNum
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f
        val contentBottom = PAGE_HEIGHT - 60f
        // Inner text widths (PDF points = pixels here)
        val CARD_TEXT_W = (PAGE_WIDTH - MARGIN * 2 - 48).toInt()   // stem / answer rows
        val CHOICE_TEXT_W = (PAGE_WIDTH - MARGIN * 2 - 66).toInt() // choice body (after letter prefix)
        val CARD_GAP = 10f
        val answerLabels = listOf("A", "B", "C", "D")

        // Reusable paints
        val paintSubject = Paint().apply {
            color = EMERALD_DARK; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val paintStemLabel = Paint().apply {
            color = EMERALD_DARK; textSize = 9f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val paintStem = Paint().apply {
            color = TEXT_PRIMARY; textSize = 10f; typeface = Typeface.DEFAULT; isAntiAlias = true
        }
        val paintChoice = Paint().apply {
            color = TEXT_SECONDARY; textSize = 9f; typeface = Typeface.DEFAULT; isAntiAlias = true
        }
        val paintCorrectChoice = Paint().apply {
            color = Color.parseColor("#0B7A52"); textSize = 9f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val paintWrongChoice = Paint().apply {
            color = Color.parseColor("#B83232"); textSize = 9f; typeface = Typeface.DEFAULT; isAntiAlias = true
        }
        val paintCorrectAnswer = Paint().apply {
            color = Color.parseColor("#0B7A52"); textSize = 9f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true
        }
        val paintUserAnswer = Paint().apply {
            color = Color.parseColor("#B83232"); textSize = 9f; typeface = Typeface.DEFAULT; isAntiAlias = true
        }
        val paintDate = Paint().apply {
            color = TEXT_SECONDARY; textSize = 8f; typeface = Typeface.DEFAULT; isAntiAlias = true
        }
        val paintDivLine = Paint().apply {
            color = DIVIDER; style = Paint.Style.STROKE; strokeWidth = 0.5f; isAntiAlias = true
        }
        val paintCardFill = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true }
        val paintCardBorder = Paint().apply {
            color = DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true
        }

        fun startNewPage() {
            currentPage?.let {
                drawFooter(it.canvas, pageNum - 1, totalPages)
                document.finishPage(it)
            }
            val pi = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
            currentPage = document.startPage(pi)
            canvas = currentPage!!.canvas
            canvas!!.drawColor(Color.WHITE)
            val tp = Paint().apply { color = TEXT_PRIMARY; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
            val label = if (pageNum == startPageNum) "Yanlış Yapılan Sorular" else "Yanlış Yapılan Sorular (devam)"
            drawSectionTitle(canvas!!, label, MARGIN + 28f, tp)
            y = MARGIN + 60f
            pageNum++
        }

        startNewPage()

        for ((_, records) in byCategory.entries.sortedByDescending { it.value.size }) {
            for (r in records) {
                // --- Pre-measure all text heights for dynamic card sizing ---
                val stemH = measureWrappedTextHeight(r.stem, paintStem, CARD_TEXT_W)

                var totalChoiceH = 0f
                val perChoiceH = r.choices.take(4).mapIndexed { i, choice ->
                    val label = "${answerLabels.getOrElse(i) { "$i" }}) $choice"
                    val h = measureWrappedTextHeight(label, paintChoice, CHOICE_TEXT_W)
                    totalChoiceH += h + 4f
                    h
                }

                val correctH = measureWrappedTextHeight(
                    "✓  Doğru cevap: ${r.correctAnswer}", paintCorrectAnswer, CARD_TEXT_W
                )
                val userAnswerText = if (r.userAnswer == "—" || r.userAnswer.isBlank())
                    "✗  Senin cevabın: boş bırakıldı"
                else
                    "✗  Senin cevabın: ${r.userAnswer}"
                val userH = measureWrappedTextHeight(userAnswerText, paintUserAnswer, CARD_TEXT_W)

                // cardH = top_pad + subject_row + divider_gap + soru_label + stem + gap
                //       + choices + extra_gap + divider_gap + correct + gap + user + bottom_pad
                val cardH = (14f + 14f + 8f + 14f + stemH + 10f
                        + totalChoiceH + 6f + 8f
                        + correctH + 4f + userH + 14f)

                // Start new page if card doesn't fit (guard against infinite loop if card > page)
                if (y + cardH + CARD_GAP > contentBottom && y > MARGIN + 70f) {
                    startNewPage()
                }

                val c = canvas!!

                // --- Draw card frame ---
                val qCard = RectF(MARGIN + 4f, y, PAGE_WIDTH - MARGIN - 4f, y + cardH)
                drawRoundedCard(c, qCard, paintCardFill, shadow = false)
                c.drawRoundRect(qCard, 10f, 10f, paintCardBorder)

                var cy = y + 14f

                // Subject + date header
                c.drawText(r.subject, MARGIN + 16f, cy, paintSubject)
                val dateStr = dateFormatShort.format(Date(r.dateMs))
                c.drawText(dateStr, PAGE_WIDTH - MARGIN - 16f - paintDate.measureText(dateStr), cy, paintDate)
                cy += 12f

                // Divider
                c.drawLine(MARGIN + 12f, cy, PAGE_WIDTH - MARGIN - 12f, cy, paintDivLine)
                cy += 8f

                // "Soru:" bold label
                c.drawText("Soru:", MARGIN + 16f, cy + 9f, paintStemLabel)
                cy += 14f

                // Full stem text — wrapped, no truncation
                drawWrappedText(c, r.stem, paintStem, MARGIN + 16f, cy, CARD_TEXT_W)
                cy += stemH + 10f

                // Choices A/B/C/D — each wrapped, color-coded
                r.choices.take(4).forEachIndexed { i, choice ->
                    val label = "${answerLabels.getOrElse(i) { "$i" }}) $choice"
                    val isCorrect = choice == r.correctAnswer
                    val isUserWrong = choice == r.userAnswer && !isCorrect
                    val chPaint = when {
                        isCorrect -> paintCorrectChoice
                        isUserWrong -> paintWrongChoice
                        else -> paintChoice
                    }
                    drawWrappedText(c, label, chPaint, MARGIN + 16f, cy, CHOICE_TEXT_W)
                    cy += (perChoiceH.getOrElse(i) { 11f }) + 4f
                }
                cy += 6f

                // Divider before answer summary
                c.drawLine(MARGIN + 12f, cy, PAGE_WIDTH - MARGIN - 12f, cy, paintDivLine)
                cy += 8f

                // Correct answer row
                drawWrappedText(c, "✓  Doğru cevap: ${r.correctAnswer}", paintCorrectAnswer, MARGIN + 16f, cy, CARD_TEXT_W)
                cy += correctH + 4f

                // User answer row
                drawWrappedText(c, userAnswerText, paintUserAnswer, MARGIN + 16f, cy, CARD_TEXT_W)

                y += cardH + CARD_GAP
            }
        }

        currentPage?.let {
            drawFooter(it.canvas, pageNum - 1, totalPages)
            document.finishPage(it)
        }
        return (pageNum - startPageNum).coerceAtLeast(1)
    }

    private fun addPage5Distraction(document: PdfDocument, pageNum: Int, totalPages: Int) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val paintBold = Paint().apply {
            color = TEXT_PRIMARY
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintReg = Paint().apply {
            color = TEXT_PRIMARY
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintSec = Paint().apply {
            color = TEXT_SECONDARY
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        var y = MARGIN
        drawSectionTitle(canvas, "Dikkat Dağıtıcı Uygulama Denemeleri", y, paintBold)
        y += 44f

        val topApps = result.attemptedApps.take(8)
        val maxCount = topApps.maxOfOrNull { it.second } ?: 1

        if (topApps.isEmpty()) {
            paintReg.textSize = 11f
            canvas.drawText("Bu dönemde engellenen uygulama denemesi kaydı yok.", MARGIN, y, paintReg)
        } else {
            val cardRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 28f * topApps.size + 56f)
            drawRoundedCard(canvas, cardRect, Paint().apply { color = Color.WHITE; style = Paint.Style.FILL; isAntiAlias = true })
            canvas.drawRoundRect(cardRect, 12f, 12f, Paint().apply { color = DIVIDER; style = Paint.Style.STROKE; strokeWidth = 1f; isAntiAlias = true })

            paintReg.textSize = 10f
            topApps.forEach { (pkg, count) ->
                val label = resolveAppLabel(pkg)
                val barW = 180f * (count.toFloat() / maxCount.coerceAtLeast(1))
                canvas.drawText(label, MARGIN + 18f, y + 20f, paintReg)
                val barPaint = Paint().apply {
                    color = EMERALD
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }
                val barRect = RectF(PAGE_WIDTH - MARGIN - 220f, y + 8f, PAGE_WIDTH - MARGIN - 220f + barW, y + 22f)
                canvas.drawRoundRect(barRect, 4f, 4f, barPaint)
                paintSec.textSize = 10f
                canvas.drawText("$count", PAGE_WIDTH - MARGIN - 30f, y + 18f, paintSec)
                y += 28f
            }
            y += 16f
            paintSec.textSize = 10f
            canvas.drawText("Öğrenci bu uygulamalara erişmeye çalışmıştır.", MARGIN + 18f, y, paintSec)
        }

        drawFooter(canvas, pageNum, totalPages)
        document.finishPage(page)
    }

    /**
     * Draws [text] wrapped to [maxWidth] using StaticLayout, starting at (x, y).
     * Returns the rendered height so the caller can advance y correctly.
     */
    private fun drawWrappedText(
        canvas: Canvas,
        text: String,
        paint: Paint,
        x: Float,
        y: Float,
        maxWidth: Int
    ): Float {
        val tp = TextPaint(paint)
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, tp, maxWidth.coerceAtLeast(50))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return layout.height.toFloat()
    }

    /** Returns the height that [drawWrappedText] would occupy for the same arguments. */
    private fun measureWrappedTextHeight(text: String, paint: Paint, maxWidth: Int): Float {
        val tp = TextPaint(paint)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, tp, maxWidth.coerceAtLeast(50))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.1f)
            .setIncludePad(false)
            .build()
            .height.toFloat()
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float, titlePaint: Paint) {
        val fillPaint = Paint().apply {
            color = EMERALD
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(MARGIN, y - 28f, PAGE_WIDTH - MARGIN, y + 4f, 8f, 8f, fillPaint)
        val saved = titlePaint.color
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 15f
        canvas.drawText(title, MARGIN + 12f, y - 10f, titlePaint)
        titlePaint.color = saved
    }
}
