package com.example.budgetmaster.ui.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetExpenseItem
import com.google.firebase.firestore.FirebaseFirestore
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
    private lateinit var forWhomContainer: RecyclerView

    // Single toggle button (edit ↔ save)
    private lateinit var editBtn: AppCompatImageButton

    private var isEditMode = false
    private lateinit var expenseItem: BudgetExpenseItem
    private var userNames: MutableMap<String, String> = mutableMapOf()
    private var budgetId: String = ""

    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge + insets handling (required)
        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_expense_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()

        // Receive the clicked expense (correct type)
        val item: BudgetExpenseItem? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("expenseItem", BudgetExpenseItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("expenseItem")
            }

        if (item == null) {
            Toast.makeText(this, "No expense data received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        expenseItem = item

        // budgetId is needed to persist edits
        budgetId = intent.getStringExtra("budgetId") ?: ""
        if (budgetId.isEmpty()) {
            Toast.makeText(this, "No budgetId provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Optionally receive userNames map (UID -> Name)
        @Suppress("UNCHECKED_CAST")
        (intent.getSerializableExtra("userNames") as? HashMap<String, String>)?.let {
            userNames.putAll(it)
        }

        fillFieldsOnce()
        ensurePayerNameLoaded(expenseItem.createdBy)

        editBtn.setOnClickListener {
            if (!isEditMode) {
                toggleEdit(true)
            } else {
                saveChanges() // persists then flips back to view
            }
        }
    }

    private fun bindViews() {
        // View mode
        amountView = findViewById(R.id.expenseAmount)
        descriptionView = findViewById(R.id.expenseDescription)
        dateView = findViewById(R.id.expenseDate)
        // NOTE: your layout uses whoPaidName in the row; if details screen uses a different id, adjust here
        paidByView = findViewById(R.id.whoPaidName)

        // Edit mode
        amountEdit = findViewById(R.id.expenseAmountEdit)
        descriptionEdit = findViewById(R.id.expenseDescriptionEdit)
        dateEdit = findViewById(R.id.expenseDateEdit)
        forWhomContainer = findViewById(R.id.expenseParticipantsRecyclerView)

        // Single toggle button
        editBtn = findViewById(R.id.editButton)

        // start in view mode
        toggleEdit(false, updateIcon = false)
    }

    private fun fillFieldsOnce() {
        val e = expenseItem

        // View mode
        amountView.text = String.format(Locale.ENGLISH, "%.2f", e.amount)
        descriptionView.text = e.description
        dateView.text = formatDate(e.date)
        paidByView.text = userNames[e.createdBy] ?: e.createdBy // fallback to UID until resolved

        // Edit mode
        amountEdit.setText(String.format(Locale.ENGLISH, "%.2f", e.amount))
        descriptionEdit.setText(e.description)
        dateEdit.setText(e.date)
    }

    private fun toggleEdit(editMode: Boolean, updateIcon: Boolean = true) {
        isEditMode = editMode

        // View fields
        amountView.visibility = if (editMode) View.GONE else View.VISIBLE
        descriptionView.visibility = if (editMode) View.GONE else View.VISIBLE
        dateView.visibility = if (editMode) View.GONE else View.VISIBLE
        paidByView.visibility = if (editMode) View.GONE else View.VISIBLE

        // Edit fields
        amountEdit.visibility = if (editMode) View.VISIBLE else View.GONE
        descriptionEdit.visibility = if (editMode) View.VISIBLE else View.GONE
        dateEdit.visibility = if (editMode) View.VISIBLE else View.GONE

        if (updateIcon) {
            // swap icon between edit/save (use your own drawables)
            editBtn.setImageResource(if (editMode) R.drawable.ic_save else R.drawable.ic_edit)
        }
    }

    private fun saveChanges() {
        // Read inputs (trust the DatePicker-provided value)
        val newAmount = amountEdit.text.toString().replace(",", ".").toDoubleOrNull()
        if (newAmount == null) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show()
            return
        }

        val newDescription = descriptionEdit.text?.toString()?.trim().orEmpty()
        val newDate = dateEdit.text?.toString()?.trim().orEmpty()

        // Update local model
        expenseItem = expenseItem.copy(
            amount = newAmount,
            description = newDescription,
            date = newDate
        )

        // Persist to Firestore
        val updates = mapOf(
            "amount" to newAmount,
            "description" to newDescription,
            "date" to newDate
        )

        db.collection("budgets")
            .document(budgetId)
            .collection("expenses")
            .document(expenseItem.id)
            .update(updates)
            .addOnSuccessListener {
                fillFieldsOnce()
                toggleEdit(false)
                Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                editBtn.setImageResource(R.drawable.ic_edit)
            }
    }


    private fun getCheckedUsers(): MutableList<String> {
        // If you later render participant checkboxes inside forWhomContainer, this will work.
        val updatedList = mutableListOf<String>()
        for (i in 0 until forWhomContainer.childCount) {
            val cb = forWhomContainer.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) {
                val uid = userNames.entries.firstOrNull { it.value == cb.text }?.key
                if (uid != null) updatedList.add(uid)
            }
        }
        return updatedList
    }

    private fun ensurePayerNameLoaded(uid: String) {
        if (userNames[uid] != null) {
            paidByView.text = userNames[uid]
            return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Unknown"
                userNames[uid] = name
                paidByView.text = name
            }
            .addOnFailureListener {
                // keep fallback
            }
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
        fmt.parse(dateStr)
        true
    } catch (_: Exception) {
        false
    }
}
