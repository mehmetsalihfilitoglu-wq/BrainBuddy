package com.edumio.app.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.edumio.app.R

/**
 * Lightweight donut/ring chart for overall accuracy or segment breakdown.
 * Same style as ProgressRingView: stroke width 24dp, bb_primary fill, rounded corners.
 */
class DonutChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    data class Segment(val value: Float, val color: Int)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f
        color = context.getColor(R.color.bb_text_muted).let {
            android.graphics.Color.argb(60, android.graphics.Color.red(it), android.graphics.Color.green(it), android.graphics.Color.blue(it))
        }
    }
    private val segmentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 24f
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    /** Single value 0-100 for simple accuracy display. Use this OR segments. */
    var progress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 100f)
            useProgressMode = true
            invalidate()
        }

    /** Multiple segments for correct/wrong/blank breakdown. Use this OR progress. */
    var segments: List<Segment> = emptyList()
        set(value) {
            field = value
            useProgressMode = false
            invalidate()
        }

    private var useProgressMode = true

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 24f / 2
        rect.set(pad, pad, width - pad, height - pad)
        canvas.drawArc(rect, 0f, 360f, false, bgPaint)

        if (useProgressMode) {
            if (progress > 0f) {
                segmentPaint.color = context.getColor(R.color.bb_primary)
                canvas.drawArc(rect, -90f, 3.6f * progress, false, segmentPaint)
            }
        } else {
            val total = segments.sumOf { it.value.toDouble() }.toFloat()
            if (total <= 0f) return
            var sweepStart = -90f
            segments.forEach { seg ->
                val sweep = 360f * (seg.value / total)
                if (sweep > 0) {
                    segmentPaint.color = seg.color
                    canvas.drawArc(rect, sweepStart, sweep, false, segmentPaint)
                    sweepStart += sweep
                }
            }
        }
    }
}
