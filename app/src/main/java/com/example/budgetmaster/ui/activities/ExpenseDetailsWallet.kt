package com.example.budgetmaster.ui.activities

import ExpenseListItem
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetmaster.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ExpenseDetailsWallet : AppCompatActivity() {

    private var isEditMode = false
    private lateinit var editButton: ImageButton

    // View mode
    private lateinit var categoryText: TextView
    private lateinit var amountText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var typeText: TextView

    // Edit mode
    private lateinit var categorySpinner: Spinner
    private lateinit var amountEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var typeSpinner: Spinner

    private lateinit var expenseItem: ExpenseListItem.Item

    private var selectedYear: Int = 0
    private var selectedMonth: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_expense_details_wallet)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        selectedYear = intent.getIntExtra("selectedYear", 0)
        selectedMonth = intent.getStringExtra("selectedMonth") ?: ""

        val item: ExpenseListItem.Item? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("expense_item", ExpenseListItem.Item::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("expense_item")
            }

        if (item == null) {
            Toast.makeText(this, "No expense data received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        expenseItem = item

        setupViews()
        populateData()
        setupEditToggle()
    }

    private fun setupViews() {
        editButton = findViewById(R.id.editButton)

        // View mode
        categoryText = findViewById(R.id.expenseCategory)
        amountText = findViewById(R.id.expenseAmount)
        descriptionText = findViewById(R.id.expenseDescription)
        typeText = findViewById(R.id.expenseType)

        // Edit mode
        categorySpinner = findViewById(R.id.expenseCategorySpinner)
        amountEdit = findViewById(R.id.expenseAmountEdit)
        descriptionEdit = findViewById(R.id.expenseDescriptionEdit)
        typeSpinner = findViewById(R.id.expenseTypeSpinner)

        // Category spinner
        val categories = listOf("Shopping", "Food", "Bills", "Travel", "Misc")
        val categoryAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = categoryAdapter

        // Type spinner
        val types = listOf("Expense", "Income")
        val typeAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        typeSpinner.adapter = typeAdapter

        // Amount formatting watcher
        amountEdit.addTextChangedListener(object : TextWatcher {
            private var current = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString() ?: return
                if (raw == current) return

                // Replace commas with dots
                var sanitized = raw.replace(',', '.')

                // Only digits + 1 dot allowed
                sanitized = sanitized.replace(Regex("[^0-9.]"), "")
                val dotIndex = sanitized.indexOf('.')
                if (dotIndex != -1) {
                    sanitized = sanitized.substring(0, dotIndex + 1) +
                            sanitized.substring(dotIndex + 1).replace(".", "")

                    // Max 2 decimals
                    if (sanitized.length > dotIndex + 3) {
                        sanitized = sanitized.substring(0, dotIndex + 3)
                    }
                }

                // Remove leading zeros
                sanitized = sanitized.replaceFirst(Regex("^0+(?!\\.)"), "0")

                current = sanitized
                amountEdit.setText(sanitized)
                amountEdit.setSelection(sanitized.length)
            }
        })
    }

    private fun populateData() {
        val titleText = if (expenseItem.type == "income") "Income Details" else "Expense Details"
        findViewById<TextView>(R.id.topBarTitle).text = titleText

        findViewById<TextView>(R.id.expenseTitle).text = expenseItem.name

        // Prefill with dot-format and 2 decimals
        val prefillAmount = expenseItem.amount.replace(",", ".").toDoubleOrNull() ?: 0.0
        val formattedAmount = "%.2f".format(prefillAmount)

        amountText.text = formattedAmount
        findViewById<TextView>(R.id.expenseDate).text = expenseItem.date
        categoryText.text = expenseItem.budget
        typeText.text = expenseItem.type.replaceFirstChar { it.uppercase() }
        descriptionText.text = expenseItem.name

        amountEdit.setText(formattedAmount)
        descriptionEdit.setText(expenseItem.name)

        // Set spinners
        val catPos =
            (categorySpinner.adapter as ArrayAdapter<String>).getPosition(expenseItem.budget)
        if (catPos >= 0) categorySpinner.setSelection(catPos)

        val typePos =
            (typeSpinner.adapter as ArrayAdapter<String>).getPosition(expenseItem.type.replaceFirstChar { it.uppercase() })
        if (typePos >= 0) typeSpinner.setSelection(typePos)
    }

    private fun setupEditToggle() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        editButton.setOnClickListener {
            if (!isEditMode) {
                toggleEditMode(true)
            } else {
                saveData()
                toggleEditMode(false)
            }
        }
    }

    private fun toggleEditMode(enable: Boolean) {
        isEditMode = enable

        editButton.setImageResource(if (enable) R.drawable.ic_save else R.drawable.ic_edit)

        if (enable) {
            categoryText.visibility = View.GONE
            typeText.visibility = View.GONE
            amountText.visibility = View.GONE
            descriptionText.visibility = View.GONE

            categorySpinner.visibility = View.VISIBLE
            typeSpinner.visibility = View.VISIBLE
            amountEdit.visibility = View.VISIBLE
            descriptionEdit.visibility = View.VISIBLE
        } else {
            categoryText.visibility = View.VISIBLE
            typeText.visibility = View.VISIBLE
            amountText.visibility = View.VISIBLE
            descriptionText.visibility = View.VISIBLE

            categorySpinner.visibility = View.GONE
            typeSpinner.visibility = View.GONE
            amountEdit.visibility = View.GONE
            descriptionEdit.visibility = View.GONE
        }
    }

    private fun saveData() {
        val newCategory = categorySpinner.selectedItem.toString()
        val newType = typeSpinner.selectedItem.toString()

        // Normalize amount
        val normalizedInput = amountEdit.text.toString().replace(",", ".")
        val newAmount = normalizedInput.toDoubleOrNull() ?: 0.0

        val newDescription = descriptionEdit.text.toString()

        // Update UI
        categoryText.text = newCategory
        typeText.text = newType
        amountText.text = "%.2f".format(newAmount)
        descriptionText.text = newDescription

        // **Update page title immediately when type changes**
        val titleText = if (newType.lowercase() == "income") "Income Details" else "Expense Details"
        findViewById<TextView>(R.id.topBarTitle).text = titleText

        val updatedData = mapOf(
            "category" to newCategory,
            "amount" to newAmount,
            "description" to newDescription,
            "type" to newType.lowercase()
        )

        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        db.collection("users")
            .document(uid)
            .collection("expenses")
            .document(selectedYear.toString())
            .collection(selectedMonth)
            .document(expenseItem.id)
            .update(updatedData)
            .addOnSuccessListener {
                Toast.makeText(this, "Changes saved!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save changes: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
    }
}
