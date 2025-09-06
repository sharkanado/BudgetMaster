package com.example.budgetmaster.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BudgetExpenseItem(
    val id: String = "",
    val amount: Double = 0.0,
    val currencyCode: String = "",
    val description: String = "",
    val date: String = "",
    val createdBy: String = "",
    val paidFor: List<String> = emptyList(),
    val isHeader: Boolean = false,
    var isExpanded: Boolean = false
) : Parcelable
