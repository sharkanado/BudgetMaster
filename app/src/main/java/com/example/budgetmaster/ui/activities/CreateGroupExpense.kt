package com.example.budgetmaster.ui.activities

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.components.BudgetMemberItem
import com.example.budgetmaster.ui.components.BudgetMembersAdapter
import com.example.budgetmaster.utils.updateLatestExpenses
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

class CreateGroupExpense : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var budgetId: String
    private lateinit var budgetName: String

    private lateinit var membersRecycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var membersAdapter: BudgetMembersAdapter
    private val membersList = mutableListOf<BudgetMemberItem>()

    // Track selected members (for "who did you pay for?")
    private val selectedMembers = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_group_expense)

        val root = findViewById<View>(R.id.main)

        // Apply insets dynamically
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get budget data from Intent
        budgetId = intent.getStringExtra("budgetId") ?: run {
            Toast.makeText(this, "No budget provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        budgetName = intent.getStringExtra("budgetName") ?: "Unknown Budget"

        // Init members Recycler
        membersRecycler = findViewById(R.id.membersRecycler)
        membersRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)

        // Load members for this budget
        loadBudgetMembers()

        // Handle save button
        findViewById<View>(R.id.saveExpenseBtn).setOnClickListener {
            saveGroupExpense()
        }
    }

    private fun loadBudgetMembers() {
        // Fetch members from budget doc
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->
                val memberIds = doc.get("members") as? List<String> ?: emptyList()
                budgetName = doc.getString("name") ?: budgetName

                if (memberIds.isEmpty()) return@addOnSuccessListener

                var processed = 0
                membersList.clear()

                for (uid in memberIds) {
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val member = BudgetMemberItem(
                                    uid = uid,
                                    name = userDoc.getString("name") ?: "Unknown",
                                    email = userDoc.getString("email") ?: "",
                                    balance = 0.0 // Placeholder for now
                                )
                                membersList.add(member)
                            }
                        }
                        .addOnCompleteListener {
                            processed++
                            if (processed == memberIds.size) {
                                // For now, select all by default
                                selectedMembers.addAll(memberIds)
                                membersAdapter = BudgetMembersAdapter(membersList)
                                membersRecycler.adapter = membersAdapter
                            }
                        }
                }
            }
    }

    private fun saveGroupExpense() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val uid = currentUser?.uid ?: run {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Gather fields
        val descriptionInput = findViewById<TextInputEditText>(R.id.descriptionInput)
        val amountInput = findViewById<TextInputEditText>(R.id.amountInput)
        val addPrivateCheck = findViewById<CheckBox>(R.id.addToPrivateWalletCheckbox)

        val description = descriptionInput.text?.toString()?.trim()
        val amount = amountInput.text?.toString()?.replace(",", ".")?.toDoubleOrNull()

        if (description.isNullOrEmpty() || amount == null || amount <= 0.0) {
            Toast.makeText(this, "Please enter valid data", Toast.LENGTH_SHORT).show()
            return
        }

        // Prepare date info
        val date = LocalDate.now()
        val year = date.year.toString()
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

        // Expense object
        val expenseData = hashMapOf(
            "amount" to amount,
            "description" to description,
            "date" to date.toString(),
            "timestamp" to Timestamp.now(),
            "type" to "expense",
            "createdBy" to uid,
            "paidFor" to selectedMembers.toList(),
            "budgetName" to budgetName
        )

        // Save to group expenses
        db.collection("budgets").document(budgetId)
            .collection("expenses").document(year)
            .collection(month)
            .add(expenseData)
            .addOnSuccessListener {
                Toast.makeText(this, "Expense added to group", Toast.LENGTH_SHORT).show()

                // If checkbox is checked, also add to private wallet
                if (addPrivateCheck.isChecked) {
                    addToPrivateWallet(uid, expenseData)
                } else {
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addToPrivateWallet(uid: String, expenseData: HashMap<String, Any>) {
        val date = LocalDate.parse(expenseData["date"] as String)
        val year = date.year.toString()
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

        // Override budgetName for private wallet
        expenseData["budgetName"] = "personal"

        db.collection("users").document(uid)
            .collection("expenses").document(year)
            .collection(month)
            .add(expenseData)
            .addOnSuccessListener {
                updateLatestExpenses(uid, expenseData)
                Toast.makeText(this, "Also added to private wallet", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Private wallet add failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }
}
