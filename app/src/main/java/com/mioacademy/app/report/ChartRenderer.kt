package com.mioacademy.app.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Pure Canvas-based chart renderer for PDF embedding.
 * NO View, NO GestureDetector, NO Looper - safe to run on any thread (IO/Default).
 * BrainBuddy color palette: emerald primary, soft gray text, white background.
 */
object ChartRenderer {

    // BrainBuddy color system (strict)
    private const val EMERALD = 0xFF12B5A6.toInt()      // #12B5A6
    private const val EMERALD_DARK = 0xFF0FAE9A.toInt() // #0FAE9A
    private const val EMERALD_LIGHT = 0xFFDFF7F4.toInt() // #DFF7F4
    private const val SURFACE = 0xFFF7F9FA.toInt()      // #F7F9FA
    private const val DIVIDER = 0xFFE6ECEF.toInt()      // #E6ECEF
    private val TEXT_PRIMARY = Color.parseColor("#1F2A30")
    private val TEXT_SECONDARY = Color.parseColor("#5F6B73")

    /**
     * Renders a line chart (success % over tests) to a bitmap.
     * Y axis 0–100, emerald line, round data points, soft grid, smooth line.
     * Can run on background thread.
     */
    fun renderLineChart(
        points: List<Pair<Int, Float>>,
        widthPx: Int = 640,
        heightPx: Int = 360
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        if (points.size < 2) return bitmap

        val density = 2f
        val padLeft = 44f * density
        val padRight = 24f * density
        val padTop = 28f * density
        val padBottom = 40f * density
        val chartLeft = padLeft
        val chartTop = padTop
        val chartWidth = widthPx - padLeft - padRight
        val chartHeight = heightPx - padTop - padBottom

        // Soft grid lines (horizontal)
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 230, 236, 239)
            strokeWidth = 1f
        }
        for (tick in listOf(0, 25, 50, 75, 100)) {
            val y = chartTop + chartHeight * (1f - tick / 100f)
            canvas.drawLine(chartLeft, y, chartLeft + chartWidth, y, gridPaint)
        }

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 10f * density
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            style = Paint.Style.STROKE
            strokeWidth = 3.5f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            style = Paint.Style.FILL
        }
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DIVIDER
            strokeWidth = 1f
        }

        val yTicks = listOf(0, 25, 50, 75, 100)
        for (tick in yTicks) {
            val y = chartTop + chartHeight * (1f - tick / 100f)
            canvas.drawLine(chartLeft - 6f, y, chartLeft, y, tickPaint)
            canvas.drawText("$tick", 4f, y + axisPaint.textSize / 3, axisPaint)
        }

        val n = points.size
        val stepX = chartWidth / (n - 1).coerceAtLeast(1)
        val coords = points.mapIndexed { i, (_, pct) ->
            val x = chartLeft + i * stepX
            val y = chartTop + chartHeight * (1f - (pct.coerceIn(0f, 100f) / 100f))
            Pair(x, y)
        }

        // Smooth line (Catmull-Rom spline for softer curve)
        val path = buildSmoothPath(coords)
        canvas.drawPath(path, linePaint)

        val dotRadius = 5f * density
        coords.forEach { (x, y) ->
            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }

        points.forEachIndexed { i, (label, _) ->
            val x = chartLeft + i * stepX
            val tw = axisPaint.measureText(label.toString())
            canvas.drawText(label.toString(), x - tw / 2, heightPx - 6f, axisPaint)
        }

        return bitmap
    }

    private fun buildSmoothPath(coords: List<Pair<Float, Float>>): Path {
        if (coords.size < 2) return Path()
        val path = Path()
        path.moveTo(coords[0].first, coords[0].second)
        for (i in 1 until coords.size) {
            val p0 = coords[(i - 2).coerceAtLeast(0)]
            val p1 = coords[i - 1]
            val p2 = coords[i]
            val p3 = coords[(i + 1).coerceAtMost(coords.size - 1)]
            val cp1x = p1.first + (p2.first - p0.first) / 6f
            val cp1y = p1.second + (p2.second - p0.second) / 6f
            val cp2x = p2.first - (p3.first - p1.first) / 6f
            val cp2y = p2.second - (p3.second - p1.second) / 6f
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.first, p2.second)
        }
        return path
    }

    /**
     * Renders a bar chart (subject correct/total) to a bitmap.
     * Bars are emerald. Optional labels: "★ En güçlü", "▲ En zayıf", "⚠ En çok yanlış".
     * @param subjectLabels map subject name -> label text for strong/weak badges
     */
    fun renderBarChart(
        bars: List<Triple<String, Int, Int>>,
        subjectLabels: Map<String, String> = emptyMap(),
        widthPx: Int = 640,
        heightPx: Int = 480
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        if (bars.isEmpty()) return bitmap

        val density = 2f
        val barHeight = 26f * density
        val gap = 14f * density
        val padStart = 16f * density
        val padEnd = 16f * density
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_PRIMARY
            textSize = 12f * density
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_SECONDARY
            textSize = 11f * density
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            textSize = 9f * density
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 230, 236, 239)
            style = Paint.Style.FILL
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            style = Paint.Style.FILL
        }

        val maxTotal = bars.maxOfOrNull { it.third }?.coerceAtLeast(1) ?: 1
        val sampleRatio = "999/999"
        val ratioWidth = chipPaint.measureText(sampleRatio) + 32f
        val barRegionEnd = widthPx - padEnd - ratioWidth
        val labelMaxWidth = (widthPx - ratioWidth - 100f).coerceAtLeast(100f)
        val barRegionStart = padStart + labelMaxWidth + 16f
        val chartWidth = (barRegionEnd - barRegionStart).coerceAtLeast(60f)
        val rect = RectF()

        bars.forEachIndexed { i, (label, correct, total) ->
            val y = padStart + i * (barHeight + gap)
            val labelY = y + barHeight - 6
            val ellipsized = if (labelPaint.measureText(label) > labelMaxWidth) {
                label.take(14) + "…"
            } else label
            canvas.drawText(ellipsized, padStart, labelY, labelPaint)
            subjectLabels[label]?.let { badge ->
                val badgeStart = padStart + labelPaint.measureText(ellipsized) + 6
                badgePaint.color = if (badge.contains("güçlü")) EMERALD else TEXT_SECONDARY
                canvas.drawText(badge, badgeStart, labelY, badgePaint)
            }

            val barFullW = chartWidth * (total.toFloat() / maxTotal)
            val barFillW = if (total > 0) barFullW * (correct.toFloat() / total) else 0f
            rect.set(barRegionStart, y, barRegionStart + barFullW, y + barHeight - 6)
            canvas.drawRoundRect(rect, 8f, 8f, bgPaint)
            if (barFillW > 0) {
                rect.set(barRegionStart, y, barRegionStart + barFillW, y + barHeight - 6)
                canvas.drawRoundRect(rect, 8f, 8f, fillPaint)
            }

            val chipText = "$correct/$total"
            val chipX = widthPx - padEnd - chipPaint.measureText(chipText)
            canvas.drawText(chipText, chipX, labelY, chipPaint)
        }

        return bitmap
    }
}
