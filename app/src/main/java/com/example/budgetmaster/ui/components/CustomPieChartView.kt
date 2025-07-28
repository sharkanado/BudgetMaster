package com.example.budgetmaster.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CustomPieChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    data class PieEntry(val value: Double, val label: String)

    interface OnSliceClickListener {
        fun onSliceClick(label: String)
    }

    private var listener: OnSliceClickListener? = null

    fun setOnSliceClickListener(listener: OnSliceClickListener) {
        this.listener = listener
    }

    private var pieData: List<PieEntry> = emptyList()
    private var total: Double = 0.0
    private var highlightedCategory: String? = null

    private val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.WHITE
    }

    private val colors = listOf(
        Color.parseColor("#FFA726"),
        Color.parseColor("#66BB6A"),
        Color.parseColor("#29B6F6"),
        Color.parseColor("#AB47BC"),
        Color.parseColor("#EF5350"),
        Color.parseColor("#FFCA28"),
        Color.parseColor("#26C6DA"),
        Color.parseColor("#8D6E63"),
        Color.parseColor("#42A5F5"),
        Color.parseColor("#7E57C2")
    )

    /**
     * Set pie data and optionally highlight a category
     */
    fun setData(data: List<PieEntry>, highlightCategory: String?) {
        pieData = data
        total = data.sumOf { it.value }
        highlightedCategory = highlightCategory
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pieData.isEmpty() || total == 0.0) return

        val width = width.toFloat()
        val height = height.toFloat()
        val baseDiameter = min(width, height) * 0.8f
        val baseRadius = baseDiameter / 2
        val cx = width / 2
        val cy = height / 2

        var startAngle = -90f

        // First pass: draw slices
        pieData.forEachIndexed { index, entry ->
            val sweepAngle = ((entry.value / total) * 360).toFloat()
            slicePaint.color = colors[index % colors.size]

            val isHighlighted = highlightedCategory != null &&
                    entry.label.equals(highlightedCategory, ignoreCase = true)

            val radius = if (isHighlighted) baseRadius * 1.05f else baseRadius

            // Draw slice
            canvas.drawArc(
                cx - radius, cy - radius,
                cx + radius, cy + radius,
                startAngle, sweepAngle, true, slicePaint
            )

            // Draw percentage text
            val midAngle = startAngle + sweepAngle / 2
            val labelRadius = radius * 0.6f
            val labelX = cx + labelRadius * cos(Math.toRadians(midAngle.toDouble())).toFloat()
            val labelY = cy + labelRadius * sin(Math.toRadians(midAngle.toDouble())).toFloat()

            val percentage = (entry.value / total * 100).toInt()
            if (percentage > 3) {
                canvas.drawText("$percentage%", labelX, labelY, textPaint)
            }

            startAngle += sweepAngle
        }

        // Second pass: draw outlines on top (only highlighted slices)
        startAngle = -90f
        pieData.forEach { entry ->
            val sweepAngle = ((entry.value / total) * 360).toFloat()

            if (highlightedCategory != null &&
                entry.label.equals(highlightedCategory, ignoreCase = true)
            ) {
                val radius = baseRadius * 1.05f
                canvas.drawArc(
                    cx - radius, cy - radius,
                    cx + radius, cy + radius,
                    startAngle, sweepAngle, true, outlinePaint
                )
            }

            startAngle += sweepAngle
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val cx = width / 2f
            val cy = height / 2f
            val dx = event.x - cx
            val dy = event.y - cy

            // Distance from center
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble())
            val radius = min(width, height) * 0.8f / 2
            if (distance > radius) return false // Outside pie

            // Compute angle
            var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))
            if (angle < 0) angle += 360.0
            angle = (angle + 90) % 360

            // Find slice
            var currentStart = 0f
            for (i in pieData.indices) {
                val sweep = ((pieData[i].value / total) * 360).toFloat()
                val end = currentStart + sweep
                if (angle >= currentStart && angle < end) {
                    listener?.onSliceClick(pieData[i].label)
                    return true
                }
                currentStart = end
            }
        }
        return true
    }
}
