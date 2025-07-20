package com.example.budgetmaster.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.components.ExpenseListItem
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
        val monthLabel = findViewById<TextView>(R.id.monthLabel)

        expensesAdapter = ExpensesAdapter(emptyList())
        val recycler = findViewById<RecyclerView>(R.id.expensesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = expensesAdapter

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

        // Placeholder logic to simulate selecting a different month (toggle on chart image click)
        val barChart = findViewById<ImageView>(R.id.barChart)
        barChart.setOnClickListener {
            // Cycle through months for demo purposes
            selectedMonth = when (selectedMonth) {
                "June" -> "July"
                "July" -> "August"
                "August" -> "September"
                else -> "June"
            }
            monthLabel.text = "$selectedMonth $selectedYear"
            loadExpenses()
        }

        findViewById<Button>(R.id.seeAnalysisButton).setOnClickListener {
//            startActivity(Intent(this, AnalysisActivity::class.java)) // Future screen
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.addExpenseFab).setOnClickListener {
            startActivity(Intent(this, AddExpense::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadExpenses()
    }

    private fun loadExpenses() {
        val uid = auth.currentUser?.uid ?: return

        findViewById<TextView>(R.id.monthLabel).text = "$selectedMonth $selectedYear"

        db.collection("users")
            .document(uid)
            .collection("expenses")
            .document(selectedYear.toString())
            .collection(selectedMonth)
            .get()
            .addOnSuccessListener { result ->
                val grouped = result.documents
                    .mapNotNull { doc ->
                        val dateStr = doc.getString("date") ?: return@mapNotNull null
                        val parsedDate = LocalDate.parse(dateStr)
                        val name = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val amount = doc.getDouble("amount") ?: 0.0
                        val currency = doc.getString("currency") ?: "PLN"
                        Triple(
                            parsedDate,
                            name,
                            Pair(category, String.format("%.2f", amount) + " $currency")
                        )
                    }
                    .groupBy { it.first }
                    .toSortedMap(compareByDescending { it })

                val listItems = mutableListOf<ExpenseListItem>()
                val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

                for ((date, entries) in grouped) {
                    val dailyTotal = entries.sumOf { (_, _, amountPair) ->
                        amountPair.second.split(" ")[0].replace(",", ".").toDoubleOrNull() ?: 0.0
                    }
                    val currency =
                        entries.firstOrNull()?.third?.second?.split(" ")?.getOrNull(1) ?: "PLN"
                    val dailyLabel = "-${"%.2f".format(dailyTotal)} $currency"

                    listItems.add(ExpenseListItem.Header(date.format(formatted), dailyLabel))

                    entries.forEach { (_, name, amountPair) ->
                        listItems.add(
                            ExpenseListItem.Item(
                                R.drawable.ic_home_white_24dp,
                                name,
                                amountPair.first,
                                "-${amountPair.second}"
                            )
                        )
                    }
                }

                expensesAdapter.updateItems(listItems)
            }
    }
}
