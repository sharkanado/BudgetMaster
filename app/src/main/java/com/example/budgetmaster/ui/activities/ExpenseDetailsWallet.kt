package com.example.budgetmaster.ui.activities

import ExpenseListItem
import android.app.DatePickerDialog
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
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ExpenseDetailsWallet : AppCompatActivity() {

    private var isEditMode = false
    private lateinit var editButton: ImageButton
    private lateinit var deleteButton: MaterialButton

    // view mode
    private lateinit var categoryText: TextView
    private lateinit var amountText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var typeText: TextView
    private lateinit var dateText: TextView

    // edit mode
    private lateinit var categorySpinner: Spinner
    private lateinit var amountEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var dateEdit: EditText

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

        // Get extras
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
        setupDeleteButton()
    }

    private fun setupViews() {
        editButton = findViewById(R.id.editButton)
        deleteButton = findViewById(R.id.deleteButton)

        // view mode
        categoryText = findViewById(R.id.expenseCategory)
        amountText = findViewById(R.id.expenseAmount)
        descriptionText = findViewById(R.id.expenseDescription)
        typeText = findViewById(R.id.expenseType)
        dateText = findViewById(R.id.expenseDate)

        // edit mode
        categorySpinner = findViewById(R.id.expenseCategorySpinner)
        amountEdit = findViewById(R.id.expenseAmountEdit)
        descriptionEdit = findViewById(R.id.expenseDescriptionEdit)
        typeSpinner = findViewById(R.id.expenseTypeSpinner)
        dateEdit = findViewById(R.id.expenseDateEdit)

        val categories = listOf("Shopping", "Food", "Bills", "Travel", "Misc")
        val categoryAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = categoryAdapter

        val types = listOf("Expense", "Income")
        val typeAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        typeSpinner.adapter = typeAdapter

        // amount formatting
        amountEdit.addTextChangedListener(object : TextWatcher {
            private var current = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString() ?: return
                if (raw == current) return

                // replaces commas with dots
                var sanitized = raw.replace(',', '.')

                // digits + 1 dot allowed
                sanitized = sanitized.replace(Regex("[^0-9.]"), "")
                val dotIndex = sanitized.indexOf('.')
                if (dotIndex != -1) {
                    sanitized = sanitized.substring(0, dotIndex + 1) +
                            sanitized.substring(dotIndex + 1).replace(".", "")

                    if (sanitized.length > dotIndex + 3) {
                        sanitized = sanitized.substring(0, dotIndex + 3)
                    }
                }

                // removes leading zeros
                sanitized = sanitized.replaceFirst(Regex("^0+(?!\\.)"), "0")

                current = sanitized
                amountEdit.setText(sanitized)
                amountEdit.setSelection(sanitized.length)
            }
        })

        // Date picker logic
        dateEdit.setOnClickListener {
            val calendar = Calendar.getInstance()
            val currentDate = dateEdit.text.toString()
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            // Parse current date if valid
            try {
                val parsedDate = format.parse(currentDate)
                parsedDate?.let {
                    calendar.time = it
                }
            } catch (_: Exception) {
            }

            val year = calendar.get(Calendar.YEAR)
            val month = calendar.get(Calendar.MONTH)
            val day = calendar.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(
                this,
                { _, y, m, d ->
                    val pickedDate = String.format("%04d-%02d-%02d", y, m + 1, d)
                    dateEdit.setText(pickedDate)
                },
                year,
                month,
                day
            )
            datePicker.show()
        }
    }

    private fun populateData() {
        val titleText = if (expenseItem.type == "income") "Income Details" else "Expense Details"
        findViewById<TextView>(R.id.topBarTitle).text = titleText

        findViewById<TextView>(R.id.expenseTitle).text = expenseItem.name

        val prefillAmount =
            expenseItem.amount.replace(",", ".").replace("-", "").toDoubleOrNull() ?: 0.0
        val formattedAmount = "%.2f".format(prefillAmount)

        amountText.text =
            if (expenseItem.type == "expense") "-$formattedAmount" else formattedAmount

        dateText.text = expenseItem.date
        categoryText.text = expenseItem.category
        typeText.text = expenseItem.type.replaceFirstChar { it.uppercase() }
        descriptionText.text = expenseItem.name

        amountEdit.setText(formattedAmount)
        descriptionEdit.setText(expenseItem.name)
        dateEdit.setText(expenseItem.date)

        // sets spinners
        val catPos =
            (categorySpinner.adapter as ArrayAdapter<String>).getPosition(expenseItem.category)
        if (catPos >= 0) categorySpinner.setSelection(catPos)

        val typePos =
            (typeSpinner.adapter as ArrayAdapter<String>)
                .getPosition(expenseItem.type.replaceFirstChar { it.uppercase() })
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

    private fun setupDeleteButton() {
        deleteButton.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(uid)
                .collection("expenses")
                .document(selectedYear.toString())
                .collection(selectedMonth)
                .document(expenseItem.id)
                .delete()
                .addOnSuccessListener {
                    Toast.makeText(this, "Successfully removed!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
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
            dateText.visibility = View.GONE

            categorySpinner.visibility = View.VISIBLE
            typeSpinner.visibility = View.VISIBLE
            amountEdit.visibility = View.VISIBLE
            descriptionEdit.visibility = View.VISIBLE
            dateEdit.visibility = View.VISIBLE
        } else {
            categoryText.visibility = View.VISIBLE
            typeText.visibility = View.VISIBLE
            amountText.visibility = View.VISIBLE
            descriptionText.visibility = View.VISIBLE
            dateText.visibility = View.VISIBLE

            categorySpinner.visibility = View.GONE
            typeSpinner.visibility = View.GONE
            amountEdit.visibility = View.GONE
            descriptionEdit.visibility = View.GONE
            dateEdit.visibility = View.GONE
        }
    }

    private fun saveData() {
        val newCategory = categorySpinner.selectedItem.toString()
        val newType = typeSpinner.selectedItem.toString().lowercase()

        val normalizedInput = amountEdit.text.toString().replace(",", ".").replace("-", "")
        val newAmount = normalizedInput.toDoubleOrNull() ?: 0.0

        if (newAmount == 0.0) {
            Toast.makeText(this, "Amount cannot be 0", Toast.LENGTH_SHORT).show()
            return
        }

        val newDescription = descriptionEdit.text.toString()
        val newDate = dateEdit.text.toString()

        categoryText.text = newCategory
        typeText.text = newType.replaceFirstChar { it.uppercase() }
        amountText.text =
            if (newType == "expense") "-%.2f".format(newAmount) else "%.2f".format(newAmount)
        descriptionText.text = newDescription
        dateText.text = newDate

        val titleText = if (newType == "income") "Income Details" else "Expense Details"
        findViewById<TextView>(R.id.topBarTitle).text = titleText

        val updatedData = mapOf(
            "category" to newCategory,
            "amount" to newAmount,
            "description" to newDescription,
            "type" to newType,
            "date" to newDate
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
