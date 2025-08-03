package com.example.budgetmaster.ui.components

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R

class BudgetSelectMembersInDebtAdapter(
    private val members: List<BudgetMemberItem>,
    private val selectedMembers: MutableSet<String>,  // Shared set
    private val onSelectionChanged: (() -> Unit)? = null // Optional callback
) : RecyclerView.Adapter<BudgetSelectMembersInDebtAdapter.MemberViewHolder>() {

    inner class MemberViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.memberName)
        val checkbox: CheckBox = view.findViewById(R.id.memberCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_budget_member_checkbox, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]

        holder.name.text = member.name
        holder.checkbox.isChecked = selectedMembers.contains(member.uid)

        holder.checkbox.setOnCheckedChangeListener(null) // prevent flicker on recycle
        holder.checkbox.isChecked = selectedMembers.contains(member.uid)

        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedMembers.add(member.uid)
            } else {
                selectedMembers.remove(member.uid)
            }
            onSelectionChanged?.invoke()
        }
    }

    override fun getItemCount(): Int = members.size

    /** Toggle select/unselect all */
    fun toggleSelectAll(selectAll: Boolean) {
        if (selectAll) {
            members.forEach { selectedMembers.add(it.uid) }
        } else {
            selectedMembers.clear()
        }
        notifyDataSetChanged()
        onSelectionChanged?.invoke()
    }
}
