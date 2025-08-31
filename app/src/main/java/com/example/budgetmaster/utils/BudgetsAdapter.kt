package com.example.budgetmaster.utils

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
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs
import kotlin.math.absoluteValue

class BudgetsAdapter(
    initialItems: List<BudgetItem>,
    private val onItemClick: (BudgetItem) -> Unit
) : ListAdapter<BudgetItem, BudgetsAdapter.BudgetViewHolder>(DIFF) {

    private val totals: MutableMap<String, Double?> = mutableMapOf()

    private val statuses: MutableMap<String, Status?> = mutableMapOf()

    private val df2 = DecimalFormat("0.00").apply {
        decimalFormatSymbols = DecimalFormatSymbols(Locale.ENGLISH).apply {
            decimalSeparator = '.'
            groupingSeparator = ','
        }
        isGroupingUsed = false
    }

    init {
        setHasStableIds(true)
        submitList(initialItems)
    }

    override fun getItemId(position: Int): Long = getItem(position).id.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BudgetViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budgets, parent, false)
        return BudgetViewHolder(view).apply {
            itemView.isClickable = true
            itemView.isFocusable = true
            itemView.setBackgroundResource(R.drawable.expense_ripple)
        }
    }

    override fun onBindViewHolder(holder: BudgetViewHolder, position: Int) {
        val item = getItem(position)
        holder.bindFull(item, totals[item.id], statuses[item.id])
    }

    override fun onBindViewHolder(
        holder: BudgetViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.isNotEmpty()) {
            val item = getItem(position)
            if (payloads.contains(PAYLOAD_TOTAL)) {
                holder.bindTotal(item, totals[item.id])
            }
            if (payloads.contains(PAYLOAD_STATUS)) {
                holder.bindStatus(item, statuses[item.id])
            }
        } else {
            onBindViewHolder(holder, position)
        }
    }

    override fun submitList(list: List<BudgetItem>?) {
        super.submitList(list)
        val ids = (list ?: emptyList()).map { it.id }.toSet()
        totals.keys.retainAll(ids)
        statuses.keys.retainAll(ids)
    }

    /** Update a single budget's aggregated expense total (payload refresh only). */
    fun updateBudgetTotal(budgetId: String, total: Double) {
        val prev = totals[budgetId]
        if (prev != null && (prev - total).absoluteValue < 0.0005) return
        totals[budgetId] = total
        val idx = currentList.indexOfFirst { it.id == budgetId }
        if (idx != -1) notifyItemChanged(idx, PAYLOAD_TOTAL)
    }

    /** Update a single budget's per-user status (payload refresh only). */
    fun updateBudgetStatus(budgetId: String, receivable: Double, debt: Double) {
        val newS =
            Status(receivable = receivable.coerceAtLeast(0.0), debt = debt.coerceAtLeast(0.0))
        val prev = statuses[budgetId]
        if (prev != null &&
            abs(prev.receivable - newS.receivable) < 0.0005 &&
            abs(prev.debt - newS.debt) < 0.0005
        ) return

        statuses[budgetId] = newS
        val idx = currentList.indexOfFirst { it.id == budgetId }
        if (idx != -1) notifyItemChanged(idx, PAYLOAD_STATUS)
    }

    inner class BudgetViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val walletName: TextView = itemView.findViewById(R.id.walletName)
        private val balanceText: TextView = itemView.findViewById(R.id.balanceText)
        private val avatarsLayout: LinearLayout = itemView.findViewById(R.id.avatarsLayout)
        private val statusText: TextView = itemView.findViewById(R.id.statusText)

        fun bindFull(budget: BudgetItem, total: Double?, status: Status?) {
            walletName.text = budget.name
            bindTotal(budget, total)
            bindStatus(budget, status)

            ensureAvatarAndCountViews()
            (avatarsLayout.getChildAt(1) as? TextView)?.text = budget.members.size.toString()

            itemView.setOnClickListener { onItemClick(budget) }
        }

        fun bindTotal(budget: BudgetItem, total: Double?) {
            val ctx = itemView.context
            if (total == null) {
                balanceText.text = ctx.getString(R.string.loading_ellipsis)
                balanceText.setTextColor(ContextCompat.getColor(ctx, R.color.grey_light))
                return
            }

            val currency = budget.preferredCurrency.ifBlank { "PLN" }
            val amount = abs(total)

            balanceText.text = "${df2.format(amount)} $currency"
        }


        fun bindStatus(budget: BudgetItem, status: Status?) {
            val ctx = itemView.context
            if (status == null) {
                statusText.text = ctx.getString(R.string.loading_ellipsis)
                statusText.setTextColor(ContextCompat.getColor(ctx, R.color.grey_light))
                return
            }
            val currency = budget.preferredCurrency.ifBlank { "PLN" }
            val receivable = status.receivable
            val debt = status.debt

            val (text, color) = when {
                receivable <= 0.0005 && debt <= 0.0005 -> {
                    "you're settled" to R.color.grey_light
                }

                receivable > debt -> {
                    val net = receivable - debt
                    "you're waiting for ${df2.format(net)} $currency" to R.color.green_success
                }

                debt > receivable -> {
                    val net = debt - receivable
                    "you have ${df2.format(net)} debt $currency" to R.color.red_error
                }

                else -> {
                    "you're settled" to R.color.grey_light
                }
            }

            statusText.text = text
            statusText.setTextColor(ContextCompat.getColor(ctx, color))
        }

        /** Create avatar icon + count TextView once; reuse on subsequent binds. */
        private fun ensureAvatarAndCountViews() {
            if (avatarsLayout.childCount >= 2) return

            val ctx = itemView.context
            val icon = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48)
                setImageResource(R.drawable.ic_person)
                setPadding(8, 8, 8, 8)
            }
            avatarsLayout.addView(icon)

            val countView = TextView(ctx).apply {
                setTextColor(ContextCompat.getColor(ctx, R.color.grey_light))
                textSize = 12f
                setPadding(8, 0, 0, 0)
            }
            avatarsLayout.addView(countView)
        }
    }

    data class Status(val receivable: Double, val debt: Double)

    companion object {
        private const val PAYLOAD_TOTAL = "payload_total"
        private const val PAYLOAD_STATUS = "payload_status"

        private fun sameMembers(old: BudgetItem, new: BudgetItem): Boolean =
            old.members.toSet() == new.members.toSet()

        val DIFF = object : DiffUtil.ItemCallback<BudgetItem>() {
            override fun areItemsTheSame(oldItem: BudgetItem, newItem: BudgetItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: BudgetItem, newItem: BudgetItem): Boolean =
                oldItem.name == newItem.name &&
                        oldItem.preferredCurrency == newItem.preferredCurrency &&
                        sameMembers(oldItem, newItem) &&
                        oldItem.ownerId == newItem.ownerId
        }
    }
}
