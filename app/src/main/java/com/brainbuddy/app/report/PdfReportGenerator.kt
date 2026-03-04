package com.brainbuddy.app.report

import android.content.Context
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Orchestrates PDF report generation using ChartRenderer (Canvas-only, no View/Looper).
 * All rendering runs on IO dispatcher - no main thread required for charts.
 */
object PdfReportGenerator {

    const val PDF_ERROR_FILENAME = "pdf_error.txt"
    private const val REPORTS_DIR = "reports"

    suspend fun generateAndGetFile(
        context: Context,
        model: StatsRepository.ReportsUiModel
    ): File? {
        val cacheDir = context.cacheDir
        val reportsDir = File(cacheDir, REPORTS_DIR).apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(reportsDir, "brainbuddy_report_$timestamp.pdf")

        return try {
            val range = ReportStatsCalculator.Range.fromDays(model.range.days)
            val result = withContext(Dispatchers.Default) {
                ReportStatsCalculator.computeForRange(context, range)
            }

            val lineChartBitmap = if (result.lastTests.size >= 2) {
                val points = result.lastTests.mapIndexed { i, p -> (i + 1) to p.percent }
                withContext(Dispatchers.Default) {
                    ChartRenderer.renderLineChart(points)
                }
            } else null

            val barChartBitmap = if (result.perSubject.isNotEmpty()) {
                val sorted = result.perSubject.entries
                    .map { (name, tc) -> name to tc }
                    .sortedByDescending { it.second.correct }
                    .take(10)
                val bars = sorted.map { (name, tc) -> Triple(name, tc.correct, tc.total) }
                val strongest = sorted.maxByOrNull { (_, tc) ->
                    val g = tc.correct + tc.wrong
                    if (g > 0) tc.correct.toFloat() / g else 0f
                }?.first
                val weakest = sorted.minByOrNull { (_, tc) ->
                    val g = tc.correct + tc.wrong
                    if (g > 0) tc.correct.toFloat() / g else 1f
                }?.first
                val mostWrong = sorted.maxByOrNull { (_, tc) -> tc.wrong }?.first
                val labels = buildMap {
                    strongest?.let { put(it, "★ En güçlü") }
                    weakest?.let { if (it != strongest) put(it, "▲ En zayıf") }
                    mostWrong?.let { if (it != strongest && it != weakest) put(it, "⚠ En çok yanlış") }
                }
                withContext(Dispatchers.Default) {
                    ChartRenderer.renderBarChart(bars, labels)
                }
            } else null

            val wrongRecords = result.wrongAnswers.map { wr ->
                PdfReportBuilder.WrongAnswerRecord(
                    subject = wr.subject,
                    stem = wr.stem,
                    userAnswer = wr.userAnswer,
                    correctAnswer = wr.correctAnswer,
                    category = wr.category,
                    dateMs = wr.dateMs
                )
            }

            val isPremium = PremiumStore(context).isPremium()

            val builder = PdfReportBuilder(
                context = context,
                result = result,
                wrongAnswerRecords = wrongRecords,
                lineChartBitmap = lineChartBitmap,
                barChartBitmap = barChartBitmap,
                isPremium = isPremium
            )

            withContext(Dispatchers.IO) {
                builder.build(outFile)
            }

            lineChartBitmap?.recycle()
            barChartBitmap?.recycle()

            if (!outFile.exists() || outFile.length() < 500) {
                withContext(Dispatchers.IO) {
                    writePdfErrorFile(cacheDir, Exception("PDF file too small or missing (size=${outFile.length()})"))
                }
                return null
            }
            outFile
        } catch (e: Exception) {
            android.util.Log.e("PDF_REPORT", "PDF generation failed", e)
            withContext(Dispatchers.IO) { writePdfErrorFile(cacheDir, e) }
            null
        }
    }

    private fun writePdfErrorFile(cacheDir: File, e: Throwable) {
        try {
            val errorFile = File(cacheDir, PDF_ERROR_FILENAME)
            errorFile.writeText("${e.message}\n\n${e.stackTraceToString()}")
        } catch (_: Exception) { }
    }
}
