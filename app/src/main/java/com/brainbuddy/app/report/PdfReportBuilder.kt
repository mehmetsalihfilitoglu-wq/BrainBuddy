package com.brainbuddy.app.report

import android.content.Context
import android.graphics.Bitmap
import com.brainbuddy.app.core.StatsRepository
import com.brainbuddy.app.core.TopicCounts
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a professional multi-page PDF performance report.
 * Uses flow-based document structure similar to reportlab.platypus concepts.
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
        private const val PAGE_WIDTH = 595f
        private const val PAGE_HEIGHT = 842f
        private const val MARGIN = 50f
        private const val EMERALD_R = 18 / 255f
        private const val EMERALD_G = 184 / 255f
        private const val EMERALD_B = 166 / 255f
        private const val TEXT_GRAY = 0.42f
        private const val MAX_STEM_LENGTH = 120
    }

    @Throws(IOException::class)
    fun build(outFile: File) {
        val doc = PDDocument()
        try {
            addPage1(doc)
            addPage2(doc)
            addPage3(doc)
            addPage4(doc)
            doc.save(outFile)
        } finally {
            doc.close()
        }
    }

    private fun addPage1(doc: PDDocument) {
        val page = PDPage(PDRectangle(PAGE_WIDTH, PAGE_HEIGHT))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            val font = PDType1Font.HELVETICA_BOLD
            val fontSmall = PDType1Font.HELVETICA
            val fontSize = 11f

            // Emerald header band (path operations first)
            cs.setNonStrokingColor(EMERALD_R, EMERALD_G, EMERALD_B)
            cs.addRect(0f, PAGE_HEIGHT - 100f, PAGE_WIDTH, 100f)
            cs.fill()
            cs.closePath()

            // Text content
            cs.beginText()
            cs.setNonStrokingColor(1f, 1f, 1f)
            cs.setFont(font, 24f)
            cs.newLineAtOffset(MARGIN, PAGE_HEIGHT - 55f)
            cs.showText("BrainBuddy")
            cs.setFont(fontSmall, 12f)
            cs.newLineAtOffset(0f, -20f)
            cs.showText("Performans Raporu")

            // Cover content (white background area)
            cs.setNonStrokingColor(0f, 0f, 0f)
            cs.setFont(font, 14f)
            val studentName = ProfileStore.currentProfileName(context)
            cs.newLineAtOffset(-120f, -85f)
            cs.showText("Öğrenci: $studentName")

            val rangeLabel = when (model.range) {
                StatsRepository.ReportRange.TODAY -> "Bugün"
                StatsRepository.ReportRange.SEVEN -> "7 Gün"
                StatsRepository.ReportRange.THIRTY -> "30 Gün"
            }
            cs.setFont(fontSmall, fontSize)
            cs.newLineAtOffset(0f, -22f)
            cs.showText("Rapor Dönemi: $rangeLabel")

            val ws = model.weeklySuccess
            cs.newLineAtOffset(0f, -18f)
            cs.showText("Toplam Test: ${ws.testCount}")
            cs.newLineAtOffset(0f, -18f)
            cs.showText("Doğru: ${ws.correct}  |  Yanlış: ${ws.wrong}  |  Boş: ${ws.blank}")
            cs.newLineAtOffset(0f, -18f)
            val successStr = if (ws.noGradedAnswers) "—" else "%.0f%%".format(ws.accuracyPercent)
            cs.showText("Başarı Oranı: $successStr")

            val adv = model.advancedStats
            val trendStr = when (adv.trendDirection) {
                StatsRepository.TrendDirection.IMPROVING -> "↑ +%.0f%%".format(adv.trendDelta)
                StatsRepository.TrendDirection.DECLINING -> "↓ %.0f%%".format(adv.trendDelta)
                else -> "→"
            }
            cs.newLineAtOffset(0f, -18f)
            cs.showText("Trend: $trendStr")

            val dateStr = SimpleDateFormat("d MMMM yyyy", Locale("tr")).format(Date())
            cs.newLineAtOffset(0f, -36f)
            cs.setNonStrokingColor(TEXT_GRAY, TEXT_GRAY, TEXT_GRAY)
            cs.showText("Oluşturulma: $dateStr")

            cs.endText()
        }
    }

    private fun addPage2(doc: PDDocument) {
        val page = PDPage(PDRectangle(PAGE_WIDTH, PAGE_HEIGHT))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            var y = PAGE_HEIGHT - MARGIN
            val font = PDType1Font.HELVETICA_BOLD
            val fontSmall = PDType1Font.HELVETICA

            drawSectionTitle(cs, "Performans Analizi", y, font)
            y -= 28f

            val ws = model.weeklySuccess
            // General stats table
            cs.setFont(fontSmall, 10f)
            cs.setNonStrokingColor(0f, 0f, 0f)
            val rows = listOf(
                "Toplam Test" to ws.testCount.toString(),
                "Doğru" to ws.correct.toString(),
                "Yanlış" to ws.wrong.toString(),
                "Boş" to ws.blank.toString(),
                "Başarı %" to (if (ws.noGradedAnswers) "—" else "%.0f".format(ws.accuracyPercent))
            )
            rows.forEach { (label, value) ->
                cs.beginText()
                cs.newLineAtOffset(MARGIN, y)
                cs.showText("$label:")
                cs.newLineAtOffset(120f, 0f)
                cs.showText(value)
                cs.endText()
                y -= 16f
            }
            y -= 16f

            // Trend analysis
            val adv = model.advancedStats
            val tc = model.trendChart
            if (!tc.isEmpty && tc.points.size >= 6) {
                val last3 = tc.points.takeLast(3).map { it.percent }
                val prev3 = tc.points.dropLast(3).takeLast(3).map { it.percent }
                val last3Avg = last3.average().toFloat()
                val prev3Avg = prev3.average().toFloat()
                val change = last3Avg - prev3Avg
                drawText(cs, "Son 3 test ort: %.0f%%".format(last3Avg), MARGIN, y, fontSmall, 10f)
                y -= 14f
                drawText(cs, "Önceki 3 test ort: %.0f%%".format(prev3Avg), MARGIN, y, fontSmall, 10f)
                y -= 14f
                drawText(cs, "Değişim: %+.0f%%".format(change), MARGIN, y, fontSmall, 10f)
                y -= 24f
            }

            // Line chart
            if (lineChartBitmap != null) {
                drawText(cs, "Son 10 Test Başarı Oranı", MARGIN, y, fontSmall, 10f)
                y -= 8f
                val imgW = 320f
                val imgH = 140f
                val pdImg = JPEGFactory.createFromImage(doc, lineChartBitmap, 0.9f)
                cs.drawImage(pdImg, MARGIN, y - imgH, imgW, imgH)
                y -= imgH + 24f
            }

            // Bar chart
            if (barChartBitmap != null) {
                drawText(cs, "Konu Performansı (Doğru/Toplam)", MARGIN, y, fontSmall, 10f)
                y -= 8f
                val imgW = 320f
                val imgH = kotlin.math.min(180f, barChartBitmap.height * (imgW / barChartBitmap.width))
                val pdImg = JPEGFactory.createFromImage(doc, barChartBitmap, 0.9f)
                cs.drawImage(pdImg, MARGIN, y - imgH, imgW, imgH)
            }
        }
    }

    private fun addPage3(doc: PDDocument) {
        val page = PDPage(PDRectangle(PAGE_WIDTH, PAGE_HEIGHT))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            var y = PAGE_HEIGHT - MARGIN
            val font = PDType1Font.HELVETICA_BOLD
            val fontSmall = PDType1Font.HELVETICA

            drawSectionTitle(cs, "Konu Analizi", y, font)
            y -= 28f

            val topics = model.topics.topicCounts
            if (topics.isEmpty()) {
                drawText(cs, "Bu dönemde konu verisi yok.", MARGIN, y, fontSmall, 10f)
                return@use
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

            subjectStats.forEach { (name, tc, successPct) ->
                val graded = tc.correct + tc.wrong
                var interpret = ""
                when {
                    successPct < 60f -> interpret = "Geliştirme gerekli."
                    successPct > 80f -> interpret = "Güçlü alan."
                    else -> {
                        val adv = model.advancedStats
                        val weakNames = adv.weakSubjects.map { it.name }
                        if (name in weakNames) interpret = "Son testlerde düşüş var."
                        else interpret = ""
                    }
                }
                if (interpret.isEmpty() && successPct in 60f..80f) {
                    interpret = "Orta seviye."
                }

                val badges = mutableListOf<String>()
                if (name == strongest) badges.add("★ En güçlü")
                if (name == weakest) badges.add("▲ En zayıf")
                if (name == mostWrong && tc.wrong > 0) badges.add("⚠ En çok yanlış")

                cs.setFont(fontSmall, 10f)
                cs.setNonStrokingColor(0f, 0f, 0f)
                drawText(cs, "$name — %.0f%% başarı | ${tc.correct}/${tc.total} | Yanlış: ${tc.wrong}".format(successPct), MARGIN, y, fontSmall, 10f)
                y -= 14f
                if (interpret.isNotEmpty()) {
                    cs.setNonStrokingColor(TEXT_GRAY, TEXT_GRAY, TEXT_GRAY)
                    drawText(cs, "  $interpret ${badges.joinToString(" ")}", MARGIN, y, fontSmall, 9f)
                    y -= 16f
                } else if (badges.isNotEmpty()) {
                    drawText(cs, "  ${badges.joinToString(" ")}", MARGIN, y, fontSmall, 9f)
                    y -= 16f
                } else {
                    y -= 6f
                }
            }

            // Optional recommendation
            val adv = model.advancedStats
            adv.smartRecommendation?.let { rec ->
                y -= 12f
                cs.setNonStrokingColor(EMERALD_R, EMERALD_G, EMERALD_B)
                drawText(cs, "Öneri: ${rec.message}", MARGIN, y, fontSmall, 10f)
            }
        }
    }

    private fun addPage4(doc: PDDocument) {
        val page = PDPage(PDRectangle(PAGE_WIDTH, PAGE_HEIGHT))
        doc.addPage(page)
        PDPageContentStream(doc, page).use { cs ->
            var y = PAGE_HEIGHT - MARGIN
            val font = PDType1Font.HELVETICA_BOLD
            val fontSmall = PDType1Font.HELVETICA

            drawSectionTitle(cs, "Yanlış Cevaplar (Kategorize)", y, font)
            y -= 28f

            if (wrongAnswerRecords.isEmpty()) {
                drawText(cs, "Bu dönemde yanlış cevap kaydı yok.", MARGIN, y, fontSmall, 10f)
                return@use
            }

            val bySubject = wrongAnswerRecords.groupBy { it.subject }
            val sortedSubjects = bySubject.entries.sortedByDescending { it.value.size }

            val maxToShow = if (isPremium) Int.MAX_VALUE else 3
            var totalShown = 0

            for ((subject, records) in sortedSubjects) {
                val sortedRecords = records.sortedByDescending { it.dateMs }
                cs.setFont(font, 11f)
                cs.setNonStrokingColor(0f, 0f, 0f)
                drawText(cs, "$subject (${records.size} yanlış)", MARGIN, y, font, 11f)
                y -= 20f

                val toShow = if (isPremium) sortedRecords else sortedRecords.take(maxToShow - totalShown)
                for (r in toShow) {
                    if (totalShown >= maxToShow && !isPremium) break
                    cs.setFont(fontSmall, 9f)
                    val stemShort = if (r.stem.length > MAX_STEM_LENGTH) r.stem.take(MAX_STEM_LENGTH) + "…" else r.stem
                    drawText(cs, "Soru: $stemShort", MARGIN + 8f, y, fontSmall, 9f)
                    y -= 12f
                    cs.setNonStrokingColor(TEXT_GRAY, TEXT_GRAY, TEXT_GRAY)
                    drawText(cs, "Öğrenci cevabı: ${r.userAnswer}  |  Doğru cevap: ${r.correctAnswer}", MARGIN + 8f, y, fontSmall, 9f)
                    y -= 10f
                    if (r.category != null) {
                        drawText(cs, "Kategori: ${r.category}", MARGIN + 8f, y, fontSmall, 8f)
                        y -= 12f
                    }
                    y -= 6f
                    totalShown++
                }

                if (!isPremium && sortedRecords.size > toShow.size) {
                    y -= 8f
                    cs.setNonStrokingColor(EMERALD_R, EMERALD_G, EMERALD_B)
                    drawText(cs, "Premium ile tamamını görüntüleyin", MARGIN + 8f, y, fontSmall, 9f)
                    y -= 20f
                    break
                }
                y -= 12f
            }
        }
    }

    private fun drawSectionTitle(cs: PDPageContentStream, title: String, y: Float, font: PDType1Font) {
        cs.setNonStrokingColor(EMERALD_R, EMERALD_G, EMERALD_B)
        cs.addRect(MARGIN, y - 24f, PAGE_WIDTH - 2 * MARGIN, 24f)
        cs.fill()
        cs.closePath()
        cs.setNonStrokingColor(1f, 1f, 1f)
        cs.setFont(font, 14f)
        cs.beginText()
        cs.newLineAtOffset(MARGIN + 8f, y - 18f)
        cs.showText(title)
        cs.endText()
    }

    private fun drawText(cs: PDPageContentStream, text: String, x: Float, y: Float, font: PDType1Font, size: Float) {
        cs.setFont(font, size)
        cs.beginText()
        cs.newLineAtOffset(x, y)
        val safe = text.replace(Char(0x00), ' ')
        cs.showText(safe.take(200))
        cs.endText()
    }

    private object ProfileStore {
        fun currentProfileName(ctx: Context): String {
            val store = com.brainbuddy.app.core.ProfileStore(ctx)
            val id = com.brainbuddy.app.core.ActiveProfileManager.getActiveProfileId(ctx)
            return store.getProfile(id)?.name ?: "Öğrenci"
        }
    }
}
