package com.brainbuddy.app.report

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/**
 * Pure Canvas-based chart renderer for PDF embedding.
 * NO View, NO GestureDetector, NO Looper - safe to run on any thread (IO/Default).
 * Uses Android system fonts and standard drawing primitives.
 */
object ChartRenderer {

    private const val EMERALD = 0xFF12B8A6.toInt()
    private val TEXT_GRAY = Color.rgb(107, 107, 107)

    /**
     * Renders a line chart (success % over tests) to a bitmap.
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
        val padLeft = 40f * density
        val padRight = 24f * density
        val padTop = 24f * density
        val padBottom = 36f * density
        val chartLeft = padLeft
        val chartTop = padTop
        val chartWidth = widthPx - padLeft - padRight
        val chartHeight = heightPx - padTop - padBottom

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_GRAY
            textSize = 11f * density
        }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            style = Paint.Style.STROKE
            strokeWidth = 4f * density
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            style = Paint.Style.FILL
        }
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_GRAY
            strokeWidth = 1f * density
        }

        val yTicks = listOf(0, 25, 50, 75, 100)
        for (tick in yTicks) {
            val y = chartTop + chartHeight * (1f - tick / 100f)
            canvas.drawLine(chartLeft - 4f, y, chartLeft, y, tickPaint)
            canvas.drawText("$tick", 8f, y + axisPaint.textSize / 3, axisPaint)
        }

        val n = points.size
        val stepX = chartWidth / (n - 1).coerceAtLeast(1)
        val path = Path()

        val coords = points.mapIndexed { i, (_, pct) ->
            val x = chartLeft + i * stepX
            val y = chartTop + chartHeight * (1f - (pct.coerceIn(0f, 100f) / 100f))
            Pair(x, y)
        }

        path.moveTo(coords[0].first, coords[0].second)
        for (i in 1 until coords.size) {
            path.lineTo(coords[i].first, coords[i].second)
        }
        canvas.drawPath(path, linePaint)

        val dotRadius = 6f * density
        coords.forEach { (x, y) ->
            canvas.drawCircle(x, y, dotRadius, dotPaint)
        }

        points.forEachIndexed { i, (label, _) ->
            val x = chartLeft + i * stepX
            val tw = axisPaint.measureText(label.toString())
            canvas.drawText(label.toString(), x - tw / 2, heightPx - 8f, axisPaint)
        }

        return bitmap
    }

    /**
     * Renders a bar chart (subject correct/total) to a bitmap.
     * Can run on background thread.
     */
    fun renderBarChart(
        bars: List<Triple<String, Int, Int>>,
        widthPx: Int = 640,
        heightPx: Int = 480
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        if (bars.isEmpty()) return bitmap

        val density = 2f
        val barHeight = 24f * density
        val gap = 12f * density
        val padStart = 12f * density
        val padEnd = 12f * density
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_GRAY
            textSize = 13f * density
        }
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TEXT_GRAY
            textSize = 12f * density
        }
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 107, 107, 107)
            style = Paint.Style.FILL
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EMERALD
            style = Paint.Style.FILL
        }

        val maxTotal = bars.maxOfOrNull { it.third }?.coerceAtLeast(1) ?: 1
        val sampleRatio = "999/999"
        val ratioWidth = chipPaint.measureText(sampleRatio) + 24f
        val barRegionEnd = widthPx - padEnd - ratioWidth
        val labelMaxWidth = (widthPx - ratioWidth - 80f).coerceAtLeast(80f)
        val barRegionStart = padStart + labelMaxWidth + 12f
        val chartWidth = (barRegionEnd - barRegionStart).coerceAtLeast(40f)
        val rect = RectF()

        bars.forEachIndexed { i, (label, correct, total) ->
            val y = padStart + i * (barHeight + gap)
            val labelY = y + barHeight - 4
            val ellipsized = if (labelPaint.measureText(label) > labelMaxWidth) {
                label.take(12) + "…"
            } else label
            canvas.drawText(ellipsized, padStart, labelY, labelPaint)

            val barFullW = chartWidth * (total.toFloat() / maxTotal)
            val barFillW = if (total > 0) barFullW * (correct.toFloat() / total) else 0f
            rect.set(barRegionStart, y, barRegionStart + barFullW, y + barHeight - 4)
            canvas.drawRoundRect(rect, 6f, 6f, bgPaint)
            if (barFillW > 0) {
                rect.set(barRegionStart, y, barRegionStart + barFillW, y + barHeight - 4)
                canvas.drawRoundRect(rect, 6f, 6f, fillPaint)
            }

            val chipText = "$correct/$total"
            val chipX = widthPx - padEnd - chipPaint.measureText(chipText)
            canvas.drawText(chipText, chipX, labelY, chipPaint)
        }

        return bitmap
    }
}
