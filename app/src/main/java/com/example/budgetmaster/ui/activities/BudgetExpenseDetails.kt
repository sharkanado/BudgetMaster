package com.example.budgetmaster.ui.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetExpenseItem
import com.example.budgetmaster.ui.components.BudgetMemberItem
import com.example.budgetmaster.ui.components.BudgetMembersAdapter
import com.example.budgetmaster.ui.components.BudgetSelectMembersInDebtAdapter
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

class BudgetExpenseDetails : AppCompatActivity() {

    // View-mode fields
    private lateinit var amountView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var dateView: TextView
    private lateinit var paidByView: TextView

    // Edit-mode fields
    private lateinit var amountEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var dateEdit: EditText

    // Single recycler – swap adapters depending on mode
    private lateinit var participantsRecycler: RecyclerView

    // Adapters & backing lists
    private val allMembers = mutableListOf<BudgetMemberItem>()           // all budget members
    private val selectedMembers = mutableSetOf<String>()                 // current selection
    private val participantsReadOnly = mutableListOf<BudgetMemberItem>() // selected -> tiles

    private lateinit var readOnlyAdapter: BudgetMembersAdapter
    private lateinit var checkboxAdapter: BudgetSelectMembersInDebtAdapter

    private lateinit var editBtn: AppCompatImageButton
    private var isEditMode = false

    private lateinit var expenseItem: BudgetExpenseItem
    private var userNames: MutableMap<String, String> = mutableMapOf()
    private var budgetId: String = ""

    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_expense_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()

        val item: BudgetExpenseItem? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("expenseItem", BudgetExpenseItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("expenseItem")
            }
        if (item == null) {
            Toast.makeText(this, "No expense data received", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        expenseItem = item

        budgetId = intent.getStringExtra("budgetId") ?: ""
        if (budgetId.isEmpty()) {
            Toast.makeText(this, "No budgetId provided", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        val passedMap: HashMap<String, String>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(
                    "userNames",
                    HashMap::class.java
                ) as? HashMap<String, String>
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("userNames") as? HashMap<String, String>
            }
        passedMap?.let { userNames.putAll(it) }

        // Seed selection from the expense
        selectedMembers.clear()
        selectedMembers.addAll(expenseItem.paidFor)

        fillFieldsOnce()
        ensurePayerNameLoaded(expenseItem.createdBy)

        // Recycler setup + adapters (now created BEFORE any toggle)
        participantsRecycler.layoutManager = LinearLayoutManager(this)

        readOnlyAdapter = BudgetMembersAdapter(participantsReadOnly)
        checkboxAdapter = BudgetSelectMembersInDebtAdapter(
            members = allMembers,
            selectedMembers = selectedMembers
        ) {
            // Update the read-only backing list as selection changes
            rebuildReadOnlyParticipants()
        }

        // Start in VIEW mode: show read-only tiles
        setUiForViewMode()
        participantsRecycler.adapter = readOnlyAdapter

        // Load members and update both adapters
        loadBudgetMembers()

        editBtn.setOnClickListener {
            if (!isEditMode) toggleEdit(true) else saveChanges()
        }
    }

    private fun bindViews() {
        amountView = findViewById(R.id.expenseAmount)
        descriptionView = findViewById(R.id.expenseDescription)
        dateView = findViewById(R.id.expenseDate)
        paidByView = findViewById(R.id.whoPaidName)

        amountEdit = findViewById(R.id.expenseAmountEdit)
        descriptionEdit = findViewById(R.id.expenseDescriptionEdit)
        dateEdit = findViewById(R.id.expenseDateEdit)

        participantsRecycler = findViewById(R.id.expenseParticipantsRecyclerView)

        editBtn = findViewById(R.id.editButton)

        // Initial UI state (view mode) — do NOT call toggleEdit() here
        setUiForViewMode()
        editBtn.setImageResource(R.drawable.ic_edit)
    }

    private fun setUiForViewMode() {
        // View fields visible
        amountView.visibility = View.VISIBLE
        descriptionView.visibility = View.VISIBLE
        dateView.visibility = View.VISIBLE
        paidByView.visibility = View.VISIBLE
        // Edit fields hidden
        amountEdit.visibility = View.GONE
        descriptionEdit.visibility = View.GONE
        dateEdit.visibility = View.GONE
        isEditMode = false
    }

    private fun setUiForEditMode() {
        // View fields hidden
        amountView.visibility = View.GONE
        descriptionView.visibility = View.GONE
        dateView.visibility = View.GONE
        paidByView.visibility = View.GONE
        // Edit fields visible
        amountEdit.visibility = View.VISIBLE
        descriptionEdit.visibility = View.VISIBLE
        dateEdit.visibility = View.VISIBLE
        isEditMode = true
    }

    private fun loadBudgetMembers() {
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->
                val memberIds = (doc.get("members") as? List<String>).orEmpty()
                allMembers.clear()

                if (memberIds.isEmpty()) {
                    participantsReadOnly.clear()
                    readOnlyAdapter.notifyDataSetChanged()
                    checkboxAdapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                var processed = 0
                memberIds.forEach { uid ->
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                allMembers.add(
                                    BudgetMemberItem(
                                        uid = uid,
                                        name = userDoc.getString("name") ?: "Unknown",
                                        email = userDoc.getString("email") ?: "",
                                        balance = 0.0
                                    )
                                )
                            }
                        }
                        .addOnCompleteListener {
                            processed++
                            if (processed == memberIds.size) {
                                // Keep only valid selected members
                                selectedMembers.retainAll(memberIds.toSet())
                                // Build read-only list from current selection
                                rebuildReadOnlyParticipants()
                                // Notify both adapters
                                readOnlyAdapter.notifyDataSetChanged()
                                checkboxAdapter.notifyDataSetChanged()
                                // Ensure the correct adapter is attached
                                participantsRecycler.adapter =
                                    if (isEditMode) checkboxAdapter else readOnlyAdapter
                            }
                        }
                }
            }
    }

    private fun rebuildReadOnlyParticipants() {
        participantsReadOnly.clear()
        if (selectedMembers.isEmpty()) return
        val set = selectedMembers.toSet()
        participantsReadOnly.addAll(allMembers.filter { it.uid in set })
    }

    private fun fillFieldsOnce() {
        val e = expenseItem
        amountView.text = String.format(Locale.ENGLISH, "%.2f", e.amount)
        descriptionView.text = e.description
        dateView.text = formatDate(e.date)
        paidByView.text = userNames[e.createdBy] ?: e.createdBy

        amountEdit.setText(String.format(Locale.ENGLISH, "%.2f", e.amount))
        descriptionEdit.setText(e.description)
        dateEdit.setText(e.date)
    }

    private fun toggleEdit(editMode: Boolean) {
        if (editMode) {
            setUiForEditMode()
            editBtn.setImageResource(R.drawable.ic_save)
            if (::checkboxAdapter.isInitialized) participantsRecycler.adapter = checkboxAdapter
        } else {
            setUiForViewMode()
            editBtn.setImageResource(R.drawable.ic_edit)
            if (::readOnlyAdapter.isInitialized) {
                rebuildReadOnlyParticipants()
                readOnlyAdapter.notifyDataSetChanged()
                participantsRecycler.adapter = readOnlyAdapter
            }
        }
    }

    private fun saveChanges() {
        val newAmount = amountEdit.text.toString().replace(",", ".").toDoubleOrNull()
        if (newAmount == null) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return
        }
        val newDescription = descriptionEdit.text?.toString()?.trim().orEmpty()
        val newDate = dateEdit.text?.toString()?.trim().orEmpty()
        if (newDate.isBlank() || !isValidDate(newDate)) {
            Toast.makeText(this, "Enter a valid date (yyyy-MM-dd)", Toast.LENGTH_SHORT).show()
            return
        }

        // Update local model
        expenseItem = expenseItem.copy(
            amount = newAmount,
            description = newDescription,
            date = newDate,
            paidFor = selectedMembers.toList()
        )

        val updates = mapOf(
            "amount" to newAmount,
            "description" to newDescription,
            "date" to newDate,
            "paidFor" to selectedMembers.toList(),
            "timestamp" to Timestamp.now()
        )

        db.collection("budgets")
            .document(budgetId)
            .collection("expenses")
            .document(expenseItem.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                rebuildReadOnlyParticipants()
                readOnlyAdapter.notifyDataSetChanged()
                fillFieldsOnce()
                toggleEdit(false)
                Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun ensurePayerNameLoaded(uid: String) {
        if (userNames[uid] != null) {
            paidByView.text = userNames[uid]; return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Unknown"
                userNames[uid] = name
                paidByView.text = name
            }
            .addOnFailureListener { /* keep fallback */ }
    }

    private fun formatDate(dateStr: String): String = try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val output = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val date = input.parse(dateStr)
        output.format(date!!)
    } catch (_: Exception) {
        dateStr
    }

    private fun isValidDate(dateStr: String): Boolean = try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { isLenient = false }
        fmt.parse(dateStr); true
    } catch (_: Exception) {
        false
    }
}
