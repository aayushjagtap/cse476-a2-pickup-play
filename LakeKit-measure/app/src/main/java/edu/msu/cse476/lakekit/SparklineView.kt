package edu.msu.cse476.lakekit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class SparklineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = ContextCompat.getColor(context, R.color.chart_line)
    }
    private val path = Path()
    private var values = FloatArray(0)

    fun setValues(newValues: List<Float>) {
        values = newValues.toFloatArray()
        rebuild()
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuild()
    }

    private fun rebuild() {
        path.reset()
        if (values.size < 2 || width == 0 || height == 0) return
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0f } ?: 1f
        val inset = paint.strokeWidth
        val usable = height - 2f * inset
        val dx = width.toFloat() / (values.size - 1)
        values.forEachIndexed { i, v ->
            val x = i * dx
            val y = inset + usable * (1f - (v - min) / span)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPath(path, paint)
    }
}
