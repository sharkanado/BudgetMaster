package com.example.budgetmaster.ui.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.components.CustomBarChartView
import com.example.budgetmaster.utils.ExpenseListItem
import com.example.budgetmaster.utils.ExpensesAdapter
import com.google.firebase.Timestamp
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

class MyWallet : AppCompatActivity() {

    private lateinit var expensesAdapter: ExpensesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var selectedYear = LocalDate.now().year
    private var selectedMonth =
        LocalDate.now().month.name.lowercase().replaceFirstChar { it.uppercase() }

    private lateinit var barChart: CustomBarChartView
    private lateinit var monthDropdown: AutoCompleteTextView

    private val df2 by lazy { DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)) }

    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    private var mainCurrency: String = "PLN"
    private var eurRatesLatest: Map<String, Double> = emptyMap()
    private var eurToMainRate: Double = 1.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_wallet)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val yearButton = findViewById<Button>(R.id.yearButton)
        val prevYearBtn = findViewById<ImageButton>(R.id.prevYearBtn)
        val nextYearBtn = findViewById<ImageButton>(R.id.nextYearBtn)
        barChart = findViewById(R.id.barChart)
        monthDropdown = findViewById(R.id.monthSpinner)

        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, months)
        monthDropdown.setAdapter(monthAdapter)

        val currentMonthIndex = months.indexOf(selectedMonth)
        if (currentMonthIndex != -1) monthDropdown.setText(months[currentMonthIndex], false)

        monthDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = months[position]
            barChart.highlightMonth(position)
            loadExpenses()
        }

        expensesAdapter = ExpensesAdapter(emptyList(), currencyCode = mainCurrency) { clickedItem ->
            val intent = Intent(this, ExpenseDetailsWallet::class.java)
            intent.putExtra("selectedYear", selectedYear)
            intent.putExtra("selectedMonth", selectedMonth)
            intent.putExtra("expenseItem", clickedItem)
            intent.putExtra("expenseId", clickedItem.id)
            startActivity(intent)
        }
        findViewById<RecyclerView>(R.id.expensesRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@MyWallet)
            adapter = expensesAdapter
        }

        yearButton.text = selectedYear.toString()
        prevYearBtn.setOnClickListener {
            selectedYear--; yearButton.text = selectedYear.toString(); loadExpenses()
        }
        nextYearBtn.setOnClickListener {
            selectedYear++; yearButton.text = selectedYear.toString(); loadExpenses()
        }

        barChart.setOnMonthClickListener { monthIndex ->
            selectedMonth = months[monthIndex]
            monthDropdown.setText(months[monthIndex], false)
            barChart.highlightMonth(monthIndex)
            loadExpenses()
        }

        findViewById<Button>(R.id.seeAnalysisButton).setOnClickListener {
            val intent = Intent(this, ExpenseAnalysis::class.java)
            intent.putExtra("selectedYear", selectedYear)
            intent.putExtra("selectedMonth", selectedMonth)
            startActivity(intent)
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addExpenseFab)
            .setOnClickListener { startActivity(Intent(this, AddExpense::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        refreshCurrencyAndRatesThenLoad()
    }

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
                    loadExpenses()
                }
            }
    }

    private fun loadExpenses() {
        val uid = auth.currentUser?.uid ?: return

        val recycler = findViewById<RecyclerView>(R.id.expensesRecyclerView)
        val balanceValue = findViewById<TextView>(R.id.balanceValue)
        val monthlyAvgValue = findViewById<TextView>(R.id.monthlyAvgValue)

        val monthlyIncomeMain = MutableList(12) { 0.0 }
        val monthlyExpensesMain = MutableList(12) { 0.0 }
        var monthsCompleted = 0

        val yearRef = db.collection("users")
            .document(uid)
            .collection("expenses")
            .document(selectedYear.toString())

        months.forEachIndexed { index, monthName ->
            yearRef.collection(monthName)
                .get()
                .addOnSuccessListener { docs ->
                    docs.forEach { doc ->
                        val type = (doc.getString("type") ?: "expense").lowercase(Locale.ENGLISH)
                        val amountUnsignedMain = amountInMainUnsigned(doc) ?: return@forEach
                        if (type == "expense") monthlyExpensesMain[index] += amountUnsignedMain
                        else monthlyIncomeMain[index] += amountUnsignedMain
                    }
                }
                .addOnCompleteListener {
                    monthsCompleted++
                    if (monthsCompleted == 12) {
                        val monthlyIncome = monthlyIncomeMain.map { it.toFloat() }
                        val monthlyExpenses = monthlyExpensesMain.map { it.toFloat() }

                        barChart.setData(
                            monthlyIncome.toMutableList(),
                            monthlyExpenses.toMutableList()
                        )
                        val currentIndex = months.indexOf(selectedMonth)
                        if (currentIndex != -1) barChart.highlightMonth(currentIndex)

                        val incomeSum = monthlyIncomeMain.sum()
                        val expenseSum = monthlyExpensesMain.sum()
                        val net = incomeSum - expenseSum
                        val monthsWithExpenses = monthlyExpenses.count { it != 0f }
                        val avgMonthlyExpenses =
                            if (monthsWithExpenses == 0) 0.0 else expenseSum / monthsWithExpenses

                        balanceValue.text = "${df2.format(net)} $mainCurrency"
                        monthlyAvgValue.text = "${df2.format(avgMonthlyExpenses)} $mainCurrency"
                    }
                }
        }

        yearRef.collection(selectedMonth)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    recycler.visibility = View.GONE
                    return@addOnSuccessListener
                }

                val rows = result.documents.mapNotNull { doc ->
                    val dateStr = doc.getString("date") ?: return@mapNotNull null
                    val parsedDate = LocalDate.parse(dateStr)
                    val name = doc.getString("description") ?: ""
                    val category = doc.getString("category") ?: ""
                    val type = (doc.getString("type") ?: "expense").lowercase(Locale.ENGLISH)

                    val curOrig = (doc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
                    val amountOrig = readAmount(doc.get("amount"))
                    val signedOrig = if (type == "expense") -amountOrig else amountOrig

                    val amountUnsignedMain = amountInMainUnsigned(doc) ?: 0.0
                    val signedMain =
                        if (type == "expense") -amountUnsignedMain else amountUnsignedMain

                    val budgetId = doc.getString("budgetId") ?: ""
                    val expenseIdInBudget = doc.getString("expenseIdInBudget") ?: ""
                    val ts = (doc.get("timestamp") as? Timestamp)?.toDate()?.time ?: 0L

                    ExpenseDetailsWithId(
                        date = parsedDate,
                        name = name,
                        category = category,
                        amountSignedMain = signedMain,
                        amountSignedOrig = signedOrig,
                        currencyOrig = curOrig,
                        type = type,
                        id = doc.id,
                        budgetId = budgetId,
                        expenseIdInBudget = expenseIdInBudget,
                        timestampMs = ts
                    )
                }.groupBy { it.date }
                    .toSortedMap(compareByDescending { it })

                val listItems = mutableListOf<ExpenseListItem>()
                val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

                for ((date, entries) in rows) {
                    val sortedEntries = entries.sortedByDescending { it.timestampMs }

                    val totalMain = sortedEntries.sumOf { it.amountSignedMain }
                    val headerLabel = df2.format(totalMain)
                    listItems.add(
                        ExpenseListItem.Header(
                            date = date.format(formatted),
                            total = headerLabel,
                            isPositive = totalMain >= 0
                        )
                    )

                    sortedEntries.forEach { row ->
                        val displayAmount =
                            "${df2.format(row.amountSignedOrig)} ${row.currencyOrig}"

                        listItems.add(
                            ExpenseListItem.Item(
                                iconResId = R.drawable.ic_home_white_24dp,
                                name = row.name,
                                budgetId = row.budgetId,
                                expenseIdInBudget = row.expenseIdInBudget,
                                category = row.category,
                                amount = displayAmount,
                                date = row.date.toString(),
                                type = row.type,
                                id = row.id
                            )
                        )
                    }
                }

                expensesAdapter.updateItems(listItems)
                recycler.visibility = View.VISIBLE
            }
    }

    private data class ExpenseDetailsWithId(
        val date: LocalDate,
        val name: String,
        val category: String,
        val amountSignedMain: Double,
        val amountSignedOrig: Double,
        val currencyOrig: String,
        val type: String,
        val id: String,
        val budgetId: String,
        val expenseIdInBudget: String,
        val timestampMs: Long
    )

    private fun amountInMainUnsigned(doc: DocumentSnapshot): Double? {
        val cur = (doc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
        val amountOrig = readAmount(doc.get("amount"))
        if (cur == mainCurrency.uppercase(Locale.ENGLISH)) {
            return abs(amountOrig)
        }

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
