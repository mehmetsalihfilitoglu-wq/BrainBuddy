package com.mioacademy.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.mioacademy.app.R

/**
 * Simple horizontal stacked bar for Correct (green) / Wrong (red) / Blank (gray) breakdown.
 */
class MiniStackedBarView @JvmOverloads constructor(
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
            android.graphics.Color.argb(80, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it))
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = (correct + wrong + blank).toFloat().coerceAtLeast(1f)
        val w = (width - paddingStart - paddingEnd).toFloat().coerceAtLeast(40f)
        val h = (height - paddingTop - paddingBottom).toFloat().coerceAtLeast(8f)
        val y = paddingTop.toFloat()
        var x = paddingStart.toFloat()
        val radius = (h / 2f).coerceAtMost(8f)

        fun drawSegment(paint: Paint, count: Int) {
            if (count <= 0) return
            val segW = w * (count / total)
            if (segW < 2f) return
            canvas.drawRoundRect(x, y, x + segW, y + h, radius, radius, paint)
            x += segW
        }
        drawSegment(correctPaint, correct)
        drawSegment(wrongPaint, wrong)
        drawSegment(blankPaint, blank)
    }
}
