package com.example.budgetmaster.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetItem
import java.util.Locale
import kotlin.math.abs

class BudgetsAdapter(
    private var items: List<BudgetItem>,
    private val onItemClick: (BudgetItem) -> Unit
) : RecyclerView.Adapter<BudgetsAdapter.BudgetViewHolder>() {

    // budgetId -> aggregated total (null until loaded)
    private val totals: MutableMap<String, Double?> = mutableMapOf()

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
        val budget = items[position]
        val ctx = holder.itemView.context

        holder.walletName.text = budget.name

        val total = totals[budget.id]
        if (total == null) {
            // Placeholder while loading
            holder.balanceText.text = ctx.getString(R.string.loading_ellipsis) // "Loading…"
            // keep default color
        } else {
            // Budgets/{id}/expenses contains only expenses → show negative sign and color red
            val currency = budget.preferredCurrency.ifBlank { "PLN" }
            holder.balanceText.text = String.format(
                Locale.ENGLISH, "-%.2f %s", abs(total), currency
            )

            // Color like personal wallet: expenses = red, income = green.
            // For budgets we only have expenses, so red when total > 0; grey if zero.
            val colorRes = when {
                total > 0.0 -> R.color.red_error
                total == 0.0 -> R.color.grey_light
                else -> R.color.green_success // future-proof if you ever have negative totals
            }
            holder.balanceText.setTextColor(ContextCompat.getColor(ctx, colorRes))
        }

        // Clear previous avatars (recycling)
        holder.avatarsLayout.removeAllViews()

        // Simple avatar + member count
        val icon = ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(48, 48)
            setImageResource(R.drawable.ic_person)
            setPadding(8, 8, 8, 8)
        }
        holder.avatarsLayout.addView(icon)

        val countView = TextView(ctx).apply {
            text = "${budget.members.size}"
            setTextColor(ContextCompat.getColor(ctx, R.color.grey_light))
            textSize = 12f
            setPadding(8, 0, 0, 0)
        }
        holder.avatarsLayout.addView(countView)

        // Ripple + click
        holder.itemView.isClickable = true
        holder.itemView.isFocusable = true
        holder.itemView.setBackgroundResource(R.drawable.expense_ripple)
        holder.itemView.setOnClickListener { onItemClick(budget) }
    }

    override fun getItemCount(): Int = items.size

    /** Replace full list (prevents duplicates) */
    fun submitList(newItems: List<BudgetItem>) {
        items = newItems
        val ids = items.map { it.id }.toSet()
        totals.keys.retainAll(ids) // drop totals for removed items
        notifyDataSetChanged()
    }

    /** Update a single budget's aggregated expense total */
    fun updateBudgetTotal(budgetId: String, total: Double) {
        totals[budgetId] = total
        val idx = items.indexOfFirst { it.id == budgetId }
        if (idx != -1) notifyItemChanged(idx)
    }
}
