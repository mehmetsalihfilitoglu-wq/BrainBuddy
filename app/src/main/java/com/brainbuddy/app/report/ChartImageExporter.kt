package com.brainbuddy.app.report

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import com.brainbuddy.app.ui.BarChartView
import com.brainbuddy.app.ui.LineChartView

/**
 * Exports charts to bitmap images for embedding in PDF reports.
 */
object ChartImageExporter {

    private val density by lazy { 2f } // Scale for PDF clarity

    /**
     * Renders a line chart (last N test success %) to a bitmap.
     * @param points List of (xLabel, percent, tooltipText) for each test
     * @param widthPx Target width in pixels
     * @param heightPx Target height in pixels
     */
    fun exportLineChart(
        context: Context,
        points: List<Triple<String, Float, String>>,
        widthPx: Int = (320 * density).toInt(),
        heightPx: Int = (180 * density).toInt()
    ): Bitmap? {
        if (points.size < 2) return null
        val lineChart = LineChartView(context)
        val pointData = points.mapIndexed { i, (label, pct, tooltip) ->
            LineChartView.PointData(label, pct.coerceIn(0f, 100f), tooltip)
        }
        lineChart.data = pointData
        return renderViewToBitmap(lineChart, widthPx, heightPx)
    }

    /**
     * Renders a bar chart (subject correct/total) to a bitmap.
     */
    fun exportBarChart(
        context: Context,
        bars: List<Triple<String, Int, Int>>,
        widthPx: Int = (320 * density).toInt(),
        heightPx: Int = (200 * density).toInt()
    ): Bitmap? {
        if (bars.isEmpty()) return null
        val barChart = BarChartView(context)
        barChart.data = bars.map { (label, correct, total) ->
            BarChartView.BarData(label, correct, total)
        }
        val barHeight = 24f * context.resources.displayMetrics.density
        val gap = 12f * context.resources.displayMetrics.density
        val neededHeight = (bars.size * (barHeight + gap) + 48).toInt().coerceAtLeast(heightPx)
        return renderViewToBitmap(barChart, widthPx, neededHeight)
    }

    private fun renderViewToBitmap(view: android.view.View, widthPx: Int, heightPx: Int): Bitmap? {
        return try {
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
