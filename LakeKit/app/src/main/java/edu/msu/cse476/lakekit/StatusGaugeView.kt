package edu.msu.cse476.lakekit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class StatusGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 15f * density
    }
    private val trackPaint = Paint().apply { color = Color.LTGRAY }
    private val fillPaint = Paint().apply { color = Color.rgb(98, 0, 238) }
    private var level = 0

    fun setLevel(value: Int) {
        level = value.coerceIn(0, 100)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawText("Lake level: $level% of range", 0f, textPaint.textSize, textPaint)
        val top = textPaint.textSize * 1.6f
        val bottom = top + 6f * density
        canvas.drawRect(0f, top, width.toFloat(), bottom, trackPaint)
        canvas.drawRect(0f, top, width * level / 100f, bottom, fillPaint)
    }
}
