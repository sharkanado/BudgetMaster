package com.example.budgetmaster.ui.activities

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.components.BudgetMemberItem
import com.example.budgetmaster.ui.components.BudgetMembersAdapter
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class EditBudget : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var budgetId: String
    private lateinit var budgetNameEdit: EditText
    private lateinit var membersRecycler: RecyclerView
    private lateinit var membersContainer: LinearLayout
    private lateinit var addMemberBtn: MaterialButton
    private lateinit var saveButton: MaterialButton

    private val currentMembers = mutableListOf<BudgetMemberItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_edit_budget)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get passed budget ID
        budgetId = intent.getStringExtra("budgetId") ?: run {
            Toast.makeText(this, "No budget ID", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Initialize views
        budgetNameEdit = findViewById(R.id.budgetNameEdit)
        membersRecycler = findViewById(R.id.membersRecycler)
        membersContainer = findViewById(R.id.membersContainer)
        addMemberBtn = findViewById(R.id.addMemberBtn)
        saveButton = findViewById(R.id.saveButton)

        membersRecycler.layoutManager = LinearLayoutManager(this)

        addMemberBtn.setOnClickListener { addNewMemberField() }
        saveButton.setOnClickListener { updateBudget() }

        loadBudgetData()
    }

    /** Load existing budget name + members **/
    private fun loadBudgetData() {
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: ""
                val memberIds = doc.get("members") as? List<String> ?: emptyList()

                budgetNameEdit.setText(name)

                if (memberIds.isEmpty()) return@addOnSuccessListener

                var loaded = 0
                for (uid in memberIds) {
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                currentMembers.add(
                                    BudgetMemberItem(
                                        uid = uid,
                                        name = userDoc.getString("name") ?: "Unknown",
                                        email = userDoc.getString("email") ?: "",
                                        balance = 0.0
                                    )
                                )
                            } else {
                                Log.e("EditBudget", "User doc not found for UID: $uid")
                            }
                        }
                        .addOnCompleteListener {
                            loaded++
                            if (loaded == memberIds.size) {
                                membersRecycler.adapter = BudgetMembersAdapter(currentMembers)
                            }
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load budget", Toast.LENGTH_SHORT).show()
            }
    }

    /** Add dynamic input field for new member email **/
    private fun addNewMemberField() {
        val view = LayoutInflater.from(this)
            .inflate(R.layout.item_member_input, membersContainer, false)

        val removeBtn = view.findViewById<ImageView>(R.id.removeMemberBtn)
        removeBtn.setOnClickListener {
            membersContainer.removeView(view)
        }

        membersContainer.addView(view)
    }

    /** Save changes to budget **/
    private fun updateBudget() {
        val newName = budgetNameEdit.text?.toString()?.trim()
        if (newName.isNullOrEmpty()) {
            Toast.makeText(this, "Enter budget name", Toast.LENGTH_SHORT).show()
            return
        }

        // Collect new member emails
        val emails = mutableListOf<String>()
        for (i in 0 until membersContainer.childCount) {
            val view = membersContainer.getChildAt(i)
            val input = view.findViewById<EditText>(R.id.memberEmailInput)
            val email = input.text?.toString()?.trim()
            if (!email.isNullOrEmpty()) emails.add(email)
        }

        // Resolve new emails to UIDs
        resolveEmailsToUids(emails) { newUids ->
            val currentUids = currentMembers.map { it.uid }
            val allMembers = (currentUids + newUids).distinct()

            // Update budget doc
            db.collection("budgets").document(budgetId)
                .update(
                    mapOf(
                        "name" to newName,
                        "members" to allMembers
                    )
                )
                .addOnSuccessListener {
                    // Add budget to new members' budgetsAccessed
                    updateBudgetsAccessedForMembers(newUids, budgetId)

                    Toast.makeText(this, "Budget updated!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    /** Convert emails to UIDs via usersByEmail mapping **/
    private fun resolveEmailsToUids(emails: List<String>, callback: (List<String>) -> Unit) {
        if (emails.isEmpty()) {
            callback(emptyList())
            return
        }

        val result = mutableListOf<String>()
        var count = 0

        for (email in emails) {
            db.collection("usersByEmail").document(email).get()
                .addOnSuccessListener { doc ->
                    val uid = doc.getString("uid")
                    if (uid != null) {
                        Log.d("EditBudget", "Resolved email $email to UID $uid")
                        result.add(uid)
                    } else {
                        Log.e("EditBudget", "No UID found for email: $email")
                    }
                }
                .addOnFailureListener {
                    Log.e("EditBudget", "Failed to fetch UID for email: $email")
                }
                .addOnCompleteListener {
                    count++
                    if (count == emails.size) callback(result)
                }
        }
    }

    /** Add budgetId to budgetsAccessed for each new member **/
    private fun updateBudgetsAccessedForMembers(memberIds: List<String>, budgetId: String) {
        if (memberIds.isEmpty()) return

        for (uid in memberIds) {
            val userRef = db.collection("users").document(uid)
            userRef.get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    userRef.update("budgetsAccessed", FieldValue.arrayUnion(budgetId))
                        .addOnSuccessListener {
                            Log.d("EditBudget", "Added $budgetId to budgetsAccessed for $uid")
                        }
                        .addOnFailureListener { e ->
                            Log.e(
                                "EditBudget",
                                "Failed to update budgetsAccessed for $uid: ${e.message}"
                            )
                        }
                } else {
                    Log.e("EditBudget", "User not found: $uid")
                }
            }.addOnFailureListener { e ->
                Log.e("EditBudget", "Error checking user $uid: ${e.message}")
            }
        }
    }
}
