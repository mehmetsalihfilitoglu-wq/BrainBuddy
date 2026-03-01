package com.brainbuddy.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.brainbuddy.app.R

/**
 * Mini bar chart for correct / wrong / blank totals.
 * Same style as BarChartView: stroke, corners, colors.
 */
class MiniBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var correct: Int = 0
        set(value) { field = value.coerceAtLeast(0); invalidate() }
    var wrong: Int = 0
        set(value) { field = value.coerceAtLeast(0); invalidate() }
    var blank: Int = 0
        set(value) { field = value.coerceAtLeast(0); invalidate() }

    private val correctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.bb_primary)
    }
    private val wrongPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.bb_error)
    }
    private val blankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.getColor(R.color.bb_text_muted).let {
            android.graphics.Color.argb(120, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it))
        }
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = context.getColor(R.color.bb_text)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = correct + wrong + blank
        if (total == 0) return

        val barHeight = 32f
        val padH = 24f
        val padV = 16f
        val maxW = (width - 2 * padH).coerceAtLeast(40f)
        val cW = maxW * (correct.toFloat() / total)
        val wW = maxW * (wrong.toFloat() / total)
        val bW = maxW * (blank.toFloat() / total)
        var x = padH
        val y = padV
        val radius = 6f

        if (correct > 0 && cW >= 4) {
            canvas.drawRoundRect(x, y, x + cW, y + barHeight, radius, radius, correctPaint)
            x += cW
        }
        if (wrong > 0 && wW >= 4) {
            canvas.drawRoundRect(x, y, x + wW, y + barHeight, radius, radius, wrongPaint)
            x += wW
        }
        if (blank > 0 && bW >= 4) {
            canvas.drawRoundRect(x, y, x + bW, y + barHeight, radius, radius, blankPaint)
        }

        val labelY = y + barHeight + 36f
        val labels = buildList {
            if (correct > 0) add("Doğru: $correct")
            if (wrong > 0) add("Yanlış: $wrong")
            if (blank > 0) add("Boş: $blank")
        }
        labels.forEachIndexed { i, s ->
            canvas.drawText(s, padH, labelY + i * 32f, labelPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val total = correct + wrong + blank
        val h = if (total == 0) 60 else 120
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(h.coerceAtLeast(60), MeasureSpec.EXACTLY))
    }
}
