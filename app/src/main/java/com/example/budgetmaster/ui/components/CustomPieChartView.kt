package com.example.budgetmaster.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class CustomPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class PieEntry(val value: Double, val label: String)

    private var pieData: List<PieEntry> = emptyList()
    private var total: Double = 0.0

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val colors = listOf(
        Color.parseColor("#FFA726"),
        Color.parseColor("#66BB6A"),
        Color.parseColor("#29B6F6"),
        Color.parseColor("#AB47BC"),
        Color.parseColor("#EF5350"),
        Color.parseColor("#FFCA28"),
        Color.parseColor("#26C6DA"),
        Color.parseColor("#8D6E63")
    )

    fun setData(data: List<PieEntry>) {
        pieData = data
        total = data.sumOf { it.value }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pieData.isEmpty() || total == 0.0) return

        val width = width.toFloat()
        val height = height.toFloat()
        val radius = min(width, height) / 2 * 0.8f
        val cx = width / 2
        val cy = height / 2

        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

        var startAngle = -90f

        pieData.forEachIndexed { index, entry ->
            val sweepAngle = ((entry.value / total) * 360).toFloat()
            paint.color = colors[index % colors.size]

            // Draw pie slice
            canvas.drawArc(rect, startAngle, sweepAngle, true, paint)

            // Draw percentage text inside the slice
            val midAngle = startAngle + sweepAngle / 2
            val textRadius = radius * 0.6f
            val x =
                (cx + textRadius * kotlin.math.cos(Math.toRadians(midAngle.toDouble()))).toFloat()
            val y =
                (cy + textRadius * kotlin.math.sin(Math.toRadians(midAngle.toDouble()))).toFloat()

            val percentage = (entry.value / total) * 100
            if (percentage >= 5) { // Show label only if slice >5% for readability
                canvas.drawText("${percentage.toInt()}%", x, y, textPaint)
            }

            startAngle += sweepAngle
        }
    }
}

