package com.example.budgetmaster.fx

import com.google.firebase.firestore.DocumentSnapshot

data class ExpenseCore(
    val amount: Double,           // original
    val currency: String,         // original code
    val amountBase: Double?,      // EUR if present
    val fxAsOf: String?           // date used for original->EUR (optional)
)

fun DocumentSnapshot.readExpenseCore(): ExpenseCore {
    val amount = when (val raw = get("amount")) {
        is Number -> raw.toDouble()
        is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
    val currency = (getString("currency") ?: "EUR").uppercase()
    val amountBase = (get("amountBase") as? Number)?.toDouble()
    val fxAsOf = (get("fx.asOf") as? String) ?: (get("fx") as? Map<*, *>)?.get("asOf") as? String
    return ExpenseCore(amount, currency, amountBase, fxAsOf)
}
