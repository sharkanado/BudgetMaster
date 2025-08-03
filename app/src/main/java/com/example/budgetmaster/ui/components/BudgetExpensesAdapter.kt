package com.example.budgetmaster.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetExpenseItem

class BudgetExpensesAdapter(
    private val expenses: MutableList<BudgetExpenseItem>,
    private val onHeaderClick: (Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val TYPE_HEADER = 0
    private val TYPE_ITEM = 1

    override fun getItemViewType(position: Int): Int {
        return if (expenses[position].isHeader) TYPE_HEADER else TYPE_ITEM
    }

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

            // Rotate arrow based on expanded state
            holder.arrow.rotation = if (item.isExpanded) 90f else 0f

            holder.itemView.setOnClickListener {
                onHeaderClick(position)
            }
        } else if (holder is ItemViewHolder) {
            holder.description.text = item.description
            holder.amount.text = "${item.amount}"
            holder.date.text = item.date
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
    }
}
