package com.example.budgetmaster.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetItem
import com.example.budgetmaster.ui.components.BudgetMemberItem
import com.example.budgetmaster.ui.components.BudgetMembersAdapter
import com.google.firebase.firestore.FirebaseFirestore

class BudgetDetails : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var budget: BudgetItem
    private lateinit var membersRecycler: RecyclerView
    private lateinit var newExpenseBtn: Button

    private val membersList = mutableListOf<BudgetMemberItem>()
    private lateinit var membersAdapter: BudgetMembersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_budget_details)

        // Initialize views
        membersRecycler = findViewById(R.id.membersRecycler)
        newExpenseBtn = findViewById(R.id.newExpenseBtn)

        // Get passed budget
        budget = intent.getParcelableExtra("budget")
            ?: run {
                Toast.makeText(this, "No budget provided", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        setupRecycler()
        setupNewExpenseButton()
        loadMembers()
    }

    /** Setup horizontal members RecyclerView **/
    private fun setupRecycler() {
        membersAdapter = BudgetMembersAdapter(membersList)
        membersRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        membersRecycler.adapter = membersAdapter
    }

    /** Handle Add New Expense button click **/
    private fun setupNewExpenseButton() {
        newExpenseBtn.setOnClickListener {
            val intent =
                Intent(this, AddExpense::class.java) // or AddNewExpense if that's your file
            intent.putExtra("budgetId", budget.id) // Pass budget ID
            startActivity(intent)
        }
    }

    /** Load members from Firestore **/
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
                            balance = 0.0 // Placeholder
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
}
