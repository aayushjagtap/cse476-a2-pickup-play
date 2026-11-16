package edu.msu.cse476.lakekit

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class SweepView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.sweep_beam)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.sweep_dot)
    }
    private val bounds = RectF()
    private var angle = 0f
    private var points = FloatArray(0)

    fun setReadings(readings: List<Reading>) {
        if (readings.isEmpty()) {
            points = FloatArray(0)
            invalidate()
            return
        }
        val low = readings.minOf { it.value }
        val span = (readings.maxOf { it.value } - low).takeIf { it > 0.0 } ?: 1.0
        val step = maxOf(1, readings.size / 120)
        val sampled = readings.filterIndexed { i, _ -> i % step == 0 }
        points = FloatArray(sampled.size * 2)
        sampled.forEachIndexed { i, r ->
            points[2 * i] = 360f * i / sampled.size
            points[2 * i + 1] = (0.2 + 0.75 * (r.value - low) / span).toFloat()
        }
        invalidate()
    }

    fun advance() {
        angle = (angle + 2f) % 360f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val grid = BitmapFactory.decodeResource(resources, R.drawable.sweep_grid)
        val radius = min(width, height) / 2f
        val cx = width / 2f
        val cy = height / 2f
        bounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawBitmap(grid, null, bounds, null)
        canvas.drawArc(bounds, angle - 40f, 40f, true, beamPaint)
        val dotRadius = 3f * resources.displayMetrics.density
        var i = 0
        while (i < points.size) {
            val a = Math.toRadians(points[i].toDouble())
            val d = points[i + 1] * radius
            canvas.drawCircle(cx + (d * cos(a)).toFloat(), cy + (d * sin(a)).toFloat(), dotRadius, dotPaint)
            i += 2
        }
    }
}
