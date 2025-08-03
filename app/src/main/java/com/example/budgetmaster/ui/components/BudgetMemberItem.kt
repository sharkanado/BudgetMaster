package com.example.budgetmaster.ui.components

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class BudgetMemberItem(
    val uid: String,
    val name: String,
    val email: String,
    val balance: Double
) : Parcelable
