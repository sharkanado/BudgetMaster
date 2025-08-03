package com.example.budgetmaster.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetExpenseItem
import com.example.budgetmaster.ui.budgets.BudgetItem
import com.example.budgetmaster.ui.components.BudgetExpensesAdapter
import com.example.budgetmaster.ui.components.BudgetMemberItem
import com.example.budgetmaster.ui.components.BudgetMembersAdapter
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

class BudgetDetails : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var budget: BudgetItem

    private val membersList = mutableListOf<BudgetMemberItem>()
    private lateinit var membersAdapter: BudgetMembersAdapter

    private val expensesList = mutableListOf<BudgetExpenseItem>()
    private lateinit var expensesAdapter: BudgetExpensesAdapter

    // Cache for accordion expand/collapse
    private val monthExpenseMap = mutableMapOf<String, List<BudgetExpenseItem>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_details)

        // Apply system bar insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get BudgetItem from intent
        budget = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra("budget", BudgetItem::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra("budget")
        } ?: run {
            Toast.makeText(this, "No budget provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Setup members RecyclerView
        val membersRecycler = findViewById<RecyclerView>(R.id.membersRecycler)
        membersAdapter = BudgetMembersAdapter(membersList)
        membersRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        membersRecycler.adapter = membersAdapter

        // Setup expenses RecyclerView (accordion)
        val expensesRecycler = findViewById<RecyclerView>(R.id.accordionRecycler)
        expensesAdapter = BudgetExpensesAdapter(expensesList) { headerPosition ->
            toggleAccordion(headerPosition)
        }
        expensesRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        expensesRecycler.adapter = expensesAdapter

        // Load members
        loadMembers()

        // Add new expense button
        val newExpenseBtn = findViewById<Button>(R.id.newExpenseBtn)
        newExpenseBtn.setOnClickListener {
            val intent = Intent(this, CreateGroupExpense::class.java)
            intent.putExtra("budgetId", budget.id)
            intent.putExtra("budgetName", budget.name)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh expenses when returning from CreateGroupExpense
        loadExpenses()
    }

    /** Load and group expenses by "Month Year" */
    private fun loadExpenses() {
        expensesList.clear()
        monthExpenseMap.clear()

        db.collection("budgets").document(budget.id)
            .collection("expenses")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    expensesAdapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                // Group by month-year
                val grouped = snapshot.documents.groupBy { doc ->
                    val dateStr = doc.getString("date") ?: ""
                    parseMonthYear(dateStr) // e.g., "June 2025"
                }

                // Sort keys descending (latest first)
                val sortedKeys = grouped.keys.sortedByDescending { key ->
                    val parts = key.split(" ")
                    if (parts.size == 2) {
                        val month = monthToNumber(parts[0])
                        val year = parts[1].toIntOrNull() ?: 0
                        year * 100 + month
                    } else 0
                }

                // Build accordion data
                for (monthKey in sortedKeys) {
                    val monthExpenses = grouped[monthKey]!!.map { doc ->
                        BudgetExpenseItem(
                            id = doc.id,
                            amount = doc.getDouble("amount") ?: 0.0,
                            description = doc.getString("description") ?: "",
                            date = doc.getString("date") ?: "",
                            createdBy = doc.getString("createdBy") ?: "",
                            paidFor = doc.get("paidFor") as? List<String> ?: emptyList(),
                            budgetName = doc.getString("budgetName") ?: ""
                        )
                    }

                    monthExpenseMap[monthKey] = monthExpenses
                    addMonthHeader(monthKey, monthExpenses)
                }
            }
    }

    /** Add header + children to the adapter list */
    private fun addMonthHeader(monthYear: String, expenses: List<BudgetExpenseItem>) {
        val headerItem = BudgetExpenseItem(
            id = "header_$monthYear",
            amount = 0.0,
            description = monthYear,
            isHeader = true,
            isExpanded = true
        )

        expensesList.add(headerItem)
        expensesList.addAll(expenses)
        expensesAdapter.notifyDataSetChanged()
    }

    /** Toggle expand/collapse for accordion sections */
    private fun toggleAccordion(headerPosition: Int) {
        val header = expensesList[headerPosition]
        if (!header.isHeader) return

        header.isExpanded = !header.isExpanded

        if (!header.isExpanded) {
            // Collapse: remove children until next header
            val toRemove = mutableListOf<BudgetExpenseItem>()
            var i = headerPosition + 1
            while (i < expensesList.size && !expensesList[i].isHeader) {
                toRemove.add(expensesList[i])
                i++
            }
            expensesList.removeAll(toRemove)
        } else {
            // Expand: reinsert children from cache
            val monthKey = header.description
            val children = monthExpenseMap[monthKey] ?: return
            expensesList.addAll(headerPosition + 1, children)
        }

        expensesAdapter.notifyDataSetChanged()
    }

    /** Load members horizontally */
    private fun loadMembers() {
        if (budget.members.isEmpty()) return

        membersList.clear()
        var processed = 0

        for (uid in budget.members) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val member = BudgetMemberItem(
                            uid = uid,
                            name = doc.getString("name") ?: "Unknown",
                            email = doc.getString("email") ?: "",
                            balance = 0.0
                        )
                        membersList.add(member)
                    }
                }
                .addOnCompleteListener {
                    processed++
                    if (processed == budget.members.size) {
                        membersAdapter.notifyDataSetChanged()
                    }
                }
        }
    }

    /** Parse "yyyy-MM-dd" → "MonthName Year" (e.g., 2025-06-10 → June 2025) */
    private fun parseMonthYear(dateStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            val date = sdf.parse(dateStr)
            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
            monthFormat.format(date!!)
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /** Convert month name (e.g., June) → number (6) for sorting */
    private fun monthToNumber(month: String): Int {
        val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )
        return months.indexOf(month) + 1
    }
}
