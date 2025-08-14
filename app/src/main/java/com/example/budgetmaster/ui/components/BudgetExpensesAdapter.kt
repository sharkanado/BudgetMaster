package com.example.budgetmaster.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetExpenseItem
import java.util.Locale

class BudgetExpensesAdapter(
    private val expenses: MutableList<BudgetExpenseItem>,
    private val userNames: Map<String, String>,             // UID → Name map
    private val onHeaderClick: (Int) -> Unit,               // For accordion toggle
    private val onExpenseClick: (BudgetExpenseItem) -> Unit // For navigation
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_ITEM = 1

    /**
     * Pass ONLY raw expense rows (no headers).
     * Rebuilds list as [Header, month-rows…] with:
     * - Months sorted newest → oldest
     * - Rows within each month sorted by DATE (yyyy-MM-dd string) newest → oldest
     */
    fun setData(rawExpenses: List<BudgetExpenseItem>) {
        val rebuilt = groupSortAndBuild(rawExpenses)
        expenses.clear()
        expenses.addAll(rebuilt)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int =
        if (expenses[position].isHeader) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_budget_expense_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_budget_expense_row, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = expenses[position]
        if (holder is HeaderViewHolder) {
            holder.title.text = item.description
            holder.arrow.rotation = if (item.isExpanded) 90f else 0f
            holder.itemView.setOnClickListener { onHeaderClick(position) }
        } else if (holder is ItemViewHolder) {
            holder.description.text = item.description
            holder.amount.text = String.format(Locale.ENGLISH, "%.2f", item.amount)
            holder.date.text = formatDate(item.date)
            holder.paidBy.text = userNames[item.createdBy] ?: "Unknown"
            holder.itemView.setOnClickListener { onExpenseClick(item) }
        }
    }

    override fun getItemCount(): Int = expenses.size

    // --- ViewHolders ---
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.headerTitle)
        val arrow: ImageView = view.findViewById(R.id.headerArrow)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val description: TextView = view.findViewById(R.id.expenseDescription)
        val amount: TextView = view.findViewById(R.id.expenseAmount)
        val date: TextView = view.findViewById(R.id.expenseDate)
        val paidBy: TextView = view.findViewById(R.id.expensePaidBy)
    }

    /**
     * Groups by "yyyy-MM" (first 7 chars), sorts months DESC, and items within each month DESC by date string.
     */
    private fun groupSortAndBuild(rowsInput: List<BudgetExpenseItem>): List<BudgetExpenseItem> {
        // Safety: only item rows
        val rows = rowsInput.filter { !it.isHeader }

        // Group by month key "yyyy-MM"
        val grouped = rows.groupBy { item ->
            item.date.take(7) // assumes "yyyy-MM-dd"
        }

        // Sort months newest → oldest by key (string order works for yyyy-MM)
        val sortedMonthKeys = grouped.keys.sortedDescending()

        val result = mutableListOf<BudgetExpenseItem>()
        for (monthKey in sortedMonthKeys) {
            // Build header like "August 2025"
            val headerTitle = monthKeyToTitle(monthKey)

            result += BudgetExpenseItem(
                description = headerTitle,
                amount = 0.0,
                date = "$monthKey-01", // internal; not shown
                createdBy = "",
                isHeader = true,
                isExpanded = true
            )

            // Sort items within this month newest → oldest by date string "yyyy-MM-dd"
            val monthItems = grouped[monthKey].orEmpty()
                .sortedByDescending { it.date }

            result += monthItems
        }

        return result
    }

    private fun monthKeyToTitle(key: String): String {
        // key expected: "yyyy-MM"
        return try {
            val (yearStr, monthStr) = key.split("-")
            val year = yearStr.toInt()
            val month = monthStr.toInt() // 1..12
            val monthName = java.text.DateFormatSymbols(Locale.ENGLISH).months[month - 1]
            "$monthName $year"
        } catch (_: Exception) {
            key // fallback
        }
    }
}

private fun formatDate(dateStr: String): String {
    return try {
        val input = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val output = java.text.SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        output.format(input.parse(dateStr)!!)
    } catch (_: Exception) {
        dateStr
    }
}
