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
import androidx.core.content.res.ResourcesCompat
import com.example.budgetmaster.R
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

    // Typeface (Manrope)
    private val tfRegular: Typeface
    private val tfBold: Typeface

    private val incomePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50") // Green for income
        style = Paint.Style.FILL
    }

    private val expensePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F44336") // Red for expenses
        style = Paint.Style.FILL
    }

    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 2f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#555555")
        strokeWidth = 1f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.RIGHT
    }

    private val monthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
        textAlign = Paint.Align.CENTER
    }

    private val noDataPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    private val months = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    init {
        // Load Manrope fonts with safe fallbacks
        val regular = ResourcesCompat.getFont(context, R.font.manrope_regular)
        val bold = ResourcesCompat.getFont(context, R.font.manrope_extrabold)
        tfRegular = regular ?: Typeface.SANS_SERIF
        tfBold = bold ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)

        // Apply typefaces
        textPaint.typeface = tfRegular
        monthPaint.typeface = tfRegular
        noDataPaint.typeface = tfRegular
    }

    fun setData(income: List<Float>, expenses: List<Float>) {
        incomeData = income
        expenseData = expenses
        startAnimation()
    }

    private fun startAnimation() {
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
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

        // Horizontal grid lines + labels
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

            // Expense bar (next to income bar)
            canvas.drawRect(
                xPos + barWidth,
                chartHeight - expenseHeight,
                xPos + 2 * barWidth,
                chartHeight,
                expensePaint
            )

            // Month label (bold when selected)
            monthPaint.typeface = if (i == selectedMonthIndex) tfBold else tfRegular
            val labelX = xPos + barWidth
            val labelY = chartHeight + (labelSpace / 2)
            canvas.drawText(months[i], labelX, labelY, monthPaint)

            xPos += 2 * barWidth + groupSpacing
        }

        // Baseline
        canvas.drawLine(leftPadding, chartHeight, width, chartHeight, axisPaint)

        // Optional "No data" text (not previously drawn, but kept for completeness)
        if (!hasData) {
            canvas.drawText("No data", width / 2f, height / 2f, noDataPaint)
        }
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
