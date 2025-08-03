package com.example.budgetmaster.ui.budgets

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BudgetExpenseItem(
    val id: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val date: String = "",
    val createdBy: String = "",
    val paidFor: List<String> = emptyList(),
    val budgetName: String = "",
    val isHeader: Boolean = false,
    var isExpanded: Boolean = false
) : Parcelable
