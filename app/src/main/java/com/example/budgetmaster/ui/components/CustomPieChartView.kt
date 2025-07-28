package com.example.budgetmaster.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
    private var highlightCategory: String? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Category -> Color map
    private val categoryColors = mapOf(
        "Food" to Color.parseColor("#FFA726"),
        "Transport" to Color.parseColor("#29B6F6"),
        "Entertainment" to Color.parseColor("#AB47BC"),
        "Bills" to Color.parseColor("#EF5350"),
        "Health" to Color.parseColor("#66BB6A"),
        "Shopping" to Color.parseColor("#FFCA28"),
        "Savings" to Color.parseColor("#26C6DA"),
        "Investment" to Color.parseColor("#8D6E63"),
        "Salary" to Color.parseColor("#42A5F5"),
        "Gift" to Color.parseColor("#EC407A"),
        "Other" to Color.parseColor("#BDBDBD")
    )

    fun setData(data: List<PieEntry>, highlight: String? = null) {
        pieData = data
        total = data.sumOf { it.value }
        highlightCategory = highlight
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pieData.isEmpty() || total == 0.0) return

        val width = width.toFloat()
        val height = height.toFloat()
        val diameter = min(width, height) * 0.8f
        val radius = diameter / 2
        val cx = width / 2
        val cy = height / 2

        var startAngle = -90f

        pieData.forEach { entry ->
            val sweepAngle = ((entry.value / total) * 360).toFloat()

            // Choose category color or fallback
            paint.color = categoryColors[entry.label] ?: Color.GRAY

            val isHighlighted = entry.label == highlightCategory
            val extraRadius = if (isHighlighted) radius * 0.05f else 0f

            // Draw slice
            canvas.drawArc(
                cx - radius - extraRadius,
                cy - radius - extraRadius,
                cx + radius + extraRadius,
                cy + radius + extraRadius,
                startAngle,
                sweepAngle,
                true,
                paint
            )

            startAngle += sweepAngle
        }
    }
}
