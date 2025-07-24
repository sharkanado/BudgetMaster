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

        // Handle window insets
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

        // --- Setup month dropdown ---
        val monthAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            months
        )
        monthDropdown.setAdapter(monthAdapter)

        // Preselect current month
        val currentMonthIndex = months.indexOf(selectedMonth)
        if (currentMonthIndex != -1) {
            monthDropdown.setText(months[currentMonthIndex], false)
        }

        // Handle dropdown selection changes
        monthDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = months[position]
            barChart.highlightMonth(position)
            loadExpenses()
        }

        // RecyclerView setup
        expensesAdapter = ExpensesAdapter(emptyList()) { clickedItem ->
            val intent = Intent(this, ExpenseDetailsWallet::class.java)
            intent.putExtra("selectedYear", selectedYear)
            intent.putExtra("selectedMonth", selectedMonth)
            intent.putExtra("expense_item", clickedItem)
            startActivity(intent)
        }

        val recycler = findViewById<RecyclerView>(R.id.expensesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = expensesAdapter

        yearButton.text = selectedYear.toString()

        // Year navigation
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

        // Bar chart month click
        barChart.setOnMonthClickListener { monthIndex ->
            selectedMonth = months[monthIndex]
            monthDropdown.setText(months[monthIndex], false)
            barChart.highlightMonth(monthIndex)
            loadExpenses()
        }

        findViewById<Button>(R.id.seeAnalysisButton).setOnClickListener {
            // TODO: Implement navigation to analysis screen
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addExpenseFab)
            .setOnClickListener {
                startActivity(Intent(this, AddExpense::class.java))
            }
    }

    override fun onResume() {
        super.onResume()
        loadExpenses()
    }

    private fun loadExpenses() {
        val uid = auth.currentUser?.uid ?: return

        val recycler = findViewById<RecyclerView>(R.id.expensesRecyclerView)
        val noDataText = findViewById<TextView>(R.id.noDataText)

        // Initialize monthly totals for the chart
        val monthlyIncome = MutableList(12) { 0f }
        val monthlyExpenses = MutableList(12) { 0f }

        // Reference to yearly node
        val yearRef = db.collection("users")
            .document(uid)
            .collection("expenses")
            .document(selectedYear.toString())

        // 1) Load yearly data for chart
        months.forEachIndexed { index, monthName ->
            yearRef.collection(monthName)
                .get()
                .addOnSuccessListener { docs ->
                    docs.forEach { doc ->
                        val amount = doc.getDouble("amount")?.toFloat() ?: 0f
                        val type = doc.getString("type") ?: "expense"
                        if (type == "expense") {
                            monthlyExpenses[index] += amount
                        } else {
                            monthlyIncome[index] += amount
                        }
                    }

                    // Update chart after each month fetch
                    barChart.setData(monthlyIncome, monthlyExpenses)

                    // Highlight currently selected month
                    val currentIndex = months.indexOf(selectedMonth)
                    if (currentIndex != -1) {
                        barChart.highlightMonth(currentIndex)
                    }
                }
        }

        // 2) Load selected month for RecyclerView list
        yearRef.collection(selectedMonth)
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    recycler.visibility = View.GONE
                    noDataText.visibility = View.VISIBLE
                } else {
                    val grouped = result.documents
                        .mapNotNull { doc ->
                            val dateStr = doc.getString("date") ?: return@mapNotNull null
                            val parsedDate = LocalDate.parse(dateStr)
                            val name = doc.getString("description") ?: ""
                            val category = doc.getString("category") ?: ""
                            val amount = doc.getDouble("amount") ?: 0.0
                            val type = doc.getString("type") ?: "expense"
                            val signedAmount = if (type == "expense") -amount else amount
                            ExpenseDetailsWithId(
                                parsedDate,
                                name,
                                category,
                                signedAmount,
                                type,
                                doc.id
                            )
                        }
                        .groupBy { it.date }
                        .toSortedMap(compareByDescending { it })

                    val listItems = mutableListOf<ExpenseListItem>()
                    val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

                    for ((date, entries) in grouped) {
                        val total = entries.sumOf { it.amount }
                        val label = "%.2f".format(total)
                        listItems.add(
                            ExpenseListItem.Header(
                                date.format(formatted),
                                label,
                                total >= 0
                            )
                        )

                        entries.forEach { (date, name, category, amount, type, id) ->
                            val displayAmount = "%.2f".format(amount)
                            listItems.add(
                                ExpenseListItem.Item(
                                    R.drawable.ic_home_white_24dp,
                                    name,
                                    category,
                                    displayAmount,
                                    date.toString(),
                                    type,
                                    id
                                )
                            )
                        }
                    }

                    expensesAdapter.updateItems(listItems)
                    recycler.visibility = View.VISIBLE
                    noDataText.visibility = View.GONE
                }
            }
    }

    private data class ExpenseDetailsWithId(
        val date: LocalDate,
        val name: String,
        val category: String,
        val amount: Double,
        val type: String,
        val id: String
    )
}
