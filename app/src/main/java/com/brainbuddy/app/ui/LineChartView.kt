package com.brainbuddy.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.TextPaint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.brainbuddy.app.R
import com.google.android.material.color.MaterialColors

/**
 * Line chart for "Son 10 Test Başarı Oranı" (0–100%).
 * Y-axis: visible ticks 0, 25, 50, 75, 100.
 * X-axis: test order or short date labels.
 * Each point = one test session. Tap shows tooltip.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class PointData(
        val xLabel: String,
        val percent: Float,
        val tooltipText: String
    )

    private val primaryColor: Int
        get() = MaterialColors.getColor(
            context,
            com.google.android.material.R.attr.colorPrimary,
            context.getColor(R.color.bb_primary)
        )

    private val textColor = MaterialColors.getColor(
        context,
        com.google.android.material.R.attr.colorOnSurfaceVariant,
        context.getColor(R.color.bb_text_muted)
    )

    private val density = resources.displayMetrics.density
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f * density
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val axisPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
        color = textColor
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        strokeWidth = 1f * density
    }
    private val path = Path()

    /** Full point data for labels and tooltip. If empty, falls back to values. */
    var data: List<PointData> = emptyList()
        set(value) {
            field = value
            values = value.map { it.percent.coerceIn(0f, 100f) }
        }

    /** Legacy: raw values only (no labels/tooltip). */
    var values: List<Float> = emptyList()
        set(value) {
            field = value.map { it.coerceIn(0f, 100f) }
            invalidate()
        }

    /** Called when user taps a data point. Passes 0-based index. */
    var onPointSelected: ((Int) -> Unit)? = null

    /** Called when user taps empty chart area (no point hit). */
    var onEmptyAreaTapped: (() -> Unit)? = null

    /** @deprecated Use onPointSelected for index-based handling. Kept for compatibility. */
    var onPointTapped: ((String) -> Unit)? = null

    private val dotRadiusPx = 6f * density
    private val yTicks = listOf(0, 25, 50, 75, 100)
    private val leftAxisWidth = 28f * density

    private var touchX = 0f
    private var touchY = 0f

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            touchX = e.x
            touchY = e.y
            findAndEmitNearestPoint()
            return true
        }
    })

    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }

    private var chartLeft = 0f
    private var chartTop = 0f
    private var chartWidth = 0f
    private var chartHeight = 0f
    private var stepX = 0f

    private fun findAndEmitNearestPoint() {
        val pts = data.ifEmpty {
            values.mapIndexed { i, v -> PointData("${i + 1}", v, "%.0f%%".format(v)) }
        }
        if (pts.isEmpty()) return
        val n = pts.size
        val hitRadius = 40f * density
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in 0 until n) {
            val x = chartLeft + i * stepX
            val v = pts[i].percent
            val y = chartTop + chartHeight * (1f - v / 100f)
            val dx = touchX - x
            val dy = touchY - y
            val d = dx * dx + dy * dy
            if (d < hitRadius * hitRadius && d < bestDist) {
                bestDist = d
                best = i
            }
        }
        if (best >= 0) {
            onPointSelected?.invoke(best)
            onPointTapped?.invoke(pts[best].tooltipText)
        } else {
            onEmptyAreaTapped?.invoke()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pts = data.ifEmpty { values.mapIndexed { i, v -> PointData("${i + 1}", v, "%.0f%%".format(v)) } }
        if (pts.size < 2) return

        val color = primaryColor
        linePaint.color = color
        dotPaint.color = color
        axisPaint.color = textColor

        val padTop = 8f * density
        val padBottom = 24f * density
        val padRight = 12f * density

        chartLeft = paddingStart + leftAxisWidth
        chartTop = paddingTop + padTop
        chartWidth = (width - paddingStart - paddingEnd - leftAxisWidth - padRight).coerceAtLeast(40f)
        chartHeight = (height - paddingTop - paddingBottom - padTop - padBottom).coerceAtLeast(40f)

        val n = pts.size
        stepX = chartWidth / (n - 1).coerceAtLeast(1)

        // Y-axis ticks (0, 25, 50, 75, 100)
        for (tick in yTicks) {
            val y = chartTop + chartHeight * (1f - tick / 100f)
            canvas.drawLine(chartLeft - 4f, y, chartLeft, y, tickPaint)
            val label = "$tick"
            canvas.drawText(label, paddingStart.toFloat(), y + axisPaint.textSize / 3, axisPaint)
        }

        // X-axis labels (under each point)
        pts.forEachIndexed { i, p ->
            val x = chartLeft + i * stepX
            val label = p.xLabel
            val tw = axisPaint.measureText(label)
            canvas.drawText(label, x - tw / 2, height - paddingBottom - 4f, axisPaint)
        }

        // Line and dots
        path.reset()
        pts.forEachIndexed { i, p ->
            val v = p.percent.coerceIn(0f, 100f)
            val x = chartLeft + i * stepX
            val y = chartTop + chartHeight * (1f - v / 100f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        pts.forEachIndexed { i, p ->
            val v = p.percent.coerceIn(0f, 100f)
            val x = chartLeft + i * stepX
            val y = chartTop + chartHeight * (1f - v / 100f)
            canvas.drawCircle(x, y, dotRadiusPx, dotPaint)
        }
    }
}
