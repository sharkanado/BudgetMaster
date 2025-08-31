package com.example.budgetmaster.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

class BudgetMembersAdapter(
    private val members: List<BudgetMemberItem>,
    private var spentByUser: Map<String, Double> = emptyMap(),
    private var currencyCode: String = ""
) : RecyclerView.Adapter<BudgetMembersAdapter.MemberViewHolder>() {

    private val df = DecimalFormat("0.00").apply {
        decimalFormatSymbols = DecimalFormatSymbols(Locale.ENGLISH).apply {
            decimalSeparator = '.'
            groupingSeparator = ' '
        }
        isGroupingUsed = false
    }

    fun setSpentByUser(map: Map<String, Double>) {
        spentByUser = map
        notifyDataSetChanged()
    }

    fun updateCurrency(code: String) {
        currencyCode = code.trim().uppercase(Locale.ENGLISH)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget_member_balance_tile, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        val value = spentByUser[member.uid] ?: member.balance

        holder.nameText.text = member.name
        holder.emailText.text = member.email
        holder.balanceText.text =
            if (currencyCode.isBlank()) df.format(value) else "${df.format(value)} $currencyCode"

        val ctx = holder.itemView.context
        val colorRes = when {
            value > 0.0 -> R.color.orange
            value < 0.0 -> R.color.red_error
            else -> R.color.grey_light
        }
        holder.balanceText.setTextColor(ContextCompat.getColor(ctx, colorRes))
    }

    override fun getItemCount(): Int = members.size

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.memberName)
        val emailText: TextView = itemView.findViewById(R.id.memberEmail)
        val balanceText: TextView = itemView.findViewById(R.id.memberBalance)
    }
}
