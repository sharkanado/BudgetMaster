package com.example.budgetmaster.ui.activities

import android.app.DatePickerDialog
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
import com.example.budgetmaster.ui.components.BudgetSelectMembersInDebtAdapter
import com.example.budgetmaster.utils.updateLatestExpenses
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class CreateGroupExpense : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var budgetId: String
    private lateinit var budgetName: String

    private lateinit var membersRecycler: androidx.recyclerview.widget.RecyclerView
    private lateinit var membersAdapter: BudgetSelectMembersInDebtAdapter
    private lateinit var selectAllCheckbox: CheckBox
    private val membersList = mutableListOf<BudgetMemberItem>()

    private val selectedMembers = mutableSetOf<String>()

    private lateinit var dateInput: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_group_expense)

        val root = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Get budget data
        budgetId = intent.getStringExtra("budgetId") ?: run {
            Toast.makeText(this, "No budget provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        budgetName = intent.getStringExtra("budgetName") ?: "Unknown Budget"

        // Init date field
        dateInput = findViewById(R.id.dateInput)
        prefillTodayDate()
        dateInput.setOnClickListener { showDatePicker() }

        // Init Recycler
        membersRecycler = findViewById(R.id.membersRecycler)
        membersRecycler.layoutManager = LinearLayoutManager(this)

        // Select All checkbox
        selectAllCheckbox = findViewById(R.id.selectAllCheckbox)
        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (::membersAdapter.isInitialized) membersAdapter.toggleSelectAll(isChecked)
        }

        // Load members
        loadBudgetMembers()

        // Save button
        findViewById<View>(R.id.saveExpenseBtn).setOnClickListener { saveGroupExpense() }
    }

    private fun prefillTodayDate() {
        val today = LocalDate.now()
        dateInput.setText(today.format(DateTimeFormatter.ISO_LOCAL_DATE)) // yyyy-MM-dd
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentDate = dateInput.text?.toString()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        try {
            if (!currentDate.isNullOrEmpty()) {
                val parsedDate = LocalDate.parse(currentDate, formatter)
                calendar.set(parsedDate.year, parsedDate.monthValue - 1, parsedDate.dayOfMonth)
            }
        } catch (_: Exception) { /* ignore */
        }

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val picker = DatePickerDialog(
            this,
            { _, y, m, d ->
                val selected = LocalDate.of(y, m + 1, d)
                dateInput.setText(selected.format(DateTimeFormatter.ISO_LOCAL_DATE))
            },
            year, month, day
        )
        picker.show()
    }

    private fun loadBudgetMembers() {
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
                                    balance = 0.0
                                )
                                membersList.add(member)
                            }
                        }
                        .addOnCompleteListener {
                            processed++
                            if (processed == memberIds.size) {
                                // Default: select all
                                selectedMembers.addAll(memberIds)

                                membersAdapter = BudgetSelectMembersInDebtAdapter(
                                    membersList,
                                    selectedMembers
                                ) {
                                    // Update Select All state dynamically
                                    selectAllCheckbox.setOnCheckedChangeListener(null)
                                    selectAllCheckbox.isChecked =
                                        selectedMembers.size == membersList.size
                                    selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
                                        membersAdapter.toggleSelectAll(isChecked)
                                    }
                                }
                                membersRecycler.adapter = membersAdapter
                                selectAllCheckbox.isChecked = true
                            }
                        }
                }
            }
    }

    private fun saveGroupExpense() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val descriptionInput = findViewById<TextInputEditText>(R.id.descriptionInput)
        val amountInput = findViewById<TextInputEditText>(R.id.amountInput)
        val addPrivateCheck = findViewById<CheckBox>(R.id.addToPrivateWalletCheckbox)

        val description = descriptionInput.text?.toString()?.trim()
        val amount = amountInput.text?.toString()?.replace(",", ".")?.toDoubleOrNull()
        val dateStr = dateInput.text?.toString()?.trim()

        if (description.isNullOrEmpty() || amount == null || amount <= 0.0 || dateStr.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter valid data", Toast.LENGTH_SHORT).show()
            return
        }

        val date = LocalDate.parse(dateStr)

        val expenseData = hashMapOf(
            "amount" to amount,
            "category" to "No Category",
            "description" to description,
            "date" to date.toString(),       // yyyy-MM-dd
            "timestamp" to Timestamp.now(),
            "type" to "expense",
            "createdBy" to uid,
            "paidFor" to selectedMembers.toList(),
        )

        // 1) Create in group → get its doc id
        db.collection("budgets").document(budgetId)
            .collection("expenses")
            .add(expenseData)
            .addOnSuccessListener { groupDocRef ->
                val groupExpenseId = groupDocRef.id   // <-- THIS is the id you need
                Toast.makeText(this, "Expense added to group", Toast.LENGTH_SHORT).show()

                if (addPrivateCheck.isChecked) {
                    // 2) Mirror into private wallet with both ids set
                    addToPrivateWallet(uid, expenseData, groupExpenseId)
                } else {
                    finish()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Save a mirrored copy in the user's private wallet. We also store:
     * - budgetId (the group budget doc id)
     * - expenseIdInBudget (the expense doc id created in the group)
     */
    private fun addToPrivateWallet(
        uid: String,
        expenseData: HashMap<String, Any>,
        groupExpenseId: String
    ) {
        val date = LocalDate.parse(expenseData["date"] as String)
        val year = date.year.toString()
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

        val privateExpenseData = hashMapOf(
            "amount" to expenseData["amount"]!!,
            "category" to expenseData["category"]!!,
            "description" to expenseData["description"]!!,
            "type" to expenseData["type"]!!,
            "date" to expenseData["date"]!!,
            "timestamp" to Timestamp.now(),
            "budgetId" to budgetId,                    // so wallet can propagate edits
            "expenseIdInBudget" to groupExpenseId      // <-- carry the group expense id
        )

        // (Optional) also store budgetName in private doc if you use/need it elsewhere:
        (expenseData["budgetName"] as? String)?.let { privateExpenseData["budgetName"] = it }

        db.collection("users").document(uid)
            .collection("expenses").document(year)
            .collection(month)
            .add(privateExpenseData)
            .addOnSuccessListener { privateDocRef ->
                // If your "latest" feed expects the wallet doc id, include it:
                val latestPayload = HashMap(privateExpenseData).apply {
                    put("expenseId", privateDocRef.id)
                }
                updateLatestExpenses(uid, latestPayload)

                Toast.makeText(this, "Also added to private wallet", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Private wallet add failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }
}
