package com.example.budgetmaster.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import java.util.Locale

class BudgetMembersAdapter(
    private val members: List<BudgetMemberItem>,
    private var spentByUser: Map<String, Double> = emptyMap() // uid -> total spent
) : RecyclerView.Adapter<BudgetMembersAdapter.MemberViewHolder>() {

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.memberName)
        val emailText: TextView = itemView.findViewById(R.id.memberEmail)
        val balanceText: TextView = itemView.findViewById(R.id.memberBalance)
    }

    /** Update totals (uid -> spent) after you load expenses, then refresh UI */
    fun setSpentByUser(map: Map<String, Double>) {
        spentByUser = map
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget_member_balance_tile, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        val spent = spentByUser[member.uid] ?: 0.0

        holder.nameText.text = member.name
        holder.emailText.text = member.email
        holder.balanceText.text = formatAmount(spent)


    }

    override fun getItemCount(): Int = members.size

    private fun formatAmount(value: Double): String =
        String.format(Locale.ENGLISH, "%.2f", value)
}
