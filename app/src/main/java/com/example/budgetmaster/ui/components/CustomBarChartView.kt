package com.example.budgetmaster.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.max

class CustomBarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var incomeData = List(12) { 0f }
    private var expenseData = List(12) { 0f }

    private var animatedProgress = 1f // 0..1 for animation

    // Click callback
    private var onMonthClickListener: ((Int) -> Unit)? = null
    private var selectedMonthIndex: Int = -1 // Bold legend for this month

    private val incomePaint = Paint().apply {
        color = Color.parseColor("#4CAF50") // Green for income
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val expensePaint = Paint().apply {
        color = Color.parseColor("#F44336") // Red for expenses
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val axisPaint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 2f
    }

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#555555")
        strokeWidth = 1f
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        isAntiAlias = true
        textAlign = Paint.Align.RIGHT
    }

    private val monthPaint = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val noDataPaint = Paint().apply {
        color = Color.GRAY
        textSize = 40f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    fun setData(income: List<Float>, expenses: List<Float>) {
        incomeData = income
        expenseData = expenses

        // Start animation
        startAnimation()
    }

    private fun startAnimation() {
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 600 // 0.6 second animation
        animator.addUpdateListener {
            animatedProgress = it.animatedValue as Float
            invalidate()
        }
        animator.start()
    }

    fun setOnMonthClickListener(listener: (Int) -> Unit) {
        onMonthClickListener = listener
    }

    fun highlightMonth(index: Int) {
        selectedMonthIndex = index
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        val leftPadding = 80f
        val labelSpace = height * 0.2f
        val chartHeight = height - labelSpace

        // Determine max value rounded to nearest 100
        var maxValue = max(incomeData.maxOrNull() ?: 0f, expenseData.maxOrNull() ?: 0f)
        if (maxValue == 0f) maxValue = 1f
        val maxHundred = ceil(maxValue / 100f) * 100f
        val scale = (chartHeight * 0.8f) / maxHundred

        // Draw horizontal grid lines + labels
        val steps = 5
        val stepValue = maxHundred / steps
        val stepHeight = (chartHeight * 0.8f) / steps

        for (i in 0..steps) {
            val y = chartHeight - (i * stepHeight)
            canvas.drawLine(leftPadding, y, width, y, gridPaint)
            canvas.drawText("${(i * stepValue).toInt()}", leftPadding - 8f, y + 8f, textPaint)
        }

        // Group layout
        val groupWidth = (width - leftPadding) / 12f
        val barWidth = groupWidth * 0.2f
        val groupSpacing = groupWidth * 0.6f

        var xPos = leftPadding
        var hasData = false

        for (i in 0 until 12) {
            val incomeHeight = incomeData[i] * scale * animatedProgress
            val expenseHeight = expenseData[i] * scale * animatedProgress

            if (incomeData[i] > 0 || expenseData[i] > 0) hasData = true

            // Income bar
            canvas.drawRect(
                xPos,
                chartHeight - incomeHeight,
                xPos + barWidth,
                chartHeight,
                incomePaint
            )

            // Expense bar (hugging income bar)
            canvas.drawRect(
                xPos + barWidth,
                chartHeight - expenseHeight,
                xPos + 2 * barWidth,
                chartHeight,
                expensePaint
            )

            // Month label (bold if selected)
            monthPaint.typeface =
                if (i == selectedMonthIndex) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val labelX = xPos + barWidth
            val labelY = chartHeight + (labelSpace / 2)
            canvas.drawText(months[i], labelX, labelY, monthPaint)

            // Move to next group
            xPos += 2 * barWidth + groupSpacing
        }

        // Baseline
        canvas.drawLine(leftPadding, chartHeight, width, chartHeight, axisPaint)

       
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            val width = width.toFloat()
            val leftPadding = 80f
            val groupWidth = (width - leftPadding) / 12f
            val barWidth = groupWidth * 0.2f
            val groupSpacing = groupWidth * 0.6f

            var xPos = leftPadding

            for (i in 0 until 12) {
                val barStart = xPos
                val barEnd = xPos + 2 * barWidth // income + expense

                if (event.x in barStart..barEnd) {
                    selectedMonthIndex = i
                    onMonthClickListener?.invoke(i)
                    invalidate()
                    return true
                }

                xPos += 2 * barWidth + groupSpacing
            }
        }
        return true
    }
}
