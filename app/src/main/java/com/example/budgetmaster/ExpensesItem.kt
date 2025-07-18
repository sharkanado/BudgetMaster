package com.example.budgetmaster

sealed class ExpenseListItem {
    data class Header(val date: String, val total: String) : ExpenseListItem()
    data class Item(
        val iconResId: Int,
        val name: String,
        val budget: String,
        val amount: String
    ) : ExpenseListItem()
}
