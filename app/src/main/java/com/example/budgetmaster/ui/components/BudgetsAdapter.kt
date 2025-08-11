package com.example.budgetmaster.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetItem
import java.util.Locale
import kotlin.math.abs
import kotlin.math.absoluteValue

class BudgetsAdapter(
    initialItems: List<BudgetItem>,
    private val onItemClick: (BudgetItem) -> Unit
) : ListAdapter<BudgetItem, BudgetsAdapter.BudgetViewHolder>(DIFF) {

    // budgetId -> aggregated total (null until loaded)
    private val totals: MutableMap<String, Double?> = mutableMapOf()

    init {
        setHasStableIds(true)
        submitList(initialItems)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budgets, parent, false)
        return BudgetViewHolder(view).apply {
            // Set static view props once (prevents ripple reset/flicker).
            itemView.isClickable = true
            itemView.isFocusable = true
            itemView.setBackgroundResource(R.drawable.expense_ripple)
        }
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        // Full bind
        val item = getItem(position)
        holder.bindFull(item, totals[item.id])
    }

    override fun onBindViewHolder(
        holder: BudgetViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty() && payloads.contains(PAYLOAD_TOTAL)) {
            // Lightweight update: just the total/color text.
            val item = getItem(position)
            holder.bindTotal(item, totals[item.id])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun submitList(list: List<BudgetItem>?) {
        super.submitList(list)
        // Prune totals for removed items (keeps map tidy).
        val ids = (list ?: emptyList()).map { it.id }.toSet()
        totals.keys.retainAll(ids)
    }

    /** Update a single budget's aggregated expense total (payload refresh only). */
    fun updateBudgetTotal(budgetId: String, total: Double) {
        val prev = totals[budgetId]
        // Only update if changed meaningfully (avoid duplicate visual updates).
        if (prev != null && (prev - total).absoluteValue < 0.0005) return

        totals[budgetId] = total
        val idx = currentList.indexOfFirst { it.id == budgetId }
        if (idx != -1) notifyItemChanged(idx, PAYLOAD_TOTAL)
    }

    inner class BudgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val walletName: TextView = itemView.findViewById(R.id.walletName)
        private val balanceText: TextView = itemView.findViewById(R.id.balanceText)
        private val avatarsLayout: LinearLayout = itemView.findViewById(R.id.avatarsLayout)

        fun bindFull(budget: BudgetItem, total: Double?) {
            walletName.text = budget.name
            bindTotal(budget, total)

            ensureAvatarAndCountViews()
            // Update member count text (child index 1 is the count TextView we create once).
            (avatarsLayout.getChildAt(1) as? TextView)?.text = budget.members.size.toString()

            itemView.setOnClickListener { onItemClick(budget) }
        }

        fun bindTotal(budget: BudgetItem, total: Double?) {
            val ctx = itemView.context
            if (total == null) {
                balanceText.text = ctx.getString(R.string.loading_ellipsis) // "Loading…"
                balanceText.setTextColor(ContextCompat.getColor(ctx, R.color.grey_light))
                return
            }

            val currency = budget.preferredCurrency.ifBlank { "PLN" }
            // Budgets/{id}/expenses currently holds only expenses → show as negative.
            balanceText.text = String.format(Locale.ENGLISH, "-%.2f %s", abs(total), currency)

            // Red for expenses, grey if zero (future-proof: green if ever negative).
            val colorRes = when {
                total > 0.0 -> R.color.red_error
                total == 0.0 -> R.color.grey_light
                else -> R.color.green_success
            }
            balanceText.setTextColor(ContextCompat.getColor(ctx, colorRes))
        }

        /** Create avatar icon + count TextView once; reuse on subsequent binds. */
        private fun ensureAvatarAndCountViews() {
            if (avatarsLayout.childCount >= 2) return

            val ctx = itemView.context
            // Icon
            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48)
                setImageResource(R.drawable.ic_person)
                setPadding(8, 8, 8, 8)
            }
            avatarsLayout.addView(icon)

            // Count
            val countView = TextView(ctx).apply {
                setTextColor(ContextCompat.getColor(ctx, R.color.grey_light))
                textSize = 12f
                setPadding(8, 0, 0, 0)
            }
            avatarsLayout.addView(countView)
        }
    }

    companion object {
        private const val PAYLOAD_TOTAL = "payload_total"

        private fun sameMembers(old: BudgetItem, new: BudgetItem): Boolean {
            // Order-insensitive comparison; works if members are unique (as expected).
            return old.members.toSet() == new.members.toSet()
        }

        val DIFF = object : DiffUtil.ItemCallback<BudgetItem>() {
            override fun areItemsTheSame(oldItem: BudgetItem, newItem: BudgetItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: BudgetItem, newItem: BudgetItem): Boolean =
                oldItem.name == newItem.name &&
                        oldItem.preferredCurrency == newItem.preferredCurrency &&
                        sameMembers(oldItem, newItem) &&
                        oldItem.ownerId == newItem.ownerId
            // Totals are held separately and updated via payload.
        }
    }
}
