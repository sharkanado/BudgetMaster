package com.example.budgetmaster.ui.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetmaster.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.round

class SettleUpPayments : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private lateinit var budgetId: String
    private lateinit var receiverId: String
    private lateinit var receiverName: String
    private var amount: Double = 0.0
    private var budgetCurrency: String = "EUR"

    private lateinit var descriptionInput: TextInputEditText
    private lateinit var amountInput: TextInputEditText
    private lateinit var amountCurrencyText: TextView
    private lateinit var dateInput: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_settle_up_payments)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, maxOf(sys.bottom, ime.bottom))
            insets
        }

        budgetId = intent.getStringExtra("budgetId") ?: run {
            Toast.makeText(this, "No budget provided", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        receiverId = intent.getStringExtra("receiverId") ?: run {
            Toast.makeText(this, "No receiver provided", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        receiverName = intent.getStringExtra("receiverName") ?: "Unknown"
        amount = intent.getDoubleExtra("amount", 0.0)

        descriptionInput = findViewById(R.id.descriptionInput)
        amountInput = findViewById(R.id.amountInput)
        amountCurrencyText = findViewById(R.id.amountCurrencyText)
        dateInput = findViewById(R.id.dateInput)

        loadBudgetCurrency()

        prefillTodayDate()
        dateInput.setOnClickListener { showDatePicker() }

        fetchCurrentUserName { yourName ->
            descriptionInput.setText("$yourName and $receiverName settlement")
        }
        amountInput.setText(String.format(Locale.US, "%.2f", amount))
        amountInput.isEnabled = false

        findViewById<android.view.View>(R.id.saveExpenseBtn).setOnClickListener {
            saveSettlement()
        }
    }

    private fun loadBudgetCurrency() {
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->
                budgetCurrency = (doc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
                amountCurrencyText.text = budgetCurrency
            }
    }

    private fun prefillTodayDate() {
        val today = LocalDate.now()
        dateInput.setText(today.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentDate = dateInput.text?.toString()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        try {
            if (!currentDate.isNullOrEmpty()) {
                val parsed = LocalDate.parse(currentDate, formatter)
                calendar.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
            }
        } catch (_: Exception) {
        }

        val picker = DatePickerDialog(
            this,
            { _, y, m, d ->
                val selected = LocalDate.of(y, m + 1, d)
                dateInput.setText(selected.format(DateTimeFormatter.ISO_LOCAL_DATE))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        picker.show()
    }

    private fun saveSettlement() {
        val uid = auth.currentUser?.uid ?: run {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }
        val description = descriptionInput.text?.toString()?.trim()
        val dateStr = dateInput.text?.toString()?.trim()

        if (description.isNullOrEmpty() || amount <= 0.0 || dateStr.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid data", Toast.LENGTH_SHORT).show(); return
        }

        val paidShares = mapOf(receiverId to round2(amount))

        val expenseData = hashMapOf(
            "amount" to amount,
            "category" to "Settlement",
            "description" to description,
            "date" to dateStr,
            "timestamp" to Timestamp.now(),
            "type" to "settlement",
            "createdBy" to uid,
            "paidFor" to listOf(receiverId),
            "paidShares" to paidShares
        )

        db.collection("budgets").document(budgetId)
            .collection("expenses")
            .add(expenseData)
            .addOnSuccessListener {
                val batch = db.batch()
                val serverTime = FieldValue.serverTimestamp()

                // receiver loses receivable
                val receiverTotalsRef = db.collection("budgets").document(budgetId)
                    .collection("totals").document(receiverId)
                batch.set(
                    receiverTotalsRef,
                    mapOf(
                        "receivable" to FieldValue.increment(-amount),
                        "updatedAt" to serverTime
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )

                // payer loses debt
                val payerTotalsRef = db.collection("budgets").document(budgetId)
                    .collection("totals").document(uid)
                batch.set(
                    payerTotalsRef,
                    mapOf(
                        "debt" to FieldValue.increment(-amount),
                        "updatedAt" to serverTime
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Settlement saved", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Error updating totals: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun fetchCurrentUserName(callback: (String) -> Unit) {
        val uid = auth.currentUser?.uid ?: return callback("You")
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                callback(doc.getString("name") ?: "You")
            }
            .addOnFailureListener { callback("You") }
    }

    private fun round2(v: Double) = round(v * 100.0) / 100.0
}
