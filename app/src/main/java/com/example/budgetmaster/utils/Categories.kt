package com.example.budgetmaster.utils

import android.graphics.Color

object Categories {

    val categoryList = listOf(
        "Food & Groceries",
        "Transport",
        "Entertainment",
        "Bills & Utilities",
        "Health & Fitness",
        "Shopping & Clothing",
        "Education",
        "Savings",
        "Investment",
        "Salary",
        "Gift & Donations",
        "Travel",
        "Rent & Housing",
        "Insurance",
        "Taxes",
        "Pets",
        "Other"
    )

    val categoryColors: Map<String, Int> = mapOf(
        "Food & Groceries" to Color.parseColor("#FFA726"),      // Orange
        "Transport" to Color.parseColor("#66BB6A"),             // Green
        "Entertainment" to Color.parseColor("#29B6F6"),         // Blue
        "Bills & Utilities" to Color.parseColor("#AB47BC"),     // Purple
        "Health & Fitness" to Color.parseColor("#EF5350"),      // Red
        "Shopping & Clothing" to Color.parseColor("#FFCA28"),   // Yellow
        "Education" to Color.parseColor("#26C6DA"),             // Cyan
        "Savings" to Color.parseColor("#8D6E63"),               // Brown
        "Investment" to Color.parseColor("#42A5F5"),            // Light Blue
        "Salary" to Color.parseColor("#7E57C2"),                // Deep Purple
        "Gift & Donations" to Color.parseColor("#EC407A"),      // Pink
        "Travel" to Color.parseColor("#26A69A"),                // Teal
        "Rent & Housing" to Color.parseColor("#5C6BC0"),        // Indigo
        "Insurance" to Color.parseColor("#FF7043"),             // Deep Orange
        "Taxes" to Color.parseColor("#9CCC65"),                 // Light Green
        "Pets" to Color.parseColor("#A1887F"),                  // Warm Brown
        "Other" to Color.parseColor("#9E9E9E")                  // Grey
    )

    fun getColor(category: String): Int {
        return categoryColors[category] ?: Color.GRAY
    }
}
