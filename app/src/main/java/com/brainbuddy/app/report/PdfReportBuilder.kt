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
        val userAnswer: String,
        val correctAnswer: String,
        val category: String?,
        val dateMs: Long
    )

    companion object {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 50f
        private val EMERALD = Color.rgb(18, 184, 166)
        private val LIGHT_GRAY = Color.rgb(242, 251, 250)
        private val TEXT_GRAY = Color.rgb(107, 107, 107)
        private const val MAX_STEM_LENGTH = 120
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
            addWrongAnswersPages(document, pageNum, totalPages)
            FileOutputStream(outFile).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun computeTotalPages(): Int {
        var n = 3
        if (wrongAnswerRecords.isNotEmpty()) {
            val approxLinesPerPage = 12
            val totalLines = wrongAnswerRecords.groupBy { it.subject }.values.sumOf { list ->
                list.size * 4 + 2
            }
            n += (totalLines + approxLinesPerPage - 1) / approxLinesPerPage
        }
        return n.coerceAtLeast(4)
    }

    private fun drawFooter(canvas: Canvas, pageNum: Int, totalPages: Int) {
        val footerPaint = Paint().apply {
            color = TEXT_GRAY
            textSize = 9f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val footerY = PAGE_HEIGHT - 24f
        canvas.drawText("BrainBuddy Öğrenci Performans Raporu", MARGIN, footerY, footerPaint)
        val pageStr = "Sayfa $pageNum / $totalPages"
        val pageW = footerPaint.measureText(pageStr)
        canvas.drawText(pageStr, PAGE_WIDTH - MARGIN - pageW, footerY, footerPaint)
        canvas.drawText(dateFormat.format(Date()), PAGE_WIDTH / 2f - footerPaint.measureText(dateFormat.format(Date())) / 2, footerY, footerPaint)
    }

    private fun addPage1Cover(document: PdfDocument, pageNum: Int, totalPages: Int) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val paintBold = Paint().apply {
            color = Color.BLACK
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintReg = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintGray = Paint().apply {
            color = TEXT_GRAY
            textSize = 11f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val fillPaint = Paint().apply {
            color = EMERALD
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        drawLogoPlaceholder(canvas, PAGE_WIDTH - MARGIN - 60, 40f)

        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 4f, fillPaint)

        var y = 70f
        canvas.drawText("BrainBuddy Öğrenci Performans Raporu", MARGIN, y, paintBold)
        y += 36f

        paintReg.textSize = 14f
        canvas.drawText("Öğrenci: ${result.profileName}", MARGIN, y, paintReg)
        y += 22f

        paintReg.textSize = 11f
        canvas.drawText("Dönem: ${result.range.labelTr}", MARGIN, y, paintReg)
        y += 18f
        canvas.drawText("Oluşturulma: ${dateFormat.format(Date())}", MARGIN, y, paintGray)
        y += 48f

        val t = result.totals
        val kpiCard = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 140f)
        val cardPaint = Paint().apply {
            color = LIGHT_GRAY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(kpiCard, 12f, 12f, cardPaint)
        val strokePaint = Paint().apply {
            color = EMERALD
            style = Paint.Style.STROKE
            strokeWidth = 1f
            isAntiAlias = true
        }
        canvas.drawRoundRect(kpiCard, 12f, 12f, strokePaint)

        y += 24f
        paintReg.textSize = 10f
        val successStr = if (t.noGradedAnswers) "—" else "%.0f%%".format(t.accuracyPercent)
        val rows = listOf(
            "Başarı %" to successStr,
            "Toplam Test" to t.testCount.toString(),
            "Doğru" to t.correct.toString(),
            "Yanlış" to t.wrong.toString(),
            "Boş" to t.blank.toString(),
            "Engellenen deneme" to t.blockedCount.toString()
        )
        rows.forEach { (label, value) ->
            canvas.drawText("$label: $value", MARGIN + 16f, y, paintReg)
            y += 18f
        }

        y += 24f
        val trendArrow = when (result.trendDirection) {
            1 -> "↑"
            -1 -> "↓"
            else -> "→"
        }
        val trendText = "Trend: Son 3 test vs önceki 3 test: $trendArrow %+.0f%%".format(result.trendDelta)
        paintReg.color = EMERALD
        canvas.drawText(trendText, MARGIN, y, paintReg)

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
            color = Color.BLACK
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintReg = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val fillPaint = Paint().apply {
            color = EMERALD
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var y = MARGIN
        drawSectionTitle(canvas, "Grafikler", y, paintBold, fillPaint)
        y += 40f

        paintBold.textSize = 12f
        canvas.drawText("Son 10 Test Başarı Oranı", MARGIN, y, paintBold)
        y += 18f

        if (lineChartBitmap != null) {
            val imgW = 480f
            val imgH = 220f
            canvas.drawBitmap(lineChartBitmap, null, RectF(MARGIN, y, MARGIN + imgW, y + imgH), null)
            y += imgH + 12f
        }

        paintReg.textSize = 9f
        canvas.drawText("Her nokta 1 testtir. Başarı = doğru/(doğru+yanlış). Boşlar başarıya dahil edilmez.", MARGIN, y, paintReg)
        y += 12f
        canvas.drawText("En az 1 soru cevaplanmalı (tam boş testler grafikte gösterilmez).", MARGIN, y, paintReg)
        y += 28f

        paintBold.textSize = 12f
        canvas.drawText("Konulara Göre (Doğru/Toplam)", MARGIN, y, paintBold)
        y += 18f

        if (barChartBitmap != null) {
            val imgW = 480f
            val imgH = kotlin.math.min(220f, barChartBitmap.height * (imgW / barChartBitmap.width))
            canvas.drawBitmap(barChartBitmap, null, RectF(MARGIN, y, MARGIN + imgW, y + imgH), null)
            y += imgH + 20f
        }

        val commentBox = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 44f)
        val boxPaint = Paint().apply {
            color = LIGHT_GRAY
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        canvas.drawRoundRect(commentBox, 8f, 8f, boxPaint)
        paintReg.textSize = 10f
        val grafYorum = when (result.trendDirection) {
            1 -> "Grafik Yorumu: Son testlerde başarı artıyor."
            -1 -> "Grafik Yorumu: Son testlerde başarı azalıyor."
            else -> "Grafik Yorumu: Başarı oranı stabil."
        }
        canvas.drawText(grafYorum, MARGIN + 12f, y + 26f, paintReg)

        drawFooter(canvas, pageNum, totalPages)
        document.finishPage(page)
    }

    private fun addPage3Analysis(document: PdfDocument, pageNum: Int, totalPages: Int) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas
        canvas.drawColor(Color.WHITE)

        val paintBold = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintReg = Paint().apply {
            color = Color.BLACK
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintGray = Paint().apply {
            color = TEXT_GRAY
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintEmerald = Paint().apply {
            color = EMERALD
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val fillPaint = Paint().apply {
            color = EMERALD
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        var y = MARGIN
        drawSectionTitle(canvas, "Ders Analizi + Öneriler", y, paintBold, fillPaint)
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

        paintReg.textSize = 10f
        paintGray.textSize = 9f

        if (subjects.isEmpty()) {
            canvas.drawText("Bu dönemde ders verisi yok.", MARGIN, y, paintReg)
        } else {
            for ((name, tc, successPct) in subjects) {
                val badges = mutableListOf<String>()
                if (name == strongest) badges.add("★ En güçlü")
                if (name == weakest) badges.add("▲ En zayıf")
                val line = "$name — Başarı: %.0f%% | Doğru: ${tc.correct} | Yanlış: ${tc.wrong} | Boş: ${tc.blank}".format(successPct)
                canvas.drawText(line, MARGIN, y, paintReg)
                y += 14f
                if (badges.isNotEmpty()) {
                    canvas.drawText(badges.joinToString(" "), MARGIN + 8f, y, paintGray)
                    y += 16f
                } else y += 6f
            }
        }

        y += 16f
        paintBold.textSize = 11f
        canvas.drawText("Öneriler", MARGIN, y, paintBold)
        y += 16f
        paintReg.textSize = 10f
        val recommendations = mutableListOf<String>()
        subjects.filter { (_, _, pct) -> pct < 60f }.forEach { (name, _, _) ->
            recommendations.add("• $name başarısı düşük: Bu dersten mini test önerilir.")
        }
        if (result.totals.accuracyPercent < 60f && !result.totals.noGradedAnswers) {
            recommendations.add("• Genel başarı hedefi: Günlük test hedefi belirleyin.")
        }
        if (recommendations.isEmpty()) {
            canvas.drawText("• Mevcut performans iyi. Düzenli çalışmaya devam edin.", MARGIN, y, paintReg)
            y += 16f
        } else {
            recommendations.forEach { rec ->
                canvas.drawText(rec, MARGIN, y, paintReg)
                y += 14f
            }
        }

        y += 20f
        paintBold.textSize = 11f
        canvas.drawText("En çok denenen uygulamalar", MARGIN, y, paintBold)
        y += 16f
        paintReg.textSize = 10f
        val topApps = result.attemptedApps.take(5)
        val maxCount = topApps.maxOfOrNull { it.second } ?: 1
        for ((pkg, count) in topApps) {
            val label = resolveAppLabel(pkg)
            val barW = 120f * (count.toFloat() / maxCount)
            canvas.drawText(label, MARGIN, y + 10f, paintReg)
            val barRect = RectF(PAGE_WIDTH - MARGIN - 150, y - 4, PAGE_WIDTH - MARGIN - 150 + barW, y + 12)
            canvas.drawRoundRect(barRect, 4f, 4f, fillPaint)
            canvas.drawText("$count", PAGE_WIDTH - MARGIN - 30, y + 10f, paintGray)
            y += 24f
        }
        if (topApps.isEmpty()) {
            canvas.drawText("Veri yok.", MARGIN, y, paintGray)
            y += 20f
        }

        y += 16f
        paintBold.textSize = 11f
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
                color = Color.BLACK
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            val fillPaint = Paint().apply {
                color = EMERALD
                style = Paint.Style.FILL
                isAntiAlias = true
            }
            drawSectionTitle(canvas, "Yanlış Yapılan Sorular (Kategorili)", MARGIN, paintBold, fillPaint)
            val paintReg = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                isAntiAlias = true
            }
            canvas.drawText("Bu dönemde yanlış cevap kaydı yok.", MARGIN, MARGIN + 60f, paintReg)
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
        val contentBottom = PAGE_HEIGHT - 50f

        fun ensurePage() {
            if (currentPage == null || y > contentBottom) {
                currentPage?.let { document.finishPage(it) }
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create()
                currentPage = document.startPage(pageInfo)
                canvas = currentPage!!.canvas
                canvas!!.drawColor(Color.WHITE)
                if (pageNum == startPageNum) {
                    val paintBold = Paint().apply {
                        color = Color.BLACK
                        typeface = Typeface.DEFAULT_BOLD
                        isAntiAlias = true
                    }
                    val fillPaint = Paint().apply {
                        color = EMERALD
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    drawSectionTitle(canvas!!, "Yanlış Yapılan Sorular (Kategorili)", MARGIN, paintBold, fillPaint)
                    y = MARGIN + 48f
                } else {
                    y = MARGIN + 24f
                }
                pageNum++
            }
        }

        val paintBold = Paint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val paintReg = Paint().apply {
            color = Color.BLACK
            textSize = 9f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }
        val paintGray = Paint().apply {
            color = TEXT_GRAY
            textSize = 8f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        for ((subject, records) in byCategory.entries.sortedByDescending { it.value.size }) {
            ensurePage()
            canvas!!.drawText("$subject (${records.size} yanlış)", MARGIN, y, paintBold)
            y += 18f

            for (r in records) {
                ensurePage()
                val stemShort = if (r.stem.length > MAX_STEM_LENGTH) r.stem.take(MAX_STEM_LENGTH) + "…" else r.stem
                canvas!!.drawText("Soru: $stemShort", MARGIN + 8f, y, paintReg)
                y += 12f
                canvas!!.drawText("Öğrenci Cevabı: ${r.userAnswer}  |  Doğru: ${r.correctAnswer}", MARGIN + 8f, y, paintGray)
                y += 10f
                canvas!!.drawText("Test tarihi: ${dateFormatShort.format(Date(r.dateMs))}", MARGIN + 8f, y, paintGray)
                y += 16f
            }
            y += 12f
        }

        currentPage?.let {
            drawFooter(it.canvas, pageNum - 1, totalPages)
            document.finishPage(it)
        }
        return (pageNum - startPageNum).coerceAtLeast(1)
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, y: Float, titlePaint: Paint, fillPaint: Paint) {
        canvas.drawRect(MARGIN, y - 24f, PAGE_WIDTH - MARGIN, y, fillPaint)
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 14f
        canvas.drawText(title, MARGIN + 8f, y - 6f, titlePaint)
        titlePaint.color = Color.BLACK
    }
}
