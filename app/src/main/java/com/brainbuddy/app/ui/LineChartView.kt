package com.brainbuddy.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.brainbuddy.app.R

/**
 * Simple line chart for last 10 tests accuracy trend (0-100%).
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = context.getColor(R.color.bb_primary)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.bb_primary)
    }
    private val path = Path()
    var values: List<Float> = emptyList()
        set(value) {
            field = value.map { it.coerceIn(0f, 100f) }
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return
        val padH = 24f
        val padV = 20f
        val w = (width - 2 * padH).coerceAtLeast(40f)
        val h = (height - 2 * padV).coerceAtLeast(40f)
        val stepX = w / (values.size - 1).coerceAtLeast(1)
        path.reset()
        values.forEachIndexed { i, v ->
            val x = padH + i * stepX
            val y = padV + h * (1f - v / 100f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
        values.forEachIndexed { i, v ->
            val x = padH + i * stepX
            val y = padV + h * (1f - v / 100f)
            canvas.drawCircle(x, y, 6f, dotPaint)
        }
    }
}
