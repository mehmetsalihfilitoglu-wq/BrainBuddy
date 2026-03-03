package com.brainbuddy.app.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.brainbuddy.app.core.StatsRepository
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a professional multi-page PDF performance report using Android native PdfDocument.
 * No external PDF library required; uses system fonts and bitmap charts.
 */
class PdfReportBuilder(
    private val context: Context,
    private val model: StatsRepository.ReportsUiModel,
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
        private val TEXT_GRAY = Color.rgb(107, 107, 107)
        private const val MAX_STEM_LENGTH = 120
    }

    @Throws(IOException::class)
    fun build(outFile: File) {
        val document = PdfDocument()
        try {
            addPage1(document)
            addPage2(document)
            addPage3(document)
            addPage4(document)
            FileOutputStream(outFile).use { document.writeTo(it) }
        } finally {
            document.close()
        }
    }

    private fun addPage1(document: PdfDocument) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

        val paintBold = Paint().apply {
            color = Color.WHITE
            textSize = 24f
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

        // Emerald header band
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 100f, fillPaint)

        var y = 55f
        canvas.drawText("BrainBuddy", MARGIN, y, paintBold)
        y += 20f
        paintReg.textSize = 12f
        canvas.drawText("Performans Raporu", MARGIN, y, paintReg)

        val studentName = ProfileStore.currentProfileName(context)
        paintReg.color = Color.BLACK
        paintReg.textSize = 14f
        y += 60f
        canvas.drawText("Öğrenci: $studentName", MARGIN, y, paintReg)

        val rangeLabel = when (model.range) {
            StatsRepository.ReportRange.TODAY -> "Bugün"
            StatsRepository.ReportRange.SEVEN -> "7 Gün"
            StatsRepository.ReportRange.THIRTY -> "30 Gün"
        }
        paintReg.textSize = 11f
        y += 22f
        canvas.drawText("Rapor Dönemi: $rangeLabel", MARGIN, y, paintReg)

        val ws = model.weeklySuccess
        y += 18f
        canvas.drawText("Toplam Test: ${ws.testCount}", MARGIN, y, paintReg)
        y += 18f
        canvas.drawText("Doğru: ${ws.correct}  |  Yanlış: ${ws.wrong}  |  Boş: ${ws.blank}", MARGIN, y, paintReg)
        y += 18f
        val successStr = if (ws.noGradedAnswers) "—" else "%.0f%%".format(ws.accuracyPercent)
        canvas.drawText("Başarı Oranı: $successStr", MARGIN, y, paintReg)

        val adv = model.advancedStats
        val trendStr = when (adv.trendDirection) {
            StatsRepository.TrendDirection.IMPROVING -> "↑ +%.0f%%".format(adv.trendDelta)
            StatsRepository.TrendDirection.DECLINING -> "↓ %.0f%%".format(adv.trendDelta)
            else -> "→"
        }
        y += 18f
        canvas.drawText("Trend: $trendStr", MARGIN, y, paintReg)

        val dateStr = SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Date())
        y += 36f
        canvas.drawText("Oluşturulma: $dateStr", MARGIN, y, paintGray)

        document.finishPage(page)
    }

    private fun addPage2(document: PdfDocument) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

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
        drawSectionTitle(canvas, "Performans Analizi", y, paintBold, fillPaint)
        y += 48f

        val ws = model.weeklySuccess
        paintReg.textSize = 10f
        val rows = listOf(
            "Toplam Test" to ws.testCount.toString(),
            "Doğru" to ws.correct.toString(),
            "Yanlış" to ws.wrong.toString(),
            "Boş" to ws.blank.toString(),
            "Başarı %" to (if (ws.noGradedAnswers) "—" else "%.0f".format(ws.accuracyPercent))
        )
        rows.forEach { (label, value) ->
            canvas.drawText("$label:", MARGIN, y, paintReg)
            canvas.drawText(value, MARGIN + 120f, y, paintReg)
            y += 16f
        }
        y += 16f

        val tc = model.trendChart
        if (!tc.isEmpty && tc.points.size >= 6) {
            val last3 = tc.points.takeLast(3).map { it.percent }
            val prev3 = tc.points.dropLast(3).takeLast(3).map { it.percent }
            val last3Avg = last3.average().toFloat()
            val prev3Avg = prev3.average().toFloat()
            val change = last3Avg - prev3Avg
            canvas.drawText("Son 3 test ort: %.0f%%".format(last3Avg), MARGIN, y, paintReg)
            y += 14f
            canvas.drawText("Önceki 3 test ort: %.0f%%".format(prev3Avg), MARGIN, y, paintReg)
            y += 14f
            canvas.drawText("Değişim: %+.0f%%".format(change), MARGIN, y, paintReg)
            y += 24f
        }

        if (lineChartBitmap != null) {
            canvas.drawText("Son 10 Test Başarı Oranı", MARGIN, y, paintReg)
            y += 20f
            val imgW = 320f
            val imgH = 140f
            canvas.drawBitmap(lineChartBitmap, null, android.graphics.RectF(MARGIN, y, MARGIN + imgW, y + imgH), null)
            y += imgH + 24f
        }

        if (barChartBitmap != null) {
            canvas.drawText("Konu Performansı (Doğru/Toplam)", MARGIN, y, paintReg)
            y += 20f
            val imgW = 320f
            val imgH = kotlin.math.min(180f, barChartBitmap.height * (imgW / barChartBitmap.width))
            canvas.drawBitmap(barChartBitmap, null, android.graphics.RectF(MARGIN, y, MARGIN + imgW, y + imgH), null)
        }

        document.finishPage(page)
    }

    private fun addPage3(document: PdfDocument) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

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
        drawSectionTitle(canvas, "Konu Analizi", y, paintBold, fillPaint)
        y += 48f

        val topics = model.topics.topicCounts
        if (topics.isEmpty()) {
            paintReg.textSize = 10f
            canvas.drawText("Bu dönemde konu verisi yok.", MARGIN, y, paintReg)
            document.finishPage(page)
            return
        }

        val subjectStats = topics.entries
            .map { (name, tc) ->
                val graded = tc.correct + tc.wrong
                val successPct = if (graded > 0) 100f * tc.correct / graded else 0f
                Triple(name, tc, successPct)
            }
            .sortedByDescending { it.third }

        val strongest = subjectStats.maxByOrNull { it.third }?.first
        val weakest = subjectStats.minByOrNull { it.third }?.first
        val mostWrong = topics.entries.maxByOrNull { it.value.wrong }?.key

        paintReg.textSize = 10f
        paintGray.textSize = 9f

        subjectStats.forEach { (name, tc, successPct) ->
            val interpret = when {
                successPct < 60f -> "Geliştirme gerekli."
                successPct > 80f -> "Güçlü alan."
                else -> {
                    val adv = model.advancedStats
                    val weakNames = adv.weakSubjects.map { it.name }
                    if (name in weakNames) "Son testlerde düşüş var." else ""
                }
            }
            val interpretFinal = if (interpret.isEmpty() && successPct in 60f..80f) "Orta seviye." else interpret

            val badges = mutableListOf<String>()
            if (name == strongest) badges.add("★ En güçlü")
            if (name == weakest) badges.add("▲ En zayıf")
            if (name == mostWrong && tc.wrong > 0) badges.add("⚠ En çok yanlış")

            paintReg.color = Color.BLACK
            val line1 = "$name — %.0f%% başarı | ${tc.correct}/${tc.total} | Yanlış: ${tc.wrong}".format(successPct)
            canvas.drawText(line1, MARGIN, y, paintReg)
            y += 14f
            if (interpretFinal.isNotEmpty()) {
                paintGray.color = TEXT_GRAY
                canvas.drawText("  $interpretFinal ${badges.joinToString(" ")}", MARGIN, y, paintGray)
                y += 16f
            } else if (badges.isNotEmpty()) {
                canvas.drawText("  ${badges.joinToString(" ")}", MARGIN, y, paintGray)
                y += 16f
            } else {
                y += 6f
            }
        }

        val adv = model.advancedStats
        adv.smartRecommendation?.let { rec ->
            y += 12f
            paintEmerald.textSize = 10f
            canvas.drawText("Öneri: ${rec.message}", MARGIN, y, paintEmerald)
        }

        document.finishPage(page)
    }

    private fun addPage4(document: PdfDocument) {
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = document.startPage(pageInfo)
        val canvas = page.canvas

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
        drawSectionTitle(canvas, "Yanlış Cevaplar (Kategorize)", y, paintBold, fillPaint)
        y += 48f

        if (wrongAnswerRecords.isEmpty()) {
            paintReg.textSize = 10f
            canvas.drawText("Bu dönemde yanlış cevap kaydı yok.", MARGIN, y, paintReg)
            document.finishPage(page)
            return
        }

        val bySubject = wrongAnswerRecords.groupBy { it.subject }
        val sortedSubjects = bySubject.entries.sortedByDescending { it.value.size }

        val maxToShow = if (isPremium) Int.MAX_VALUE else 3
        var totalShown = 0

        paintBold.textSize = 11f
        paintReg.textSize = 9f
        paintGray.textSize = 9f
        paintEmerald.textSize = 9f

        for ((subject, records) in sortedSubjects) {
            val sortedRecords = records.sortedByDescending { it.dateMs }
            paintBold.color = Color.BLACK
            canvas.drawText("$subject (${records.size} yanlış)", MARGIN, y, paintBold)
            y += 20f

            val toShow = if (isPremium) sortedRecords else sortedRecords.take(maxToShow - totalShown)
            for (r in toShow) {
                if (totalShown >= maxToShow && !isPremium) break
                paintReg.color = Color.BLACK
                val stemShort = if (r.stem.length > MAX_STEM_LENGTH) r.stem.take(MAX_STEM_LENGTH) + "…" else r.stem
                canvas.drawText("Soru: $stemShort", MARGIN + 8f, y, paintReg)
                y += 12f
                paintGray.color = TEXT_GRAY
                canvas.drawText("Öğrenci cevabı: ${r.userAnswer}  |  Doğru cevap: ${r.correctAnswer}", MARGIN + 8f, y, paintGray)
                y += 10f
                if (r.category != null) {
                    paintGray.textSize = 8f
                    canvas.drawText("Kategori: ${r.category}", MARGIN + 8f, y, paintGray)
                    paintGray.textSize = 9f
                    y += 12f
                }
                y += 6f
                totalShown++
            }

            if (!isPremium && sortedRecords.size > toShow.size) {
                y += 8f
                canvas.drawText("Premium ile tamamını görüntüleyin", MARGIN + 8f, y, paintEmerald)
                y += 20f
                break
            }
            y += 12f
        }

        document.finishPage(page)
    }

    private fun drawSectionTitle(
        canvas: Canvas,
        title: String,
        y: Float,
        titlePaint: Paint,
        fillPaint: Paint
    ) {
        canvas.drawRect(MARGIN, y - 24f, PAGE_WIDTH - MARGIN, y, fillPaint)
        titlePaint.color = Color.WHITE
        titlePaint.textSize = 14f
        canvas.drawText(title, MARGIN + 8f, y - 6f, titlePaint)
        titlePaint.color = Color.BLACK
    }

    private object ProfileStore {
        fun currentProfileName(ctx: Context): String {
            val store = com.brainbuddy.app.core.ProfileStore(ctx)
            val id = com.brainbuddy.app.core.ActiveProfileManager.getActiveProfileId(ctx)
            return store.getProfile(id)?.name ?: "Öğrenci"
        }
    }
}
