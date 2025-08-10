package com.example.budgetmaster.ui.components

import ExpenseListItem
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.utils.Categories

class ExpensesAdapter(
    private var items: List<ExpenseListItem>,
    private val onItemClick: ((ExpenseListItem.Item) -> Unit)? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ExpenseListItem.Header -> TYPE_HEADER
        is ExpenseListItem.Item -> TYPE_ITEM
        else -> throw IllegalArgumentException("Unknown view type at position $position")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expenses_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_expense, parent, false)
            ItemViewHolder(view, onItemClick)
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
        private val dateText = itemView.findViewById<TextView>(R.id.dailyExpensesDate)
        private val totalText = itemView.findViewById<TextView>(R.id.dailyExpensesSummary)

        fun bind(header: ExpenseListItem.Header) {
            dateText.text = header.date
            totalText.text = header.total
        }
    }

    // Item ViewHolder
    class ItemViewHolder(
        private val itemViewRoot: View,
        private val onItemClick: ((ExpenseListItem.Item) -> Unit)?
    ) : RecyclerView.ViewHolder(itemViewRoot) {

        private val icon = itemViewRoot.findViewById<ImageView>(R.id.iconImage)
        private val categoryText = itemViewRoot.findViewById<TextView>(R.id.dailyExpensesCategory)
        private val nameText = itemViewRoot.findViewById<TextView>(R.id.dailyExpensesName)
        private val amountText = itemViewRoot.findViewById<TextView>(R.id.dailyExpensesSummary)
        private val iconContainer = itemViewRoot.findViewById<View>(R.id.iconContainer)

        fun bind(item: ExpenseListItem.Item) {
            // in ExpensesAdapter.ItemViewHolder.bind(), at the top:
            Log.d("DEBUG_CLASS", "VH bind item class=${item::class.qualifiedName}")

            // Set category text & name
            categoryText.text = item.category
            nameText.text = item.name

            // Amount with color for income/expense
            amountText.text = item.amount
            val colorRes = if (item.type == "income") R.color.green_success else R.color.red_error
            amountText.setTextColor(ContextCompat.getColor(itemViewRoot.context, colorRes))

            // Set round background with category color
            val color = Categories.getColor(item.category)
            val circleDrawable =
                ContextCompat.getDrawable(itemViewRoot.context, R.drawable.bg_circle)
            circleDrawable?.setTint(color)
            iconContainer.background = circleDrawable


            // Set category-specific icon
            icon.setImageResource(Categories.getIcon(item.category))

            // Click handler
            if (onItemClick != null) {
                itemView.isClickable = true
                itemView.isFocusable = true
                itemView.setBackgroundResource(R.drawable.expense_ripple)
                itemView.setOnClickListener {
                    Log.d(
                        "DEBUG",
                        "CLICK bid='${item.budgetId}', eid='${item.expenseIdInBudget}'"
                    )
                    onItemClick.invoke(item)
                }
            } else {
                itemView.isClickable = false
                itemView.isFocusable = false
                itemView.setBackgroundResource(R.drawable.expense_bg)
                itemView.setOnClickListener(null)
            }
        }
    }
}
