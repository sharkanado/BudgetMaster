package com.example.budgetmaster

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ExpensesAdapter(
    private val items: List<ExpenseListItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ExpenseListItem.Header -> TYPE_HEADER
        is ExpenseListItem.Item -> TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recyclerview_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_expense, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ExpenseListItem.Header -> (holder as HeaderViewHolder).bind(item)
            is ExpenseListItem.Item -> (holder as ItemViewHolder).bind(item)
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText = itemView.findViewById<TextView>(R.id.dailyExpensesDate)
        private val totalText = itemView.findViewById<TextView>(R.id.dailyExpensesSummary)

        fun bind(header: ExpenseListItem.Header) {
            dateText.text = header.date
            totalText.text = header.total
        }
    }

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon = itemView.findViewById<ImageView>(R.id.iconImage)
        private val name = itemView.findViewById<TextView>(R.id.dailyExpensesName)
        private val budget = itemView.findViewById<TextView>(R.id.dailyExpensesDate);
        private val amount = itemView.findViewById<TextView>(R.id.dailyExpensesSummary)

        fun bind(item: ExpenseListItem.Item) {
            icon.setImageResource(item.iconResId)
            name.text = item.name
            budget.text = item.budget
            amount.text = item.amount
        }
    }
}
