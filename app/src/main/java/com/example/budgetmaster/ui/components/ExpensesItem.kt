package com.example.budgetmaster.ui.components

sealed class ExpenseListItem {
    data class Header(
        val date: String,
        val total: String,
        val isIncome: Boolean
    ) : ExpenseListItem()

    data class Item(
        val iconResId: Int,
        val name: String,
        val budget: String,
        val amount: String,
        val type: String,
    ) : ExpenseListItem()
}
