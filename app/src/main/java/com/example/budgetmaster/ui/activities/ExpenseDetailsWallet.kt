package com.example.budgetmaster.ui.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetmaster.R
import com.example.budgetmaster.utils.Categories
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.round

class ExpenseDetailsWallet : AppCompatActivity() {

    private var isEditMode = false
    private lateinit var editButton: ImageButton

    private lateinit var categoryText: TextView
    private lateinit var amountText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var typeText: TextView
    private lateinit var dateText: TextView

    private lateinit var categorySpinner: Spinner
    private lateinit var amountEdit: EditText
    private lateinit var amountCurrencyLabel: TextView
    private lateinit var amountInMainText: TextView
    private lateinit var originalAmountStatic: TextView
    private lateinit var descriptionEdit: EditText
    private lateinit var typeSpinner: Spinner
    private lateinit var dateEdit: EditText

    private var selectedYear: Int = 0
    private var selectedMonth: String = ""
    private var expenseDocumentId: String = ""

    private var originalCurrency: String = "EUR"
    private var originalAmount: Double = 0.0
    private var originalDateStr: String = ""
    private var expenseType: String = "expense"
    private var expenseCategory: String = ""
    private var expenseDescription: String = ""

    private var mainCurrency: String = "EUR"
    private var mainCurrencyLoaded = false

    private val df2 by lazy { DecimalFormat("0.00", DecimalFormatSymbols(Locale.US)) }
    private val eurRatesCache =
        mutableMapOf<String, Map<String, Double>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_expense_details_wallet)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        selectedYear = intent.getIntExtra("selectedYear", 0)
        selectedMonth = intent.getStringExtra("selectedMonth") ?: ""
        expenseDocumentId = intent.getStringExtra("expenseId") ?: ""

        setupViews()
        setupTopBarBehavior()
        updateTopIcons()

        fetchUserMainCurrency()
        fetchExpenseDoc()
    }

    private fun setupViews() {
        editButton = findViewById(R.id.editButton)

        categoryText = findViewById(R.id.expenseCategory)
        amountText = findViewById(R.id.expenseAmount)
        descriptionText = findViewById(R.id.expenseDescription)
        typeText = findViewById(R.id.expenseType)
        dateText = findViewById(R.id.expenseDate)

        categorySpinner = findViewById(R.id.expenseCategorySpinner)
        amountEdit = findViewById(R.id.expenseAmountEdit)
        amountCurrencyLabel = findViewById(R.id.amountCurrencyLabel)
        amountInMainText = findViewById(R.id.amountInMainText)
        originalAmountStatic = findViewById(R.id.originalAmountStatic)
        descriptionEdit = findViewById(R.id.expenseDescriptionEdit)
        typeSpinner = findViewById(R.id.expenseTypeSpinner)
        dateEdit = findViewById(R.id.expenseDateEdit)

        categorySpinner.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, Categories.categoryList
        )

        val types = listOf("Expense", "Income")
        typeSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, types)

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

                updatePreviewInMain()
            }
        })

        dateEdit.setOnClickListener {
            val cal = Calendar.getInstance()
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
            try {
                format.parse(dateEdit.text.toString())?.let { cal.time = it }
            } catch (_: Exception) {
            }
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH)
            val d = cal.get(Calendar.DAY_OF_MONTH)
            DatePickerDialog(this, { _, yy, mm, dd ->
                val newDate = String.format("%04d-%02d-%02d", yy, mm + 1, dd)
                dateEdit.setText(newDate)
                updatePreviewInMain()
            }, y, m, d).show()
        }
    }

    private fun setupTopBarBehavior() {
        val back = findViewById<ImageButton>(R.id.backButton)
        back.setOnClickListener { if (isEditMode) toggleEditMode(false) else onBackPressedDispatcher.onBackPressed() }
        editButton.setOnClickListener { if (!isEditMode) showOverflowMenu() else saveData() }
    }

    private fun updateTopIcons() {
        val back = findViewById<ImageButton>(R.id.backButton)
        if (isEditMode) {
            editButton.setImageResource(R.drawable.ic_save)
            back.setImageResource(R.drawable.ic_remove)
        } else {
            editButton.setImageResource(R.drawable.ic_more_vert)
            back.setImageResource(R.drawable.ic_chevron_left)
        }
    }

    private fun showOverflowMenu() {
        PopupMenu(this, editButton).apply {
            menu.add(0, MENU_EDIT, 0, getString(R.string.edit))
            menu.add(0, MENU_DELETE, 1, getString(R.string.delete))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> {
                        toggleEditMode(true); true
                    }

                    MENU_DELETE -> {
                        deleteExpense(); true
                    }

                    else -> false
                }
            }
            show()
        }
    }

    private fun toggleEditMode(enable: Boolean) {
        isEditMode = enable
        editButton.setImageResource(if (enable) R.drawable.ic_save else R.drawable.ic_more_vert)

        val viewVis = if (enable) View.GONE else View.VISIBLE
        val editVis = if (enable) View.VISIBLE else View.GONE

        categoryText.visibility = viewVis
        typeText.visibility = viewVis
        amountText.visibility = viewVis
        descriptionText.visibility = viewVis
        dateText.visibility = viewVis

        findViewById<View>(R.id.amountEditRow).visibility = editVis
        originalAmountStatic.visibility = editVis
        amountInMainText.visibility = editVis

        categorySpinner.visibility = editVis
        typeSpinner.visibility = editVis
        descriptionEdit.visibility = editVis
        dateEdit.visibility = editVis

        updateTopIcons()

        if (enable) updatePreviewInMain()
    }

    private fun fetchExpenseDoc() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (selectedYear == 0 || selectedMonth.isEmpty() || expenseDocumentId.isEmpty()) {
            Toast.makeText(this, "Missing expense path.", Toast.LENGTH_SHORT)
                .show(); finish(); return
        }

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .collection("expenses").document(selectedYear.toString())
            .collection(selectedMonth).document(expenseDocumentId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Expense not found.", Toast.LENGTH_SHORT)
                        .show(); finish(); return@addOnSuccessListener
                }
                bindFromDoc(doc)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load expense.", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun bindFromDoc(doc: DocumentSnapshot) {
        originalCurrency = (doc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
        originalAmount = readAmount(doc.get("amount"))
        originalDateStr = doc.getString("date") ?: ""
        expenseType = (doc.getString("type") ?: "expense").lowercase(Locale.ENGLISH)
        expenseCategory = doc.getString("category") ?: ""
        expenseDescription = doc.getString("description") ?: ""

        findViewById<TextView>(R.id.topBarTitle).text =
            if (expenseType == "income") "Income Details" else "Expense Details"
        amountText.text = formatAmountSigned(originalAmount, originalCurrency, expenseType)
        dateText.text = originalDateStr
        categoryText.text = expenseCategory
        typeText.text = expenseType.replaceFirstChar { it.uppercase() }
        descriptionText.text = expenseDescription

        amountCurrencyLabel.text = originalCurrency
        originalAmountStatic.text = "Original: ${df2.format(originalAmount)} $originalCurrency"
        descriptionEdit.setText(expenseDescription)
        dateEdit.setText(originalDateStr)

        val catPos = (categorySpinner.adapter as ArrayAdapter<String>).getPosition(expenseCategory)
        if (catPos >= 0) categorySpinner.setSelection(catPos)
        val typePos =
            (typeSpinner.adapter as ArrayAdapter<String>).getPosition(expenseType.replaceFirstChar { it.uppercase() })
        if (typePos >= 0) typeSpinner.setSelection(typePos)

        amountEdit.setText(df2.format(originalAmount))
    }

    private fun fetchUserMainCurrency() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .get()
            .addOnSuccessListener {
                mainCurrency = (it.getString("mainCurrency") ?: "EUR").uppercase(Locale.ENGLISH)
                mainCurrencyLoaded = true
                updatePreviewInMain()
            }
            .addOnFailureListener {
                mainCurrency = "EUR"
                mainCurrencyLoaded = true
            }
    }

    private fun updatePreviewInMain() {
        if (!isEditMode || !mainCurrencyLoaded) return
        val amount = amountEdit.text?.toString()?.replace(",", ".")?.toDoubleOrNull() ?: return
        val dateStr = dateEdit.text?.toString()?.takeIf { it.isNotBlank() } ?: originalDateStr
        if (dateStr.isBlank()) return

        CoroutineScope(Dispatchers.Main).launch {
            val snap = getEurRates(dateStr) ?: run {
                amountInMainText.text = "≈ —"
                return@launch
            }
            val value = convertOrigToMain(amount, originalCurrency, mainCurrency, snap)
            amountInMainText.text = "≈ ${df2.format(value)} $mainCurrency (as of ${snap.first})"
        }
    }

    private fun saveData() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val editedAmountOrig =
            amountEdit.text.toString().trim().replace(",", ".").toDoubleOrNull() ?: 0.0
        val newDescription = descriptionEdit.text.toString().trim()
        val newCategory = categorySpinner.selectedItem.toString()
        val newType = typeSpinner.selectedItem.toString().lowercase(Locale.ENGLISH)
        val dateStr = dateEdit.text.toString().ifBlank { originalDateStr }
        val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(dateStr)!!
        val dayTs = Timestamp(parsedDate)

        val newYear = dateStr.substring(0, 4)
        val newMonth = SimpleDateFormat("MMMM", Locale.ENGLISH).format(parsedDate)

        val pathChanged = (newYear != selectedYear.toString()) || (newMonth != selectedMonth)
        val db = FirebaseFirestore.getInstance()
        val oldRef = db.collection("users").document(uid)
            .collection("expenses").document(selectedYear.toString())
            .collection(selectedMonth).document(expenseDocumentId)
        val newRef = db.collection("users").document(uid)
            .collection("expenses").document(newYear)
            .collection(newMonth).document(expenseDocumentId)

        CoroutineScope(Dispatchers.Main).launch {
            val patch = mutableMapOf<String, Any>(
                "amount" to editedAmountOrig,
                "currency" to originalCurrency,
                "description" to newDescription,
                "category" to newCategory,
                "type" to newType,
                "date" to dateStr,
                "timestamp" to Timestamp.now(),
                "dayTimestamp" to dayTs
            )

            val snap = getEurRates(dateStr) ?: getEurRates(null)
            snap?.let { s ->
                val curToEur = curToEurRate(originalCurrency, s.second)
                if (curToEur != null) {
                    val amountBase = round2(editedAmountOrig * curToEur)
                    patch["baseCurrency"] = "EUR"
                    patch["amountBase"] = amountBase
                    patch["fx"] = mapOf(
                        "rate" to curToEur,
                        "asOf" to s.first,
                        "provider" to "frankfurter"
                    )
                }
            }

            try {
                if (pathChanged) {
                    val b = db.batch()
                    b.set(newRef, patch, SetOptions.merge())
                    b.delete(oldRef)
                    b.commit().await()
                } else {
                    newRef.set(patch, SetOptions.merge()).await()
                }

                val latestSnap = db.collection("users").document(uid)
                    .collection("latest")
                    .whereEqualTo("expenseId", expenseDocumentId)
                    .get().await()
                if (!latestSnap.isEmpty) {
                    val b2 = db.batch()
                    latestSnap.documents.forEach { b2.set(it.reference, patch, SetOptions.merge()) }
                    b2.commit().await()
                }

                Toast.makeText(this@ExpenseDetailsWallet, "Expense updated", Toast.LENGTH_SHORT)
                    .show()

                selectedYear = newYear.toInt(); selectedMonth = newMonth
                originalAmount = editedAmountOrig
                originalDateStr = dateStr
                expenseType = newType
                expenseCategory = newCategory
                expenseDescription = newDescription

                bindViewModeFromState()
                toggleEditMode(false)
            } catch (e: Exception) {
                Toast.makeText(
                    this@ExpenseDetailsWallet,
                    "Failed: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun bindViewModeFromState() {
        findViewById<TextView>(R.id.topBarTitle).text =
            if (expenseType == "income") "Income Details" else "Expense Details"
        amountText.text = formatAmountSigned(originalAmount, originalCurrency, expenseType)
        dateText.text = originalDateStr
        categoryText.text = expenseCategory
        typeText.text = expenseType.replaceFirstChar { it.uppercase() }
        descriptionText.text = expenseDescription
    }

    private fun deleteExpense() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance()
            .collection("users").document(uid).collection("expenses")
            .document(selectedYear.toString()).collection(selectedMonth)
            .document(expenseDocumentId)
            .delete()
            .addOnSuccessListener {
                FirebaseFirestore.getInstance()
                    .collection("users").document(uid).collection("latest")
                    .whereEqualTo("expenseId", expenseDocumentId)
                    .get()
                    .addOnSuccessListener { snap ->
                        val b = FirebaseFirestore.getInstance().batch()
                        for (d in snap.documents) b.delete(d.reference)
                        b.commit().addOnSuccessListener {
                            Toast.makeText(this, "Successfully removed!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Deleted, but failed to clean latest: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private suspend fun getEurRates(asOf: String?): Pair<String, Map<String, Double>>? =
        withContext(Dispatchers.IO) {
            val key = asOf ?: "latest"
            eurRatesCache[key]?.let { return@withContext (asOf ?: "latest") to it }

            var conn: HttpURLConnection? = null
            try {
                val url = if (asOf != null)
                    URL("https://api.frankfurter.dev/v1/$asOf?from=EUR")
                else
                    URL("https://api.frankfurter.dev/v1/latest?from=EUR")

                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val dateUsed = json.optString("date", asOf ?: "latest")
                val rj = json.optJSONObject("rates") ?: return@withContext null
                val map = mutableMapOf<String, Double>()
                val it = rj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    map[k.uppercase(Locale.ENGLISH)] = rj.getDouble(k)
                }
                eurRatesCache[asOf ?: "latest"] = map
                dateUsed to map
            } catch (_: Exception) {
                null
            } finally {
                conn?.disconnect()
            }
        }

    private fun curToEurRate(cur: String, eurToMap: Map<String, Double>): Double? {
        if (cur.equals("EUR", true)) return 1.0
        val eurToCur = eurToMap[cur.uppercase(Locale.ENGLISH)] ?: return null
        return 1.0 / eurToCur
    }

    private fun convertOrigToMain(
        amount: Double,
        orig: String,
        main: String,
        snap: Pair<String, Map<String, Double>>
    ): Double {
        val eurTo = snap.second
        val curToEur = curToEurRate(orig, eurTo) ?: return Double.NaN
        val eurToMain =
            if (main.equals("EUR", true)) 1.0 else (eurTo[main.uppercase(Locale.ENGLISH)]
                ?: return Double.NaN)
        return amount * curToEur * eurToMain
    }

    private fun formatAmountSigned(amount: Double, currency: String, type: String): String {
        val v = if (type.equals("expense", true)) -amount else amount
        return "${df2.format(v)} $currency"
    }

    private fun readAmount(raw: Any?): Double = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun round2(v: Double) = round(v * 100.0) / 100.0

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_DELETE = 2
    }
}
