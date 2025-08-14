package com.example.budgetmaster.ui.activities

import ExpenseListItem
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import com.example.budgetmaster.utils.Categories
import com.google.android.material.button.MaterialButton
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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
    private var expenseDocumentId: String = ""

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
        expenseDocumentId = intent.getStringExtra("expenseId") ?: ""

        val item: ExpenseListItem.Item? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("expense_item", ExpenseListItem.Item::class.java)
                    ?: intent.getParcelableExtra("expenseItem", ExpenseListItem.Item::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<ExpenseListItem.Item>("expense_item")
                    ?: @Suppress("DEPRECATION") intent.getParcelableExtra("expenseItem")
            }

        if (item == null) {
            Toast.makeText(this, "No expense data received", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        expenseItem = item
        if (expenseDocumentId.isBlank()) expenseDocumentId = expenseItem.id

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

        val categoryAdapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Categories.categoryList
        )
        categorySpinner.adapter = categoryAdapter

        val types = listOf("Expense", "Income")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)
        typeSpinner.adapter = typeAdapter

        // amount formatting
        amountEdit.addTextChangedListener(object : TextWatcher {
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
                    sanitized = sanitized.substring(0, dotIndex + 1) +
                            sanitized.substring(dotIndex + 1).replace(".", "")
                    if (sanitized.length > dotIndex + 3) {
                        sanitized = sanitized.substring(0, dotIndex + 3)
                    }
                }
                sanitized = sanitized.replaceFirst(Regex("^0+(?!\\.)"), "0")
                current = sanitized
                amountEdit.setText(sanitized)
                amountEdit.setSelection(sanitized.length)
            }
        })

        // date picker
        dateEdit.setOnClickListener {
            val calendar = Calendar.getInstance()
            val currentDate = dateEdit.text.toString()
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            try {
                format.parse(currentDate)?.let { calendar.time = it }
            } catch (_: Exception) {
            }
            val y = calendar.get(Calendar.YEAR)
            val m = calendar.get(Calendar.MONTH)
            val d = calendar.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, yy, mm, dd ->
                dateEdit.setText(String.format("%04d-%02d-%02d", yy, mm + 1, dd))
            }, y, m, d).show()
        }
    }

    private fun populateData() {
        val titleText = if (expenseItem.type == "income") "Income Details" else "Expense Details"
        findViewById<TextView>(R.id.topBarTitle).text = titleText

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

        val catPos =
            (categorySpinner.adapter as ArrayAdapter<String>).getPosition(expenseItem.category)
        if (catPos >= 0) categorySpinner.setSelection(catPos)

        val typePos = (typeSpinner.adapter as ArrayAdapter<String>)
            .getPosition(expenseItem.type.replaceFirstChar { it.uppercase() })
        if (typePos >= 0) typeSpinner.setSelection(typePos)
    }

    private fun setupEditToggle() {
        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        editButton.setOnClickListener {
            if (!isEditMode) toggleEditMode(true) else saveData()
        }
    }

    private fun setupDeleteButton() {
        deleteButton.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setOnClickListener
            val db = FirebaseFirestore.getInstance()

            // Delete from wallet
            db.collection("users").document(uid).collection("expenses")
                .document(selectedYear.toString()).collection(selectedMonth)
                .document(expenseDocumentId)
                .delete()
                .addOnSuccessListener {
                    // Clean "latest"
                    db.collection("users").document(uid).collection("latest")
                        .whereEqualTo("expenseId", expenseDocumentId)
                        .get()
                        .addOnSuccessListener { snap ->
                            val batch = db.batch()
                            for (doc in snap.documents) batch.delete(doc.reference)
                            batch.commit().addOnSuccessListener {
                                Toast.makeText(this, "Successfully removed!", Toast.LENGTH_SHORT)
                                    .show()
                                finish()
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(
                                this,
                                "Deleted from expenses, failed to clean latest: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        }
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

        val viewVis = if (enable) View.GONE else View.VISIBLE
        val editVis = if (enable) View.VISIBLE else View.GONE

        categoryText.visibility = viewVis
        typeText.visibility = viewVis
        amountText.visibility = viewVis
        descriptionText.visibility = viewVis
        dateText.visibility = viewVis

        categorySpinner.visibility = editVis
        typeSpinner.visibility = editVis
        amountEdit.visibility = editVis
        descriptionEdit.visibility = editVis
        dateEdit.visibility = editVis
    }

    private fun saveData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val year = dateEdit.text.toString().substring(0, 4)
        val month = SimpleDateFormat("MMMM", Locale.ENGLISH)
            .format(
                SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
                    .parse(dateEdit.text.toString())!!
            )

        val amountStr = amountEdit.text.toString().trim()
        val amount = amountStr.replace(",", ".").toDoubleOrNull() ?: 0.0

        // Store "amount" as NUMBER to prevent future crashes
        val updatedData = mapOf(
            "category" to categorySpinner.selectedItem.toString(),
            "description" to descriptionEdit.text.toString().trim(),
            "amount" to amount, // NUMBER, not string
            "date" to dateEdit.text.toString(),
            "type" to typeSpinner.selectedItem.toString().lowercase(),
            "timestamp" to Timestamp.now()
        )

        Log.d("DEBUG_FIRESTORE", "Updating doc: $uid / $year / $month / $expenseDocumentId")

        val db = FirebaseFirestore.getInstance()

        // 1) Update in user's expenses
        db.collection("users").document(uid)
            .collection("expenses").document(year)
            .collection(month).document(expenseDocumentId)
            .set(updatedData, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show()

                // 2) Also update in 'latest' where expenseId matches
                val latestPatch = mapOf(
                    "category" to updatedData["category"],
                    "description" to updatedData["description"],
                    "amount" to updatedData["amount"],   // keep number
                    "date" to updatedData["date"],
                    "type" to updatedData["type"],
                    "timestamp" to updatedData["timestamp"]
                )

                db.collection("users").document(uid)
                    .collection("latest")
                    .whereEqualTo("expenseId", expenseDocumentId)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (snap.isEmpty) return@addOnSuccessListener
                        val batch = db.batch()
                        for (doc in snap.documents) {
                            batch.set(doc.reference, latestPatch, SetOptions.merge())
                        }
                        batch.commit()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }

    }
}
