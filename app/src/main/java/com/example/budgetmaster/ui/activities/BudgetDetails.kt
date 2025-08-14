package com.example.budgetmaster.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
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
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class BudgetDetails : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var budget: BudgetItem

    private val membersList = mutableListOf<BudgetMemberItem>()
    private lateinit var membersAdapter: BudgetMembersAdapter

    private val expensesList = mutableListOf<BudgetExpenseItem>()
    private lateinit var expensesAdapter: BudgetExpensesAdapter

    // title ("August 2025") -> sorted items within that month (newest→oldest)
    private val monthExpenseMap = mutableMapOf<String, List<BudgetExpenseItem>>()
    private val userNames = mutableMapOf<String, String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_details)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

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

        // Set budget name
        findViewById<TextView>(R.id.budgetNameText).text = budget.name

        val membersRecycler = findViewById<RecyclerView>(R.id.membersRecycler)
        membersAdapter = BudgetMembersAdapter(membersList)
        membersRecycler.layoutManager = LinearLayoutManager(this)
        membersRecycler.adapter = membersAdapter

        val expensesRecycler = findViewById<RecyclerView>(R.id.accordionRecycler)
        expensesAdapter = BudgetExpensesAdapter(
            expensesList,
            userNames,
            onHeaderClick = { headerPosition -> toggleAccordion(headerPosition) },
            onExpenseClick = { expenseItem ->
                val intent = Intent(this, BudgetExpenseDetails::class.java)
                intent.putExtra("expenseItem", expenseItem) // Pass the whole object
                intent.putExtra("budgetId", budget.id)
                startActivity(intent)
            }
        )
        expensesRecycler.layoutManager = LinearLayoutManager(this)
        expensesRecycler.adapter = expensesAdapter

        findViewById<MaterialButton>(R.id.newExpenseBtn).setOnClickListener {
            val intent = Intent(this, CreateGroupExpense::class.java)
            intent.putExtra("budgetId", budget.id)
            intent.putExtra("budgetName", budget.name)
            startActivity(intent)
        }

        findViewById<MaterialButton>(R.id.settleUpBtn).setOnClickListener {
            val intent = Intent(this, GroupSettlement::class.java)
            intent.putExtra("budgetId", budget.id)
            startActivity(intent)
        }

        findViewById<ImageButton>(R.id.settingsBtn).setOnClickListener {
            val intent = Intent(this, EditBudget::class.java)
            intent.putExtra("budgetId", budget.id)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        refreshData()
    }

    private fun refreshData() {
        loadMembers()
        loadExpenses()
    }

    private fun loadExpenses() {
        expensesList.clear()
        monthExpenseMap.clear()
        expensesAdapter.notifyDataSetChanged()

        db.collection("budgets").document(budget.id)
            .collection("expenses")
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // No expenses -> also clear spentByUser in members list
                    membersAdapter.setSpentByUser(emptyMap())
                    expensesAdapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                // 1) Build raw rows
                val allRows: List<BudgetExpenseItem> = snapshot.documents.map { doc ->
                    BudgetExpenseItem(
                        id = doc.id,
                        amount = readAmount(doc),
                        description = doc.getString("description") ?: "",
                        date = doc.getString("date") ?: "", // expected "yyyy-MM-dd"
                        createdBy = doc.getString("createdBy") ?: "",
                        paidFor = readStringList(doc.get("paidFor")),
                    )
                }

                // >>> FEED "spent by user" (uid -> total spent) INTO MEMBERS ADAPTER <<<
                val spentByUser: Map<String, Double> = allRows
                    .filter { it.createdBy.isNotBlank() }
                    .groupBy { it.createdBy }
                    .mapValues { (_, items) -> items.sumOf { it.amount } }

                membersAdapter.setSpentByUser(spentByUser)

                // 2) Group by month key "yyyy-MM"
                val groupedByMonthKey = allRows.groupBy { it.date.take(7) } // safe on short strings

                // 3) Sort months newest → oldest by key (string works for yyyy-MM)
                val sortedMonthKeys = groupedByMonthKey.keys
                    .filter { it.length == 7 }
                    .sortedDescending()

                // 4) Rebuild UI list with headers and with rows inside each month sorted by date desc
                expensesList.clear()
                monthExpenseMap.clear()

                for (monthKey in sortedMonthKeys) {
                    val monthTitle = monthKeyToTitle(monthKey) // e.g., "August 2025"

                    val monthItems = groupedByMonthKey[monthKey].orEmpty()
                        .sortedByDescending { it.date } // "yyyy-MM-dd" sorts correctly as string

                    monthExpenseMap[monthTitle] = monthItems

                    // Add header
                    val headerItem = BudgetExpenseItem(
                        id = "header_$monthTitle",
                        amount = 0.0,
                        description = monthTitle,
                        isHeader = true,
                        isExpanded = true
                    )
                    expensesList.add(headerItem)

                    // Add children (expanded by default)
                    expensesList.addAll(monthItems)
                }

                expensesAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load expenses: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun toggleAccordion(headerPosition: Int) {
        val header = expensesList[headerPosition]
        if (!header.isHeader) return

        header.isExpanded = !header.isExpanded

        if (!header.isExpanded) {
            val toRemove = mutableListOf<BudgetExpenseItem>()
            var i = headerPosition + 1
            while (i < expensesList.size && !expensesList[i].isHeader) {
                toRemove.add(expensesList[i])
                i++
            }
            expensesList.removeAll(toRemove)
        } else {
            val monthKey = header.description // this is the title like "August 2025"
            val children = monthExpenseMap[monthKey] ?: return
            // children are already sorted by date desc
            expensesList.addAll(headerPosition + 1, children)
        }

        expensesAdapter.notifyDataSetChanged()
    }

    private fun loadMembers() {
        membersList.clear()
        userNames.clear()
        membersAdapter.notifyDataSetChanged()

        if (budget.members.isEmpty()) return

        var processed = 0
        for (uid in budget.members) {
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val name = doc.getString("name") ?: "Unknown"
                        val member = BudgetMemberItem(
                            uid = uid,
                            name = name,
                            email = doc.getString("email") ?: "",
                            balance = 0.0
                        )
                        membersList.add(member)
                        userNames[uid] = name
                    }
                }
                .addOnCompleteListener {
                    processed++
                    if (processed == budget.members.size) {
                        membersAdapter.notifyDataSetChanged()
                        expensesAdapter.notifyDataSetChanged() // refresh names in expense rows
                    }
                }
        }
    }

    // ===== Helpers =====

    private fun monthKeyToTitle(key: String): String {
        // key: "yyyy-MM"
        return try {
            val (yearStr, monthStr) = key.split("-")
            val year = yearStr.toInt()
            val month = monthStr.toInt() // 1..12
            val monthName = java.text.DateFormatSymbols(Locale.ENGLISH).months[month - 1]
            "$monthName $year"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    private fun readAmount(doc: DocumentSnapshot): Double {
        val raw = doc.get("amount")
        return when (raw) {
            is Number -> raw.toDouble()
            is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readStringList(raw: Any?): List<String> = when (raw) {
        is List<*> -> raw.mapNotNull { it?.toString() }
        else -> emptyList()
    }
}
