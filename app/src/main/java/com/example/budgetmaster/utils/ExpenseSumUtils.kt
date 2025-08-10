package com.example.budgetmaster.utils

import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

object ExpenseSumUtils {

    private val MONTHS_EN = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    data class YearTotals(val expense: Double, val income: Double) {
        val net: Double get() = income - expense
    }

    // ---------- PUBLIC API ----------

    /** Sum a single month under users/{uid}/expenses/{year}/{month}. */
    suspend fun sumMonthAmount(
        db: FirebaseFirestore,
        uid: String,
        year: Int,
        month: String,
        type: String? = null,                // "expense" or "income"
        preferAggregate: Boolean = true,
        fallbackClient: Boolean = true
    ): Double {
        val colRef = db.collection("users").document(uid)
            .collection("expenses").document(year.toString())
            .collection(month)

        val q: Query = if (type.isNullOrBlank()) colRef else colRef.whereEqualTo("type", type)

        if (preferAggregate) {
            try {
                val aggSnap = q.aggregate(AggregateField.sum("amount"))
                    .get(AggregateSource.SERVER)
                    .await()
                val sumAny = aggSnap.get(AggregateField.sum("amount"))
                val v = (sumAny as? Number)?.toDouble()
                if (v != null) return v
            } catch (e: Exception) {
                if (!fallbackClient) throw e
            }
        }

        // Fallback: client-side sum (also covers legacy string "amount")
        val snap = colRef.get().await()
        var total = 0.0
        for (doc in snap.documents) {
            if (!type.isNullOrBlank()) {
                val t = (doc.getString("type") ?: "").lowercase()
                if (t != type.lowercase()) continue
            }
            total += readAmount(doc.get("amount"))
        }
        return total
    }

    /**
     * Sum a whole year by summing ONLY months that actually contain documents.
     * Optional type filter ("expense" or "income").
     */
    suspend fun sumYearAmount(
        db: FirebaseFirestore,
        uid: String,
        year: Int,
        type: String? = null
    ): Double = coroutineScope {
        val monthsWithDocs = listExistingMonths(db, uid, year)
        monthsWithDocs.map { month ->
            async { sumMonthAmount(db, uid, year, month, type) }
        }.awaitAll().sum()
    }

    /** Get both totals (expense & income) for a year (only existing months considered). */
    suspend fun sumYearTotals(
        db: FirebaseFirestore,
        uid: String,
        year: Int
    ): YearTotals = coroutineScope {
        val expenseDef = async { sumYearAmount(db, uid, year, type = "expense") }
        val incomeDef = async { sumYearAmount(db, uid, year, type = "income") }
        YearTotals(expense = expenseDef.await(), income = incomeDef.await())
    }

    /**
     * Sum expense+income totals for every given year (or discover them automatically).
     * Returns map: year -> YearTotals.
     */
    suspend fun sumAllYearsTotals(
        db: FirebaseFirestore,
        uid: String,
        years: List<Int>? = null
    ): Map<Int, YearTotals> = coroutineScope {
        val yrs = years ?: listYearsSmart(db, uid)
        val pairs = yrs.map { y -> async { y to sumYearTotals(db, uid, y) } }.awaitAll()
        pairs.toMap()
    }

    /** List years (fast when year docs exist; otherwise falls back to latest/probe). */
    suspend fun listYearsSmart(
        db: FirebaseFirestore,
        uid: String,
        probeBackYears: Int = 2 // probe this many years back if we can't detect from docs/latest
    ): List<Int> {
        // 1) Direct year docs
        val direct = listYearsDirect(db, uid)
        if (direct.isNotEmpty()) return direct

        // 2) From 'latest' (by parsing yyyy-MM-dd)
        val fromLatest = listYearsFromLatest(db, uid)
        if (fromLatest.isNotEmpty()) return fromLatest

        // 3) Probe: current year and a few prior years; keep any that have any month with docs
        val nowY = LocalDate.now().year
        val candidates = (nowY - probeBackYears..nowY).toList()
        val existing = mutableListOf<Int>()
        for (y in candidates) {
            if (hasAnyDocsInYear(db, uid, y)) existing += y
        }
        return existing.ifEmpty { listOf(nowY) } // worst case: just try current year
    }

    // ---------- INTERNAL HELPERS ----------

    /** Try to list year documents under users/{uid}/expenses. Works if you set a sentinel. */
    private suspend fun listYearsDirect(db: FirebaseFirestore, uid: String): List<Int> {
        val snap = db.collection("users").document(uid)
            .collection("expenses")
            .get()
            .await()
        return snap.documents.mapNotNull { it.id.toIntOrNull() }.sorted()
    }

    /** Derive years from users/{uid}/latest's "date" (yyyy-MM-dd). */
    private suspend fun listYearsFromLatest(db: FirebaseFirestore, uid: String): List<Int> {
        val latestSnap = db.collection("users").document(uid)
            .collection("latest")
            .get()
            .await()
        return latestSnap.documents.mapNotNull { doc ->
            val dateStr = doc.getString("date") ?: return@mapNotNull null
            dateStr.takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
        }.distinct().sorted()
    }

    /** Check if at least one month collection under {year} has any doc (very cheap: limit(1)). */
    private suspend fun hasAnyDocsInYear(
        db: FirebaseFirestore,
        uid: String,
        year: Int
    ): Boolean {
        val checks = MONTHS_EN.map { month ->
            asyncHasAnyDoc(db, uid, year, month)
        }
        // Run sequentially to keep reads minimal; or parallel if you prefer speed.
        for (task in checks) if (task) return true
        return false
    }

    /** List months that actually have at least 1 doc (uses limit(1) per month). */
    private suspend fun listExistingMonths(
        db: FirebaseFirestore,
        uid: String,
        year: Int
    ): List<String> {
        val existing = mutableListOf<String>()
        for (month in MONTHS_EN) {
            if (asyncHasAnyDoc(db, uid, year, month)) existing += month
        }
        return existing
    }

    /** Very cheap existence check: returns true if the month has at least one doc. */
    private suspend fun asyncHasAnyDoc(
        db: FirebaseFirestore,
        uid: String,
        year: Int,
        month: String
    ): Boolean {
        val snap = db.collection("users").document(uid)
            .collection("expenses").document(year.toString())
            .collection(month)
            .limit(1)
            .get()
            .await()
        return !snap.isEmpty
    }

    private fun readAmount(raw: Any?): Double = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}
