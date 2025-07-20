package com.example.budgetmaster.ui.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetmaster.R
import com.example.budgetmaster.utils.updateLatestExpenses
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
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

        val dateInput = findViewById<TextInputEditText>(R.id.dateInput)
        val today = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        dateInput.setText(dateFormat.format(today))
        dateInput.inputType = InputType.TYPE_NULL
        dateInput.isFocusable = false
        dateInput.isClickable = true

        dateInput.setOnClickListener {
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

        val currencySpinner = findViewById<Spinner>(R.id.currencySpinner)
        val currencies = listOf("PLN", "USD", "EUR", "GBP", "JPY")
        val currencyAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, currencies)
        currencySpinner.adapter = currencyAdapter

        val categorySpinner = findViewById<Spinner>(R.id.categorySpinner)
        val categories = listOf(
            "Food", "Transport", "Entertainment", "Bills",
            "Health", "Shopping", "Savings", "Investment", "Salary", "Gift", "Other"
        )
        val categoryAdapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        categorySpinner.adapter = categoryAdapter

        findViewById<MaterialButton>(R.id.saveTransactionBtn).setOnClickListener {
            val userId = Firebase.auth.currentUser?.uid
            if (userId != null) {
                saveExpense(this, userId)
                startActivity(Intent(this, MyWallet::class.java))
                finish()
            }
        }
    }

    private fun saveExpense(context: Context, uid: String) {
        val amountInput = findViewById<TextInputEditText>(R.id.amountInput)
        val categorySpinner = findViewById<Spinner>(R.id.categorySpinner)
        val currencySpinner = findViewById<Spinner>(R.id.currencySpinner)
        val descriptionInput = findViewById<TextInputEditText>(R.id.descriptionInput)
        val dateInput = findViewById<TextInputEditText>(R.id.dateInput)
        val btnExpense = findViewById<MaterialButton>(R.id.btnExpense)

        val amount = amountInput.text.toString().toDoubleOrNull()
        val category = categorySpinner.selectedItem?.toString() ?: ""
        val currency = currencySpinner.selectedItem?.toString() ?: ""
        val description = descriptionInput.text.toString()
        val dateStr = dateInput.text.toString()

        if (amount == null || dateStr.isEmpty() || category.isEmpty() || currency.isEmpty()) {
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
            "currency" to currency,
            "description" to description,
            "type" to transactionType,
            "date" to dateStr,
            "timestamp" to Timestamp.now()
        )

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(uid)
            .collection("expenses")
            .document(year)
            .collection(month)
            .add(expenseData)
            .addOnSuccessListener {
                Toast.makeText(context, "Expense added successfully.", Toast.LENGTH_SHORT).show()
                updateLatestExpenses(uid, expenseData)
            }
            .addOnFailureListener { e ->
                Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
