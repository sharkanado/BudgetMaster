package com.example.budgetmaster.ui.components

import ExpenseListItem
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.utils.Categories
import java.util.Locale
import kotlin.math.max

class ExpensesAdapter(
    private var items: List<ExpenseListItem>,
    private val onItemClick: ((ExpenseListItem.Item) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1

        // Extract a numeric token from arbitrary text, accept spaces, '.' or ',' as separators.
        // Treat the LAST separator ('.' or ',') as the decimal point; others are grouping.
        private fun parseAmountOrNull(raw: String): Double? {
            val match = Regex("[-+]?\\d[\\d.,\\s]*").find(raw.trim()) ?: return null
            var token = match.value.replace("\\s".toRegex(), "") // drop spaces

            val lastDot = token.lastIndexOf('.')
            val lastComma = token.lastIndexOf(',')
            val decIdx = max(lastDot, lastComma)

            token = if (decIdx >= 0) {
                val intPart = token.substring(0, decIdx).replace(".", "").replace(",", "")
                val fracPart = token.substring(decIdx + 1).replace(".", "").replace(",", "")
                "$intPart.$fracPart"
            } else {
                // No decimal separator -> remove groupings
                token.replace(".", "").replace(",", "")
            }
            return token.toDoubleOrNull()
        }

        private fun formatAmount(value: Double): String =
            String.format(Locale.US, "%.2f", value)

        // Robust formatter for strings: try to parse; if ok -> format with dot+2dp,
        // else return original with commas normalized to dots so it still shows.
        private fun formatAmountFromString(s: String): String {
            val n = parseAmountOrNull(s)
            return if (n != null) formatAmount(n) else s.replace(',', '.')
        }
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ExpenseListItem.Header -> TYPE_HEADER
        is ExpenseListItem.Item -> TYPE_ITEM
        else -> throw IllegalArgumentException("Unknown view type at position $position")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_expenses_header, parent, false))
        } else {
            ItemViewHolder(inflater.inflate(R.layout.item_expense, parent, false), onItemClick)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ExpenseListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ExpenseListItem.Item -> (holder as ItemViewHolder).bind(item)
            else -> throw IllegalArgumentException("Unsupported item type at position $position")
        }
    }

    fun updateItems(newItems: List<ExpenseListItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    // Header ViewHolder
    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dailyExpensesDate)
        private val totalText: TextView = itemView.findViewById(R.id.dailyExpensesSummary)

        fun bind(header: ExpenseListItem.Header) {
            dateText.text = header.date
            // Safely format totals no matter if they arrive like "123,45", "1 234,56", "Total: 12,34 PLN"
            totalText.text = formatAmountFromString(header.total)
        }
    }

    // Item ViewHolder
    inner class ItemViewHolder(
        private val itemViewRoot: View,
        private val onItemClick: ((ExpenseListItem.Item) -> Unit)?
    ) : RecyclerView.ViewHolder(itemViewRoot) {

        private val icon: ImageView = itemViewRoot.findViewById(R.id.iconImage)
        private val categoryText: TextView = itemViewRoot.findViewById(R.id.dailyExpensesCategory)
        private val nameText: TextView = itemViewRoot.findViewById(R.id.dailyExpensesName)
        private val amountText: TextView = itemViewRoot.findViewById(R.id.dailyExpensesSummary)
        private val iconContainer: View = itemViewRoot.findViewById(R.id.iconContainer)

        fun bind(item: ExpenseListItem.Item) {
            categoryText.text = item.category
            nameText.text = item.name

            // Amount: always dot + 2 decimals (robust against "12,5" etc.)
            amountText.text = formatAmountFromString(item.amount)

            val colorRes = if (item.type == "income") R.color.green_success else R.color.red_error
            amountText.setTextColor(ContextCompat.getColor(itemViewRoot.context, colorRes))

            val color = Categories.getColor(item.category)
            ContextCompat.getDrawable(itemViewRoot.context, R.drawable.bg_circle)?.let { bg ->
                bg.setTint(color)
                iconContainer.background = bg
            }

            icon.setImageResource(Categories.getIcon(item.category))

            if (onItemClick != null) {
                itemView.isClickable = true
                itemView.isFocusable = true
                itemView.setBackgroundResource(R.drawable.expense_ripple)
                itemView.setOnClickListener { onItemClick.invoke(item) }
            } else {
                itemView.isClickable = false
                itemView.isFocusable = false
                itemView.setBackgroundResource(R.drawable.expense_bg)
                itemView.setOnClickListener(null)
            }
        }
    }
}
