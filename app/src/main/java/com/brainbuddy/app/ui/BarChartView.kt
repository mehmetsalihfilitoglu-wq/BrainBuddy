package com.brainbuddy.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.brainbuddy.app.R

/**
 * Simple bar chart for topic correct/total.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class BarData(val label: String, val correct: Int, val total: Int)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.bb_text_muted).let { android.graphics.Color.argb(60, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it)) }
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.bb_primary)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 36f
        color = context.getColor(R.color.bb_text)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 32f
        color = context.getColor(R.color.bb_text_muted)
    }

    var data: List<BarData> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return
        val barHeight = 28f
        val gap = 16f
        val labelWidth = 100f
        val chipWidth = 48f
        val chartWidth = (width - paddingStart - paddingEnd - labelWidth - chipWidth - 16f).coerceAtLeast(60f)
        val startX = paddingStart + labelWidth
        val maxTotal = data.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1

        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 28f
            color = context.getColor(R.color.bb_text_muted)
        }
        data.forEachIndexed { i, d ->
            val y = paddingTop + i * (barHeight + gap)
            val label = if (d.label.length > 12) d.label.take(10) + ".." else d.label
            canvas.drawText(label, paddingStart.toFloat(), y + barHeight - 4, labelPaint)
            val barFullW = chartWidth * (d.total.toFloat() / maxTotal)
            val barFillW = if (d.total > 0) barFullW * (d.correct.toFloat() / d.total) else 0f
            canvas.drawRoundRect(startX, y, startX + barFullW, y + barHeight - 4, 6f, 6f, bgPaint)
            if (barFillW > 0) {
                canvas.drawRoundRect(startX, y, startX + barFillW, y + barHeight - 4, 6f, 6f, fillPaint)
            }
            val chipText = "${d.correct}/${d.total}"
            val chipX = startX + chartWidth + 12f
            canvas.drawText(chipText, chipX, y + barHeight - 4, chipPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val h = if (data.isEmpty()) 60 else (data.size * (28 + 16) + paddingTop + paddingBottom).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h.coerceAtLeast(60), MeasureSpec.EXACTLY))
    }
}
