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
import com.example.budgetmaster.ui.budgets.BudgetItem
import com.example.budgetmaster.ui.components.BudgetMemberItem
import com.example.budgetmaster.ui.components.BudgetMembersAdapter
import com.google.firebase.firestore.FirebaseFirestore

class BudgetDetails : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var budget: BudgetItem
    private val membersList = mutableListOf<BudgetMemberItem>()
    private lateinit var membersAdapter: BudgetMembersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_details)


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- Get passed BudgetItem safely ---
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

        // Setup Recycler
        val membersRecycler = findViewById<RecyclerView>(R.id.membersRecycler)
        membersAdapter = BudgetMembersAdapter(membersList)
        membersRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        membersRecycler.adapter = membersAdapter

        // Load members from Firestore
        loadMembers()

        // Handle new expense button
        val newExpenseBtn = findViewById<Button>(R.id.newExpenseBtn)
        newExpenseBtn.setOnClickListener {
            val intent = Intent(this, CreateGroupExpense::class.java)
            intent.putExtra("budgetId", budget.id)
            startActivity(intent)
        }
    }

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
                            balance = 0.0 // Placeholder until implemented
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
