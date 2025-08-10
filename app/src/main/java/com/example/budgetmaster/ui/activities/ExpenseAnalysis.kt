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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class ExpenseAnalysis : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var typeSpinner: AutoCompleteTextView
    private lateinit var yearSpinner: AutoCompleteTextView
    private lateinit var monthSpinner: AutoCompleteTextView
    private lateinit var categorySpinner: AutoCompleteTextView
    private lateinit var pieChart: CustomPieChartView
    private lateinit var totalSpentText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var noDataText: TextView
    private lateinit var loadingProgressBar: View

    private lateinit var expensesAdapter: ExpensesAdapter

    private var selectedYear = LocalDate.now().year
    private var selectedMonth =
        LocalDate.now().month.name.lowercase().replaceFirstChar { it.uppercase() }
    private var selectedCategory: String? = null
    private var selectedType = "Expense"

    private var cachedEntries: List<Pair<ExpenseListItem.Item, Double>> = emptyList()
    private var cachedPieData: List<CustomPieChartView.PieEntry> = emptyList()

    private val months = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_expense_analysis)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        intent.getIntExtra("selectedYear", -1).let {
            if (it != -1) selectedYear = it
        }
        intent.getStringExtra("selectedMonth")?.let {
            selectedMonth = it
        }

        // Initialize views
        typeSpinner = findViewById(R.id.typeSpinner)
        yearSpinner = findViewById(R.id.yearSpinner)
        monthSpinner = findViewById(R.id.monthSpinner)
        categorySpinner = findViewById(R.id.categorySpinner)
        pieChart = findViewById(R.id.pieChart)
        totalSpentText = findViewById(R.id.totalSpentText)
        recyclerView = findViewById(R.id.expensesRecyclerView)
        noDataText = findViewById(R.id.noDataText)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)

        setupTypeSpinner()
        setupYearSpinner()
        setupMonthSpinner()
        setupCategorySpinner()

        recyclerView.layoutManager = LinearLayoutManager(this)

        expensesAdapter = ExpensesAdapter(emptyList()) { clickedItem ->
            val intent = Intent(this, ExpenseDetailsWallet::class.java)
            intent.putExtra("selectedYear", selectedYear)
            intent.putExtra("selectedMonth", selectedMonth)
            intent.putExtra("expenseItem", clickedItem)
            startActivity(intent)
        }
        recyclerView.adapter = expensesAdapter

        pieChart.setOnSliceClickListener(object : CustomPieChartView.OnSliceClickListener {
            override fun onSliceClick(label: String) {
                if (selectedCategory == label) {
                    selectedCategory = null
                    categorySpinner.setText("All", false)
                } else {
                    selectedCategory = label
                    categorySpinner.setText(label, false)
                }
                pieChart.setData(cachedPieData, selectedCategory)
                updateRecyclerView(selectedCategory)
            }
        })

        loadMonthData()
    }

    override fun onResume() {
        super.onResume()
        loadMonthData()
    }

    private fun setupTypeSpinner() {
        val types = listOf("Expense", "Income")
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, types)
        typeSpinner.setAdapter(adapter)
        typeSpinner.setText(selectedType, false)

        typeSpinner.setOnItemClickListener { _, _, position, _ ->
            selectedType = types[position]
            loadMonthData()
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
            loadMonthData()
        }
    }

    private fun setupMonthSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, months)
        monthSpinner.setAdapter(adapter)

        val currentMonthIndex = months.indexOf(selectedMonth)
        if (currentMonthIndex != -1) {
            monthSpinner.setText(months[currentMonthIndex], false)
        }

        monthSpinner.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = months[position]
            loadMonthData()
        }
    }

    private fun setupCategorySpinner() {
        val categories = listOf("All") + Categories.categoryList
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, categories)
        categorySpinner.setAdapter(adapter)
        categorySpinner.setText("All", false)

        categorySpinner.setOnItemClickListener { _, _, position, _ ->
            selectedCategory = if (position == 0) null else categories[position]
            pieChart.setData(cachedPieData, selectedCategory)
            updateRecyclerView(selectedCategory)
        }
    }

    private fun loadMonthData() {
        val uid = auth.currentUser?.uid ?: return

        // Show spinner
        loadingProgressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        noDataText.visibility = View.GONE

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
                    noDataText.visibility = View.VISIBLE
                    pieChart.setData(emptyList(), null)
                    totalSpentText.text = "0.00"
                    return@addOnSuccessListener
                }

                val allCategoryTotals = result.documents
                    .filter {
                        it.getString("type")?.equals(selectedType, ignoreCase = true) == true
                    }
                    .groupBy { it.getString("category") ?: "Other" }
                    .map { (cat, items) ->
                        CustomPieChartView.PieEntry(
                            items.sumOf { it.getDouble("amount") ?: 0.0 },
                            cat
                        )
                    }
                    .sortedByDescending { it.value }

                cachedPieData = allCategoryTotals
                pieChart.setData(cachedPieData, selectedCategory)

                cachedEntries = result.documents.mapNotNull { doc ->
                    val category = doc.getString("category") ?: "Other"
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: "expense"
                    val description = doc.getString("description") ?: ""
                    val dateStr = doc.getString("date") ?: ""

                    if (!type.equals(selectedType, ignoreCase = true)) return@mapNotNull null

                    val signedAmount = if (type == "expense") -amount else amount

                    val item = ExpenseListItem.Item(
                        Categories.getIcon(category),
                        description,
                        budgetId = "null", expenseIdInBudget = "null",
                        category,
                        "%.2f".format(signedAmount),
                        dateStr,
                        type,
                        doc.id
                    )

                    Pair(item, signedAmount)
                }

                updateRecyclerView(selectedCategory)
            }
            .addOnFailureListener {
                loadingProgressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                noDataText.visibility = View.VISIBLE
            }
    }

    private fun updateRecyclerView(category: String?) {
        val filteredPairs =
            if (category == null) cachedEntries else cachedEntries.filter { it.first.category == category }

        val total = filteredPairs.sumOf { it.second }
        totalSpentText.text = "${"%.2f".format(total)}"

        val grouped = filteredPairs.map { it.first }.groupBy { it.date }
            .toSortedMap(compareByDescending { LocalDate.parse(it) })

        val listItems = mutableListOf<ExpenseListItem>()
        val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

        for ((date, items) in grouped) {
            val totalForDay = filteredPairs.filter { it.first.date == date }.sumOf { it.second }
            listItems.add(
                ExpenseListItem.Header(
                    LocalDate.parse(date).format(formatted),
                    "%.2f".format(totalForDay),
                    totalForDay >= 0
                )
            )
            listItems.addAll(items)
        }

        expensesAdapter.updateItems(listItems)
        recyclerView.visibility = if (listItems.isEmpty()) View.GONE else View.VISIBLE
        noDataText.visibility = if (listItems.isEmpty()) View.VISIBLE else View.GONE
    }
}
