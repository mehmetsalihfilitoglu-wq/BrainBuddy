package com.brainbuddy.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.brainbuddy.app.R

/**
 * Simple bar chart for topic correct/total and apps.
 * Layout: [Label] 12dp [ProgressBar] 12dp [Ratio/Count] — ratio never clips.
 */
class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class BarData(val label: String, val correct: Int, val total: Int)

    private val density = context.resources.displayMetrics.density
    private val dp12 = 12f * density
    private val dp48 = 48f * density

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.bb_text_muted).let {
            android.graphics.Color.argb(60, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it))
        }
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.emerald_primary)
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics)
        color = context.getColor(R.color.bb_text_muted)
    }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 13f, context.resources.displayMetrics)
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
        val barHeight = 24f * density
        val gap = 12f * density
        val padStart = paddingStart.toFloat()
        val padEnd = paddingEnd.toFloat()
        val padTop = paddingTop.toFloat()
        val availableWidth = width - paddingStart - paddingEnd

        // Reserve space for ratio first — never clip (min 48dp)
        val sampleRatio = "999/999"
        val ratioReserved = maxOf(dp48, chipPaint.measureText(sampleRatio) + dp12) + dp12
        val barRegionEnd = width - padEnd - ratioReserved

        // Label flexible, bar gets the rest (min 40dp); responsive for small screens
        val barMinWidth = 40f * density
        val labelMaxWidth = (availableWidth - ratioReserved - barMinWidth - dp12).coerceIn(24f, (availableWidth - ratioReserved) * 0.55f)
        val barRegionStart = padStart + labelMaxWidth + dp12
        val chartWidth = (barRegionEnd - barRegionStart).coerceAtLeast(20f)
        val maxTotal = data.maxOfOrNull { it.total }?.coerceAtLeast(1) ?: 1

        data.forEachIndexed { i, d ->
            val y = padTop + i * (barHeight + gap) + barHeight - 4
            val labelY = y
            val ellipsizedLabel = TextUtils.ellipsize(d.label, labelPaint, labelMaxWidth, TextUtils.TruncateAt.END)
            canvas.drawText(ellipsizedLabel.toString(), padStart, labelY, labelPaint)

            val barFullW = chartWidth * (d.total.toFloat() / maxTotal)
            val barFillW = if (d.total > 0) barFullW * (d.correct.toFloat() / d.total) else 0f
            val barY = padTop + i * (barHeight + gap)
            canvas.drawRoundRect(barRegionStart, barY, barRegionStart + barFullW, barY + barHeight - 4, 6f, 6f, bgPaint)
            if (barFillW > 0) {
                canvas.drawRoundRect(barRegionStart, barY, barRegionStart + barFillW, barY + barHeight - 4, 6f, 6f, fillPaint)
            }

            val chipText = "${d.correct}/${d.total}"
            val chipTextWidth = chipPaint.measureText(chipText)
            val chipX = width - padEnd - chipTextWidth
            canvas.drawText(chipText, chipX, y, chipPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val barHeight = 24f * density
        val gap = 12f * density
        val h = if (data.isEmpty()) 60 else (data.size * (barHeight + gap) + paddingTop + paddingBottom).toInt()
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h.coerceAtLeast(60), MeasureSpec.EXACTLY))
    }
}
