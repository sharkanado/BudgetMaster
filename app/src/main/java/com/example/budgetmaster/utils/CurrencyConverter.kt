package com.example.budgetmaster.fx

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.round

class CurrencyConverter(
    private val fx: FxClient,
    private val baseCurrency: String = "EUR"
) {
    private val df2 = DecimalFormat("0.00", DecimalFormatSymbols(Locale.US))

    // Convert base(EUR) → target using EUR->target from the snapshot
    fun baseToTarget(amountBase: Double, target: String, snapshot: FxSnapshot): Double {
        if (target.equals(baseCurrency, true)) return amountBase
        val rate = snapshot.rates[target.uppercase()] ?: return Double.NaN
        return round2(amountBase * rate)
    }

    fun format(amount: Double, code: String, approx: Boolean = false): String {
        val prefix = if (approx) "≈ " else ""
        return "$prefix${df2.format(amount)} $code"
    }

    private fun round2(v: Double) = round(v * 100.0) / 100.0

    suspend fun convertForDisplay(
        amountBase: Double,
        target: String,
        asOf: String? = null,   // pass expense date for historical, or null for latest
    ): DisplayAmount? {
        val snap = fx.getRatesEUR(asOf) ?: return null
        val value = baseToTarget(amountBase, target, snap)
        if (value.isNaN()) return null
        return DisplayAmount(value, target.uppercase(), snap.asOf)
    }
}

data class DisplayAmount(
    val value: Double,
    val currency: String,
    val asOf: String
)
