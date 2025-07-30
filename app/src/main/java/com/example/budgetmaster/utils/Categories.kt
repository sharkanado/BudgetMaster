package com.example.budgetmaster.utils

import android.graphics.Color
import com.example.budgetmaster.R

object Categories {

    val categoryList = listOf(
        "Food & Groceries",
        "Transport",
        "Entertainment",
        "Bills & Utilities",
        "Health",
        "Shopping & Clothing",
        "Education",
        "Savings",
        "Investment",
        "Salary",
        "Gift & Donations",
        "Travel",
        "Rent & Housing",
        "Insurance",
        "Pets",
        "Other",
        "Sport"
    )

    val categoryColors: Map<String, Int> = mapOf(
        "Food & Groceries" to Color.parseColor("#FFA726"),
        "Transport" to Color.parseColor("#66BB6A"),
        "Entertainment" to Color.parseColor("#29B6F6"),
        "Bills & Utilities" to Color.parseColor("#AB47BC"),
        "Health" to Color.parseColor("#EF5350"),
        "Shopping & Clothing" to Color.parseColor("#FFCA28"),
        "Education" to Color.parseColor("#26C6DA"),
        "Savings" to Color.parseColor("#8D6E63"),
        "Investment" to Color.parseColor("#42A5F5"),
        "Salary" to Color.parseColor("#7E57C2"),
        "Gift & Donations" to Color.parseColor("#EC407A"),
        "Travel" to Color.parseColor("#26A69A"),
        "Rent & Housing" to Color.parseColor("#5C6BC0"),
        "Insurance" to Color.parseColor("#FF7043"),
        "Pets" to Color.parseColor("#A1887F"),
        "Other" to Color.parseColor("#9E9E9E"),
        "Sport" to Color.parseColor("#fcba03")
    )

    // Map categories to icon drawables
    val categoryIcons: Map<String, Int> = mapOf(
        "Food & Groceries" to R.drawable.ic_food,
        "Transport" to R.drawable.ic_transport,
        "Entertainment" to R.drawable.ic_entertainment,
        "Bills & Utilities" to R.drawable.ic_bills,
        "Health" to R.drawable.ic_health,
        "Shopping & Clothing" to R.drawable.ic_shopping,
        "Education" to R.drawable.ic_education,
        "Savings" to R.drawable.ic_savings,
        "Investment" to R.drawable.ic_investment,
        "Salary" to R.drawable.ic_salary,
        "Gift & Donations" to R.drawable.ic_gift,
        "Travel" to R.drawable.ic_travel,
        "Rent & Housing" to R.drawable.ic_rent,
        "Insurance" to R.drawable.ic_insurance,
        "Pets" to R.drawable.ic_pets,
        "Other" to R.drawable.ic_other,
        "Sport" to R.drawable.ic_sport
    )

    fun getColor(category: String): Int {
        return categoryColors[category] ?: Color.GRAY
    }

    fun getIcon(category: String): Int {
        return categoryIcons[category] ?: R.drawable.ic_other
    }
}
