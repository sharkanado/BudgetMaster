package com.example.budgetmaster.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.budgetmaster.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class AddNewBudget : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    // Currency options
    private val currencyOptions = listOf("USD", "EUR", "GBP", "PLN")

    // Declare views
    private lateinit var currencySpinner: AutoCompleteTextView
    private lateinit var addMemberBtn: Button
    private lateinit var membersContainer: LinearLayout
    private lateinit var budgetNameInput: TextInputEditText
    private lateinit var saveBudgetBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_budget)

        // Initialize views
        currencySpinner = findViewById(R.id.currencySpinner)
        addMemberBtn = findViewById(R.id.addMemberBtn)
        membersContainer = findViewById(R.id.membersContainer)
        budgetNameInput = findViewById(R.id.budgetNameInput)
        saveBudgetBtn = findViewById(R.id.saveBudgetBtn)

        // Setup UI actions
        setupCurrencyDropdown()
        setupAddMemberButton()

        saveBudgetBtn.setOnClickListener {
            createBudget()
        }
    }

    /** Setup currency dropdown **/
    private fun setupCurrencyDropdown() {
        val adapter = ArrayAdapter(
            this,
            androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
            currencyOptions
        )
        currencySpinner.setAdapter(adapter)
    }

    /** Add dynamic member fields **/
    private fun setupAddMemberButton() {
        addMemberBtn.setOnClickListener {
            val newMemberView = LayoutInflater.from(this)
                .inflate(R.layout.item_member_input, membersContainer, false)

            val removeBtn = newMemberView.findViewById<ImageView>(R.id.removeMemberBtn)
            removeBtn.setOnClickListener {
                membersContainer.removeView(newMemberView)
            }

            membersContainer.addView(newMemberView)
        }
    }

    /** Create budget and save to Firestore **/
    private fun createBudget() {
        val budgetName = budgetNameInput.text.toString().trim()
        val currency = currencySpinner.text.toString().trim()

        if (budgetName.isEmpty()) {
            Toast.makeText(this, "Please enter a budget name", Toast.LENGTH_SHORT).show()
            return
        }
        if (currency.isEmpty()) {
            Toast.makeText(this, "Please select a currency", Toast.LENGTH_SHORT).show()
            return
        }

        // Collect member emails
        val memberEmails = mutableListOf<String>()
        for (i in 0 until membersContainer.childCount) {
            val memberView = membersContainer.getChildAt(i)
            val emailField = memberView.findViewById<TextInputEditText>(R.id.memberEmailInput)
            val email = emailField.text?.toString()?.trim()

            if (!email.isNullOrEmpty()) {
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    memberEmails.add(email)
                } else {
                    Toast.makeText(this, "Invalid email: $email", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        val ownerId = currentUser?.uid ?: return
        val budgetId = UUID.randomUUID().toString()

        // Resolve emails to UIDs, then save budget
        resolveEmailsToUids(memberEmails) { resolvedMemberIds ->
            // Combine owner + resolved members
            val allMembers = listOf(ownerId) + resolvedMemberIds

            val metadata = hashMapOf(
                "name" to budgetName,
                "ownerId" to ownerId,
                "members" to allMembers,
                "preferredCurrency" to currency,
                "createdAt" to com.google.firebase.Timestamp.now()
            )

            db.collection("budgets").document(budgetId)
                .set(metadata)
                .addOnSuccessListener {
                    // Update budgetsAccessed for all members
                    updateBudgetsAccessedForMembers(allMembers, budgetId)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error creating budget: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
        }
    }

    /**
     * Look up UIDs for emails from usersByEmail mapping
     */
    private fun resolveEmailsToUids(emails: List<String>, callback: (List<String>) -> Unit) {
        if (emails.isEmpty()) {
            callback(emptyList())
            return
        }

        val uids = mutableListOf<String>()
        var processed = 0

        for (email in emails) {
            db.collection("usersByEmail").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val uid = doc.getString("uid")
                        if (uid != null) uids.add(uid)
                    } else {
                        Toast.makeText(this, "User not found: $email", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Lookup failed for $email", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    processed++
                    if (processed == emails.size) {
                        callback(uids)
                    }
                }
        }
    }

 
    private fun updateBudgetsAccessedForMembers(memberIds: List<String>, budgetId: String) {
        var updated = 0
        for (uid in memberIds) {
            db.collection("users")
                .document(uid)
                .update(
                    "budgetsAccessed",
                    com.google.firebase.firestore.FieldValue.arrayUnion(budgetId)
                )
                .addOnCompleteListener {
                    updated++
                    if (updated == memberIds.size) {
                        Toast.makeText(this, "Budget created successfully!", Toast.LENGTH_SHORT)
                            .show()
                        finish()
                    }
                }
        }
    }
}
