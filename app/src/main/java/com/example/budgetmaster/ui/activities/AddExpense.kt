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
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.round

class AddExpense : AppCompatActivity() {

    private val BASE_CURRENCY = "EUR"
    private var userMainCurrency: String = "PLN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_add_expense)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // prefetch user's main currency as fallback/default
        Firebase.auth.currentUser?.uid?.let { uid ->
            FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener { doc ->
                    doc.getString("mainCurrency")?.let { userMainCurrency = it.uppercase() }
                }
        }

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

        val categoryDropdown = findViewById<AutoCompleteTextView>(R.id.categorySpinner)
        val categoryAdapter =
            ArrayAdapter(this, android.R.layout.simple_list_item_1, Categories.categoryList)
        categoryDropdown.setAdapter(categoryAdapter)

        val amountInput = findViewById<TextInputEditText>(R.id.amountInput)
        amountInput.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val raw = s?.toString() ?: return
                if (raw == current) return
                var sanitized = raw.replace(',', '.')
                sanitized = sanitized.replace(Regex("[^0-9.]"), "")
                val dotIndex = sanitized.indexOf('.')
                if (dotIndex != -1) {
                    sanitized =
                        sanitized.substring(0, dotIndex + 1) + sanitized.substring(dotIndex + 1)
                            .replace(".", "")
                    if (sanitized.length > dotIndex + 3) {
                        sanitized = sanitized.substring(0, dotIndex + 3)
                    }
                }
                sanitized = sanitized.replaceFirst(Regex("^0+(?!\\.)"), "0")
                current = sanitized
                amountInput.setText(sanitized)
                if (amountInput.isFocused && sanitized.length <= (amountInput.text?.length ?: 0)) {
                    amountInput.setSelection(sanitized.length)
                }
            }
        })

        findViewById<MaterialButton>(R.id.saveTransactionBtn).setOnClickListener {
            val userId = Firebase.auth.currentUser?.uid
            if (userId != null) {
                saveExpense(this, userId)
            }
        }
    }

    private fun selectedCurrency(): String {
        val dd1 = findViewById<AutoCompleteTextView?>(R.id.currencyDropdown)
        val dd2 = findViewById<AutoCompleteTextView?>(R.id.currencySpinner)
        val raw = (dd1?.text?.toString() ?: dd2?.text?.toString() ?: "").trim()
        val parsed = raw.substringBefore("—").trim().ifEmpty { raw }
        return (if (parsed.isNotEmpty()) parsed else userMainCurrency).uppercase()
    }

    private suspend fun fetchFxRate(
        dateStr: String,
        from: String,
        to: String
    ): Pair<Double, String>? {
        if (from.uppercase() == to.uppercase()) return 1.0 to dateStr
        fun urlFor(path: String) = "https://api.frankfurter.dev/v1/$path"
        return withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                // Try historical for the given date
                var url = URL(urlFor("$dateStr?from=$from&to=$to"))
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                var body = conn.inputStream.bufferedReader().use { it.readText() }
                var json = JSONObject(body)
                if (json.has("rates") && json.getJSONObject("rates").has(to)) {
                    val rate = json.getJSONObject("rates").getDouble(to)
                    val asOf = json.optString("date", dateStr)
                    return@withContext rate to asOf
                }
                conn.disconnect()

                // Fallback to latest
                url = URL(urlFor("latest?from=$from&to=$to"))
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                body = conn.inputStream.bufferedReader().use { it.readText() }
                json = JSONObject(body)
                if (json.has("rates") && json.getJSONObject("rates").has(to)) {
                    val rate = json.getJSONObject("rates").getDouble(to)
                    val asOf = json.optString("date", dateStr)
                    return@withContext rate to asOf
                }
                null
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun round2(value: Double): Double = round(value * 100.0) / 100.0

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

        val currency = selectedCurrency()
        val transactionType = if (btnExpense.isChecked) "expense" else "income"
        val date = LocalDate.parse(dateStr)
        val year = date.year.toString()
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

        lifecycleScope.launch {
            val fx = fetchFxRate(dateStr, currency, BASE_CURRENCY)
            if (fx == null && currency != BASE_CURRENCY) {
                Toast.makeText(context, "Couldn’t fetch FX rate. Try again.", Toast.LENGTH_LONG)
                    .show()
                return@launch
            }
            val (rate, asOf) = fx ?: (1.0 to dateStr)
            val amountBase = round2(amount * rate)

            val expenseData = hashMapOf(
                "amount" to amount,                    // original amount
                "currency" to currency,                // original currency
                "baseCurrency" to BASE_CURRENCY,       // EUR
                "amountBase" to amountBase,            // converted to EUR
                "fx" to mapOf(
                    "rate" to rate,
                    "asOf" to asOf,
                    "provider" to "frankfurter"
                ),
                "category" to category,
                "description" to description,
                "type" to transactionType,
                "date" to dateStr,
                "timestamp" to Timestamp.now(),
                "budgetName" to "personal"
            )

            val db = FirebaseFirestore.getInstance()
            val expensesRef = db.collection("users")
                .document(uid)
                .collection("expenses")
                .document(year)
                .collection(month)

            expensesRef.add(expenseData)
                .addOnSuccessListener { docRef ->
                    expenseData["expenseId"] = docRef.id
                    Toast.makeText(context, "Expense added successfully.", Toast.LENGTH_SHORT)
                        .show()
                    updateLatestExpenses(uid, expenseData)
                    startActivity(Intent(this@AddExpense, MyWallet::class.java))
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }
}
