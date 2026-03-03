package com.brainbuddy.app.report

import android.content.Context
import android.graphics.Bitmap
import com.brainbuddy.app.core.ActiveProfileManager
import com.brainbuddy.app.core.PremiumStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.ProfileStore
import com.brainbuddy.app.core.StatsRepository
import com.brainbuddy.app.quiz.QuizResultActivity
import com.brainbuddy.app.quiz.QuestionRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Orchestrates PDF report generation: gathers data, builds charts, invokes PdfReportBuilder,
 * and provides the file for sharing via FileProvider.
 */
object PdfReportGenerator {

    fun generateAndGetFile(
        context: Context,
        model: StatsRepository.ReportsUiModel
    ): File? {
        return try {
            val sinceMs = getSinceMsForRange(context, model.range.days)
            val completePerfs = model.run {
                // Use data already filtered by StatsRepository
                val analytics = com.brainbuddy.app.core.AnalyticsStore(context)
                analytics.getTestPerformances().filter { it.tsMs >= sinceMs && it.correctCount + it.wrongCount > 0 }
            }

            val wrongRecords = buildWrongAnswerRecords(context)
            val lineChartBitmap = buildLineChartBitmap(context, model)
            val barChartBitmap = buildBarChartBitmap(context, model)
            val isPremium = PremiumStore(context).isPremium()

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "BrainBuddy_Report_$timestamp.pdf"

            val reportsDir = File(context.cacheDir, "brainbuddy_reports").apply { mkdirs() }
            val outFile = File(reportsDir, fileName)

            val builder = PdfReportBuilder(
                context = context,
                model = model,
                wrongAnswerRecords = wrongRecords,
                lineChartBitmap = lineChartBitmap,
                barChartBitmap = barChartBitmap,
                isPremium = isPremium
            )
            builder.build(outFile)

            lineChartBitmap?.recycle()
            barChartBitmap?.recycle()

            outFile
        } catch (e: Exception) {
            android.util.Log.e("PDF_REPORT", "PDF generation failed", e)
            null
        }
    }

    private fun getSinceMsForRange(context: Context, days: Int): Long {
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = now
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val todayStart = cal.timeInMillis
        return if (days == 1) todayStart
        else todayStart - TimeUnit.DAYS.toMillis((days - 1).toLong())
    }

    private fun buildWrongAnswerRecords(context: Context): List<PdfReportBuilder.WrongAnswerRecord> {
        val protectionPrefs = ProtectionPrefs(context)
        val wrongIds = protectionPrefs.lastFailedWrongIds()
        val sessionJson = protectionPrefs.lastFailedSessionJson()
        val questionsJson = protectionPrefs.lastFailedQuestionsJson()

        if (wrongIds.isEmpty() || sessionJson.isNullOrBlank()) return emptyList()

        val session = QuizResultActivity.decodeSession(sessionJson)
        val sessionAnswers = session?.answers ?: return emptyList()

        val questions = if (!questionsJson.isNullOrBlank()) {
            QuizResultActivity.decodeQuestions(questionsJson)
        } else {
            val repo = QuestionRepository(context)
            val allMap = repo.loadAllQuestions().associateBy { it.id }
            wrongIds.mapNotNull { allMap[it] }
        }

        val questionMap = questions.associateBy { it.id }
        val dateMs = session?.completedAt ?: session?.startedAt ?: System.currentTimeMillis()

        return wrongIds.mapNotNull { qId ->
            val q = questionMap[qId] ?: return@mapNotNull null
            val userIdx = sessionAnswers[qId] ?: -1
            val userAnswer = if (userIdx in 0..3) q.choices.getOrNull(userIdx) ?: "—" else "—"
            val correctAnswer = q.choices.getOrNull(q.correctIndex) ?: "?"
            val category = q.topic ?: q.subject.tr
            PdfReportBuilder.WrongAnswerRecord(
                subject = q.subject.tr,
                stem = q.stem,
                userAnswer = userAnswer,
                correctAnswer = correctAnswer,
                category = category,
                dateMs = dateMs
            )
        }
    }

    private fun buildLineChartBitmap(context: Context, model: StatsRepository.ReportsUiModel): Bitmap? {
        val tc = model.trendChart
        if (tc.isEmpty || tc.points.size < 2) return null
        val points = tc.points.map { p ->
            Triple(
                p.index.toString(),
                p.percent,
                "Test ${p.index}: %.0f%%".format(p.percent)
            )
        }
        return ChartImageExporter.exportLineChart(context, points)
    }

    private fun buildBarChartBitmap(context: Context, model: StatsRepository.ReportsUiModel): Bitmap? {
        val topics = model.topics.topicCounts
        if (topics.isEmpty()) return null
        val bars = topics.entries
            .sortedByDescending { it.value.correct }
            .take(10)
            .map { (name, tc) -> Triple(name, tc.correct, tc.total) }
        return ChartImageExporter.exportBarChart(context, bars)
    }
}
