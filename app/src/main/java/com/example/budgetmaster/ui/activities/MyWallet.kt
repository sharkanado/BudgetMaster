package com.example.budgetmaster.ui.activities

import ExpenseListItem
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
import com.example.budgetmaster.ui.components.ExpensesAdapter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        if (currentMonthIndex != -1) {
            monthDropdown.setText(months[currentMonthIndex], false)
        }

        monthDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = months[position]
            barChart.highlightMonth(position)
            loadExpenses()
        }

        expensesAdapter = ExpensesAdapter(emptyList()) { clickedItem ->
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
            selectedYear--
            yearButton.text = selectedYear.toString()
            loadExpenses()
        }
        nextYearBtn.setOnClickListener {
            selectedYear++
            yearButton.text = selectedYear.toString()
            loadExpenses()
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
        loadExpenses()
    }

    private fun loadExpenses() {
        val uid = auth.currentUser?.uid ?: return

        val recycler = findViewById<RecyclerView>(R.id.expensesRecyclerView)
        val noDataText = findViewById<TextView>(R.id.noDataText)
        val balanceValue = findViewById<TextView>(R.id.balanceValue)
        val monthlyAvgValue = findViewById<TextView>(R.id.monthlyAvgValue)

        val monthlyIncome = MutableList(12) { 0f }
        val monthlyExpenses = MutableList(12) { 0f }
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
                        val amount = readAmount(doc.get("amount")).toFloat()
                        val type = doc.getString("type") ?: "expense"
                        if (type == "expense") monthlyExpenses[index] += amount
                        else monthlyIncome[index] += amount
                    }
                }
                .addOnCompleteListener {
                    monthsCompleted++
                    if (monthsCompleted == 12) {
                        barChart.setData(monthlyIncome, monthlyExpenses)
                        val currentIndex = months.indexOf(selectedMonth)
                        if (currentIndex != -1) barChart.highlightMonth(currentIndex)
                        val incomeSum = monthlyIncome.sum()
                        val expenseSum = monthlyExpenses.sum()
                        val net = incomeSum - expenseSum
                        val monthsWithData =
                            (0 until 12).count { monthlyIncome[it] != 0f || monthlyExpenses[it] != 0f }
                                .let { if (it == 0) 1 else it }
                        val avg = net / monthsWithData
                        balanceValue.text = df2.format(net)
                        monthlyAvgValue.text = df2.format(avg)
                    }
                }
        }

        yearRef.collection(selectedMonth)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    recycler.visibility = View.GONE
                    noDataText.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                val rows = result.documents.mapNotNull { doc ->
                    val dateStr = doc.getString("date") ?: return@mapNotNull null
                    val parsedDate = LocalDate.parse(dateStr)
                    val name = doc.getString("description") ?: ""
                    val category = doc.getString("category") ?: ""
                    val type = doc.getString("type") ?: "expense"
                    val amountVal = readAmount(doc.get("amount"))
                    val signedAmount = if (type == "expense") -amountVal else amountVal
                    val budgetId = doc.getString("budgetId") ?: ""
                    val expenseIdInBudget = doc.getString("expenseIdInBudget") ?: ""
                    val ts = (doc.get("timestamp") as? Timestamp)?.toDate()?.time ?: 0L
                    ExpenseDetailsWithId(
                        date = parsedDate,
                        name = name,
                        category = category,
                        amount = signedAmount,
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
                    val total = sortedEntries.sumOf { it.amount }
                    val label = df2.format(total)
                    listItems.add(
                        ExpenseListItem.Header(
                            date = date.format(formatted),
                            total = label,
                            isPositive = total >= 0
                        )
                    )
                    sortedEntries.forEach { row ->
                        val displayAmount = df2.format(row.amount)
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
                noDataText.visibility = View.GONE
            }
    }

    private data class ExpenseDetailsWithId(
        val date: LocalDate,
        val name: String,
        val category: String,
        val amount: Double,
        val type: String,
        val id: String,
        val budgetId: String,
        val expenseIdInBudget: String,
        val timestampMs: Long
    )

    private fun readAmount(raw: Any?): Double = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}
