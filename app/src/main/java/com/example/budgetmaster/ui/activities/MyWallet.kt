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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

        // Month dropdown
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

        // Recycler with click → details
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

        val monthlyIncome = MutableList(12) { 0f }
        val monthlyExpenses = MutableList(12) { 0f }

        val yearRef = db.collection("users")
            .document(uid)
            .collection("expenses")
            .document(selectedYear.toString())

        // Chart data
        months.forEachIndexed { index, monthName ->
            yearRef.collection(monthName)
                .get()
                .addOnSuccessListener { docs ->
                    docs.forEach { doc ->
                        val amount = doc.getDouble("amount")?.toFloat() ?: 0f
                        val type = doc.getString("type") ?: "expense"
                        if (type == "expense") monthlyExpenses[index] += amount
                        else monthlyIncome[index] += amount
                    }
                    barChart.setData(monthlyIncome, monthlyExpenses)
                    val currentIndex = months.indexOf(selectedMonth)
                    if (currentIndex != -1) barChart.highlightMonth(currentIndex)
                }
        }

        // Month list data
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

                    val amount = doc.getDouble("amount") ?: 0.0
                    val signedAmount = if (type == "expense") -amount else amount

                    // Read IDs for group propagation
                    val budgetId = doc.getString("budgetId") ?: ""
                    val expenseIdInBudget = doc.getString("expenseIdInBudget") ?: ""

                    ExpenseDetailsWithId(
                        date = parsedDate,
                        name = name,
                        category = category,
                        amount = signedAmount,
                        type = type,
                        id = doc.id,
                        budgetId = budgetId,
                        expenseIdInBudget = expenseIdInBudget
                    )
                }.groupBy { it.date }
                    .toSortedMap(compareByDescending { it })

                val listItems = mutableListOf<ExpenseListItem>()
                val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

                for ((date, entries) in rows) {
                    val total = entries.sumOf { it.amount }
                    val label = "%.2f".format(total)
                    listItems.add(
                        ExpenseListItem.Header(
                            date = date.format(formatted),
                            total = label,
                            isPositive = total >= 0
                        )
                    )

                    entries.forEach { row ->
                        val displayAmount = "%.2f".format(row.amount)
                        // IMPORTANT: use named args so budgetId/expenseIdInBudget land correctly
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
                listItems.filterIsInstance<ExpenseListItem.Item>()
                    .take(3)
                    .forEach {
                        android.util.Log.d(
                            "DEBUG_BUILD",
                            "WILL BIND bid='${it.budgetId}', eid='${it.expenseIdInBudget}'"
                        )
                    }
                listItems.filterIsInstance<ExpenseListItem.Item>().take(1).forEach {
                    android.util.Log.d("DEBUG_CLASS", "MW item class=${it::class.qualifiedName}")
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
        val expenseIdInBudget: String
    )
}
