package com.example.budgetmaster.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetItem

class BudgetsAdapter(
    private val budgets: List<BudgetItem>,
    private val onItemClick: (BudgetItem) -> Unit
) : RecyclerView.Adapter<BudgetsAdapter.BudgetViewHolder>() {

    inner class BudgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val walletName: TextView = itemView.findViewById(R.id.walletName)
        val balanceText: TextView = itemView.findViewById(R.id.balanceText)
        val avatarsLayout: LinearLayout = itemView.findViewById(R.id.avatarsLayout)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budgets, parent, false)
        return BudgetViewHolder(view)
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        val budget = budgets[position]

        holder.walletName.text = budget.name
        holder.balanceText.text = "${budget.balance.toInt()} ${budget.preferredCurrency}"

        // Clear previous views (if recycled)
        holder.avatarsLayout.removeAllViews()

        // Add first member icon
        val icon = ImageView(holder.itemView.context).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            setImageResource(R.drawable.ic_person)
            setPadding(8, 8, 8, 8)
        }
        holder.avatarsLayout.addView(icon)


        val textView = TextView(holder.itemView.context).apply {
            text = "${budget.members.size}"
            setTextColor(holder.itemView.context.getColor(R.color.grey_light))
            textSize = 12f
            setPadding(8, 0, 0, 0)
        }
        holder.avatarsLayout.addView(textView)

        // Ripple effect & click
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setBackgroundResource(R.drawable.expense_ripple)

        holder.itemView.setOnClickListener {
            onItemClick(budget)
        }
    }

    override fun getItemCount(): Int = budgets.size
}
