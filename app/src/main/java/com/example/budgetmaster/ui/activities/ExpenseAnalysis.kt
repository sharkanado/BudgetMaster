package com.example.budgetmaster.ui.activities

import ExpenseListItem
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.components.CustomPieChartView
import com.example.budgetmaster.ui.components.ExpensesAdapter
import com.example.budgetmaster.utils.Categories
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class ExpenseAnalysis : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var typeSpinner: AutoCompleteTextView
    private lateinit var yearSpinner: AutoCompleteTextView
    private lateinit var monthSpinner: AutoCompleteTextView
    private lateinit var categorySpinner: AutoCompleteTextView
    private lateinit var pieChart: CustomPieChartView
    private lateinit var totalSpentText: TextView
    private lateinit var averageSpentText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgressBar: View
    private lateinit var fabNewExpense: FloatingActionButton

    private lateinit var expensesAdapter: ExpensesAdapter

    private var selectedYear = LocalDate.now().year
    private var selectedMonth =
        LocalDate.now().month.name.lowercase().replaceFirstChar { it.uppercase() }
    private var selectedCategory: String? = null
    private var selectedType = "Expense"

    // (display item with ORIGINAL string, SIGNED amount in MAIN currency for math)
    private var cachedEntries: List<Pair<ExpenseListItem.Item, Double>> = emptyList()
    private var cachedPieData: List<CustomPieChartView.PieEntry> = emptyList()

    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private val df2 by lazy { DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)) }

    // Currency view state — SAME as in MyWallet
    private var mainCurrency: String = "PLN"
    private var eurRatesLatest: Map<String, Double> = emptyMap() // EUR -> CODE
    private var eurToMainRate: Double = 1.0                      // EUR -> main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_expense_analysis)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        intent.getIntExtra("selectedYear", -1).let { if (it != -1) selectedYear = it }
        intent.getStringExtra("selectedMonth")?.let { selectedMonth = it }

        typeSpinner = findViewById(R.id.typeSpinner)
        yearSpinner = findViewById(R.id.yearSpinner)
        monthSpinner = findViewById(R.id.monthSpinner)
        categorySpinner = findViewById(R.id.categorySpinner)
        pieChart = findViewById(R.id.pieChart)
        totalSpentText = findViewById(R.id.totalSpentText)
        averageSpentText = findViewById(R.id.averageSpentText)
        recyclerView = findViewById(R.id.expensesRecyclerView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        fabNewExpense = findViewById(R.id.addExpenseFab)

        setupTypeSpinner()
        setupYearSpinner()
        setupMonthSpinner()
        setupCategorySpinner()

        recyclerView.layoutManager = LinearLayoutManager(this)
        expensesAdapter = ExpensesAdapter(emptyList(), currencyCode = mainCurrency) { clickedItem ->
            val intent = Intent(this, ExpenseDetailsWallet::class.java)
            intent.putExtra("selectedYear", selectedYear)
            intent.putExtra("selectedMonth", selectedMonth)
            intent.putExtra("expenseItem", clickedItem)
            intent.putExtra("expenseId", clickedItem.id)
            startActivity(intent)
        }
        recyclerView.adapter = expensesAdapter

        fabNewExpense.setOnClickListener { startActivity(Intent(this, AddExpense::class.java)) }

        refreshCurrencyAndRatesThenLoad()
    }

    override fun onResume() {
        super.onResume()
        refreshCurrencyAndRatesThenLoad()
    }

    private fun setupTypeSpinner() {
        val types = listOf("Expense", "Income")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, types)
        typeSpinner.setAdapter(adapter)
        typeSpinner.setText(selectedType, false)
        typeSpinner.setOnItemClickListener { _, _, position, _ ->
            selectedType = types[position]
            refreshCurrencyAndRatesThenLoad()
        }
    }

    private fun setupYearSpinner() {
        val currentYear = LocalDate.now().year
        val years = (currentYear - 5..currentYear + 5).map { it.toString() }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, years)
        yearSpinner.setAdapter(adapter)
        yearSpinner.setText(selectedYear.toString(), false)
        yearSpinner.setOnItemClickListener { _, _, position, _ ->
            selectedYear = years[position].toInt()
            refreshCurrencyAndRatesThenLoad()
        }
    }

    private fun setupMonthSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, months)
        monthSpinner.setAdapter(adapter)
        months.indexOf(selectedMonth).takeIf { it != -1 }?.let {
            monthSpinner.setText(months[it], false)
        }
        monthSpinner.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = months[position]
            refreshCurrencyAndRatesThenLoad()
        }
    }

    private fun setupCategorySpinner() {
        val categories = listOf("All") + Categories.categoryList
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, categories)
        categorySpinner.setAdapter(adapter)
        categorySpinner.setText(selectedCategory ?: "All", false)
        categorySpinner.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = if (position == 0) null else categories[position]
            pieChart.setData(cachedPieData, selectedCategory)
            updateRecyclerView(selectedCategory)
            computeAverageMonthlySum()
        }
    }

    /** EXACTLY like MyWallet: load user's mainCurrency, fetch latest EUR rates, then load data */
    private fun refreshCurrencyAndRatesThenLoad() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                mainCurrency = (doc.getString("mainCurrency") ?: "PLN").uppercase()
                expensesAdapter.updateCurrency(mainCurrency)
            }
            .addOnFailureListener {
                mainCurrency = "PLN"
                expensesAdapter.updateCurrency(mainCurrency)
            }
            .addOnCompleteListener {
                MainScope().launch {
                    eurRatesLatest =
                        withContext(Dispatchers.IO) { fetchEurRatesLatest() } ?: emptyMap()
                    eurToMainRate =
                        if (mainCurrency.equals("EUR", true)) 1.0 else eurRatesLatest[mainCurrency]
                            ?: 1.0
                    loadMonthData()
                    computeAverageMonthlySum()
                }
            }
    }

    private fun loadMonthData() {
        val uid = auth.currentUser?.uid ?: return

        loadingProgressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE

        db.collection("users")
            .document(uid)
            .collection("expenses")
            .document(selectedYear.toString())
            .collection(selectedMonth)
            .get()
            .addOnSuccessListener { result ->
                loadingProgressBar.visibility = View.GONE

                if (result.isEmpty) {
                    recyclerView.visibility = View.GONE
                    pieChart.setData(emptyList(), null)
                    totalSpentText.text = "0.00 $mainCurrency"
                    averageSpentText.text = "0.00 $mainCurrency"
                    cachedEntries = emptyList()
                    cachedPieData = emptyList()
                    return@addOnSuccessListener
                }

                // Pie data in MAIN currency (unsigned per category, filtered by selectedType & selectedCategory)
                val allCategoryTotals = result.documents
                    .asSequence()
                    .filter { it.getString("type")?.equals(selectedType, true) == true }
                    .filter {
                        selectedCategory == null || (it.getString("category")
                            ?: "Other") == selectedCategory
                    }
                    .groupBy { it.getString("category") ?: "Other" }
                    .map { (cat, items) ->
                        val sumMainUnsigned = items.sumOf { amountInMainUnsigned(it) ?: 0.0 }
                        CustomPieChartView.PieEntry(sumMainUnsigned, cat)
                    }
                    .sortedByDescending { it.value }

                cachedPieData = allCategoryTotals
                pieChart.setData(cachedPieData, selectedCategory)

                // Build items: ORIGINAL display only + SIGNED MAIN for headers/total
                cachedEntries = result.documents.mapNotNull { doc ->
                    val type = (doc.getString("type") ?: "expense").lowercase(Locale.ENGLISH)
                    if (!type.equals(selectedType, true)) return@mapNotNull null
                    if (selectedCategory != null && (doc.getString("category")
                            ?: "Other") != selectedCategory
                    ) return@mapNotNull null

                    val category = doc.getString("category") ?: "Other"
                    val description = doc.getString("description") ?: ""
                    val dateStr = doc.getString("date") ?: ""

                    // ORIGINAL display (what user typed)
                    val curOrig = (doc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
                    val amountOrig = readAmount(doc.get("amount"))
                    val signedOrig = if (type == "expense") -amountOrig else amountOrig
                    val displayAmount = "${df2.format(signedOrig)} $curOrig"

                    // SIGNED MAIN for headers/totals
                    val unsignedMain = amountInMainUnsigned(doc) ?: 0.0
                    val signedMain = if (type == "expense") -unsignedMain else unsignedMain

                    val item = ExpenseListItem.Item(
                        iconResId = Categories.getIcon(category),
                        name = description,
                        budgetId = "null", expenseIdInBudget = "null",
                        category = category,
                        amount = displayAmount, // ORIGINAL ONLY
                        date = dateStr,
                        type = type,
                        id = doc.id
                    )
                    Pair(item, signedMain)
                }

                updateRecyclerView(selectedCategory)
            }
            .addOnFailureListener {
                loadingProgressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                totalSpentText.text = "0.00 $mainCurrency"
                averageSpentText.text = "0.00 $mainCurrency"
            }
    }

    private fun computeAverageMonthlySum() {
        val uid = auth.currentUser?.uid ?: return
        val yearRef = db.collection("users").document(uid)
            .collection("expenses").document(selectedYear.toString())

        var completed = 0
        var sumAcrossMonthsMain = 0.0
        var monthsWithData = 0

        months.forEach { monthName ->
            yearRef.collection(monthName)
                .get()
                .addOnSuccessListener { docs ->
                    val monthTotalMainUnsigned = docs.documents
                        .asSequence()
                        .filter { it.getString("type")?.equals(selectedType, true) == true }
                        .filter {
                            selectedCategory == null || (it.getString("category")
                                ?: "Other") == selectedCategory
                        }
                        .sumOf { amountInMainUnsigned(it) ?: 0.0 }
                    if (monthTotalMainUnsigned != 0.0) {
                        sumAcrossMonthsMain += monthTotalMainUnsigned
                        monthsWithData++
                    }
                }
                .addOnCompleteListener {
                    completed++
                    if (completed == 12) {
                        val avg =
                            if (monthsWithData == 0) 0.0 else sumAcrossMonthsMain / monthsWithData
                        averageSpentText.text = "${df2.format(avg)} $mainCurrency"
                    }
                }
        }
    }

    private fun updateRecyclerView(category: String?) {
        val filtered =
            if (category == null) cachedEntries else cachedEntries.filter { it.first.category == category }

        val totalMainSigned = filtered.sumOf { it.second }
        totalSpentText.text = "${df2.format(totalMainSigned)} $mainCurrency"

        val grouped = filtered.groupBy { it.first.date }
            .toSortedMap(compareByDescending { LocalDate.parse(it) })

        val listItems = mutableListOf<ExpenseListItem>()
        val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

        for ((date, pairs) in grouped) {
            val dayTotalMainSigned = pairs.sumOf { it.second }
            listItems.add(
                ExpenseListItem.Header(
                    LocalDate.parse(date).format(dayFmt),
                    df2.format(dayTotalMainSigned), // adapter appends currency code
                    isPositive = dayTotalMainSigned >= 0
                )
            )
            listItems.addAll(pairs.map { it.first }) // ORIGINAL values per item
        }

        expensesAdapter.updateItems(listItems)
        recyclerView.visibility = if (listItems.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---------- SAME conversions as in MyWallet ----------

    /** Amount in MAIN currency, unsigned; with "no-recalc-if-same-currency" rule. */
    private fun amountInMainUnsigned(doc: DocumentSnapshot): Double? {
        val cur = (doc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
        val amountOrig = readAmount(doc.get("amount"))
        if (cur == mainCurrency.uppercase(Locale.ENGLISH)) {
            // Already in display currency → do NOT convert.
            return abs(amountOrig)
        }
        // Prefer stored amountBase (EUR), else compute via EUR snapshot
        val amountBase = (doc.get("amountBase") as? Number)?.toDouble()
            ?: run {
                if (cur.equals("EUR", true)) amountOrig
                else {
                    val eurToCur = eurRatesLatest[cur] ?: return null
                    val curToEur = 1.0 / eurToCur
                    amountOrig * curToEur
                }
            }
        return abs(amountBase * eurToMainRate)
    }

    private fun readAmount(raw: Any?): Double = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    /** Fetch a single latest EUR snapshot (EUR -> CODE map). */
    private fun fetchEurRatesLatest(): Map<String, Double>? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.frankfurter.dev/v1/latest?from=EUR")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val rates = json.optJSONObject("rates") ?: return emptyMap()
            val out = mutableMapOf<String, Double>()
            val keys = rates.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k.uppercase(Locale.ENGLISH)] = rates.getDouble(k)
            }
            out
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
