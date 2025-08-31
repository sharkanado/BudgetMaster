package com.example.budgetmaster.utils

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BudgetItem(
    val id: String = "",
    val name: String = "",
    val preferredCurrency: String = "",
    val members: List<String> = emptyList(),
    val ownerId: String = "",
    val balance: Double = 0.0
) : Parcelable
