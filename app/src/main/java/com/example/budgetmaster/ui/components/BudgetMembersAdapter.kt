package com.example.budgetmaster.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R

class BudgetMembersAdapter(
    private val members: List<BudgetMemberItem>
) : RecyclerView.Adapter<BudgetMembersAdapter.MemberViewHolder>() {

    inner class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.memberName)
        val emailText: TextView = itemView.findViewById(R.id.memberEmail)
        val balanceText: TextView = itemView.findViewById(R.id.memberBalance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget_member_tile, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.nameText.text = member.name
        holder.emailText.text = member.email
        holder.balanceText.text = "${member.balance} PLN"
    }

    override fun getItemCount(): Int = members.size
}
