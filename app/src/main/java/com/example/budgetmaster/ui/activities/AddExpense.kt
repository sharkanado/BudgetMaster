package com.example.budgetmaster.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetmaster.R
import com.example.budgetmaster.utils.Categories
import com.example.budgetmaster.utils.updateLatestExpenses
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale

class AddExpense : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_expense)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Date Picker Setup with Calendar Icon
        val dateInputLayout = findViewById<TextInputLayout>(R.id.dateInputLayout)
        val dateInput = findViewById<TextInputEditText>(R.id.dateInput)
        val today = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateInput.setText(dateFormat.format(today))

        fun showDatePicker() {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select date")
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            picker.show(supportFragmentManager, "DATE_PICKER")

            picker.addOnPositiveButtonClickListener { selection ->
                val selectedDate = Date(selection)
                dateInput.setText(dateFormat.format(selectedDate))
            }
        }

        dateInputLayout.setEndIconOnClickListener { showDatePicker() }
        dateInput.setOnClickListener { showDatePicker() }

        // Toggle Buttons Setup
        val btnExpense = findViewById<MaterialButton>(R.id.btnExpense)
        val btnIncome = findViewById<MaterialButton>(R.id.btnIncome)
        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.transactionTypeToggle)

        fun updateToggleStyles() {
            val orange = ContextCompat.getColor(this, R.color.orange)
            val greyDark = ContextCompat.getColor(this, R.color.grey_dark)
            val white = ContextCompat.getColor(this, android.R.color.white)

            if (btnExpense.isChecked) {
                btnExpense.setBackgroundColor(orange)
                btnIncome.setBackgroundColor(greyDark)
            } else {
                btnIncome.setBackgroundColor(orange)
                btnExpense.setBackgroundColor(greyDark)
            }

            btnExpense.setTextColor(white)
            btnIncome.setTextColor(white)
        }

        toggleGroup.addOnButtonCheckedListener { _, _, _ -> updateToggleStyles() }
        updateToggleStyles()

        // Category Dropdown
        val categoryDropdown = findViewById<AutoCompleteTextView>(R.id.categorySpinner)

        val categoryAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            Categories.categoryList
        )
        categoryDropdown.setAdapter(categoryAdapter)


        // Amount Input (comma/dot handling)
        val amountInput = findViewById<TextInputEditText>(R.id.amountInput)
        amountInput.addTextChangedListener(object : TextWatcher {
            private var current = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString() ?: return
                if (raw == current) return

                var sanitized = raw.replace(',', '.')

                // Only digits + one dot
                sanitized = sanitized.replace(Regex("[^0-9.]"), "")
                val dotIndex = sanitized.indexOf('.')
                if (dotIndex != -1) {
                    sanitized = sanitized.substring(0, dotIndex + 1) +
                            sanitized.substring(dotIndex + 1).replace(".", "")

                    if (sanitized.length > dotIndex + 3) {
                        sanitized = sanitized.substring(0, dotIndex + 3)
                    }
                }

                // Remove leading zeros (except before dot)
                sanitized = sanitized.replaceFirst(Regex("^0+(?!\\.)"), "0")

                current = sanitized
                amountInput.setText(sanitized)
                amountInput.setSelection(sanitized.length)
            }
        })

        // Save Button
        findViewById<MaterialButton>(R.id.saveTransactionBtn).setOnClickListener {
            val userId = Firebase.auth.currentUser?.uid
            if (userId != null) {
                saveExpense(this, userId)
            }
        }
    }

    private fun saveExpense(context: Context, uid: String) {
        val amountInput = findViewById<TextInputEditText>(R.id.amountInput)
        val categoryDropdown = findViewById<AutoCompleteTextView>(R.id.categorySpinner)
        val descriptionInput = findViewById<TextInputEditText>(R.id.descriptionInput)
        val dateInput = findViewById<TextInputEditText>(R.id.dateInput)
        val btnExpense = findViewById<MaterialButton>(R.id.btnExpense)

        val amountText = amountInput.text.toString().replace(",", ".")
        val amount = amountText.toDoubleOrNull()
        val category = categoryDropdown.text.toString()
        val description = descriptionInput.text.toString()
        val dateStr = dateInput.text.toString()

        if (amount == null || amount == 0.0 || dateStr.isEmpty() || category.isEmpty()) {
            Toast.makeText(context, "Please fill in all fields correctly", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val transactionType = if (btnExpense.isChecked) "expense" else "income"
        val date = LocalDate.parse(dateStr)
        val year = date.year.toString()
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

        val expenseData = hashMapOf(
            "amount" to amount,
            "category" to category,
            "description" to description,
            "type" to transactionType,
            "date" to dateStr,
            "timestamp" to Timestamp.now()
        )

        val db = FirebaseFirestore.getInstance()
        val expensesRef = db.collection("users")
            .document(uid)
            .collection("expenses")
            .document(year)
            .collection(month)

        expensesRef.add(expenseData)
            .addOnSuccessListener { docRef ->
                // Attach the ID for later deletion from `latest`
                expenseData["expenseId"] = docRef.id

                Toast.makeText(context, "Expense added successfully.", Toast.LENGTH_SHORT).show()
                updateLatestExpenses(uid, expenseData)

                startActivity(Intent(this, MyWallet::class.java))
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

}
