package com.example.budgetmaster.ui.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.utils.BudgetMemberItem
import com.example.budgetmaster.utils.BudgetSplitMembersAdapter
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.round

class CreateGroupExpense : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private lateinit var budgetId: String
    private lateinit var budgetName: String
    private lateinit var amountCurrencyText: TextView

    private var budgetCurrency: String = "EUR" // default, will be fetched from budget

    private lateinit var membersRecycler: RecyclerView
    private lateinit var selectAllCheckbox: CheckBox
    private val membersList = mutableListOf<BudgetMemberItem>()
    private val selectedMembers = mutableSetOf<String>()

    private lateinit var dateInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var amountInput: TextInputEditText

    // exchange component
    private lateinit var exchangeInput: TextInputEditText
    private lateinit var exchangeSpinner: Spinner
    private lateinit var exchangeSwap: ShapeableImageView
    private lateinit var exchangeComponent: View
    private var currencies: List<String> = emptyList()

    private lateinit var splitAdapter: BudgetSplitMembersAdapter

    private val sharesByUid = linkedMapOf<String, Double>()

    private val syms = DecimalFormatSymbols(Locale.getDefault()).apply {
        decimalSeparator = '.'
        groupingSeparator = ' '
    }
    private val df2 = DecimalFormat("0.00").apply {
        decimalFormatSymbols = syms
        isGroupingUsed = false
    }

    private var suppressAmountWatcher = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_group_expense)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, max(sys.bottom, ime.bottom))
            insets
        }

        budgetId = intent.getStringExtra("budgetId") ?: run {
            Toast.makeText(this, "No budget provided", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        budgetName = intent.getStringExtra("budgetName") ?: "Unknown Budget"

        amountCurrencyText = findViewById(R.id.amountCurrencyText)
        dateInput = findViewById(R.id.dateInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        amountInput = findViewById(R.id.amountInput)
        membersRecycler = findViewById(R.id.membersRecycler)
        selectAllCheckbox = findViewById(R.id.selectAllCheckbox)

        // exchange views
        exchangeComponent = findViewById(R.id.exchangeOfficeContainer)
        exchangeInput = findViewById(R.id.exchangeInput)
        exchangeSpinner = findViewById(R.id.exchangeSpinner)
        exchangeSwap = findViewById(R.id.exchangeSwapIcon)

        prefillTodayDate()
        dateInput.setOnClickListener { showDatePicker() }

        membersRecycler.layoutManager = LinearLayoutManager(this)
        val exchangeHeader = findViewById<View>(R.id.exchangeOfficeHeader)
        val exchangeContent = findViewById<View>(R.id.exchangeOfficeContent)
        val exchangeArrow = findViewById<ImageView>(R.id.exchangeOfficeArrow)

        var expanded = false

        exchangeHeader.setOnClickListener {
            expanded = !expanded
            exchangeContent.visibility = if (expanded) View.VISIBLE else View.GONE
            // rotate arrow for effect
            exchangeArrow.animate().rotation(if (expanded) 180f else 0f).setDuration(200).start()
        }

        amountInput.addTextChangedListener(afterTextChanged = {
            if (suppressAmountWatcher) return@addTextChangedListener
            val txt = amountInput.text?.toString().orEmpty()
            var normalized = txt.replace(',', '.')
            val firstDot = normalized.indexOf('.')
            if (firstDot != -1) {
                normalized = normalized.substring(0, firstDot + 1) +
                        normalized.substring(firstDot + 1).replace(".", "")
            }
            if (normalized != txt) {
                suppressAmountWatcher = true
                val cursor = amountInput.selectionStart.coerceAtLeast(0)
                amountInput.setText(normalized)
                amountInput.setSelection(normalized.length.coerceAtMost(cursor))
                suppressAmountWatcher = false
            }
            recomputeSharesEqual()
            if (::splitAdapter.isInitialized) splitAdapter.notifyDataSetChanged()
        })
        amountInput.setOnFocusChangeListener { v, hasFocus -> if (!hasFocus) normalizeTotalField() }
        amountInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                normalizeTotalField()
                v.clearFocus()
                true
            } else false
        }

        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (membersList.isEmpty()) return@setOnCheckedChangeListener
            selectedMembers.clear()
            if (isChecked) selectedMembers.addAll(membersList.map { it.uid })
            recomputeSharesEqual()
            if (::splitAdapter.isInitialized) splitAdapter.notifyDataSetChanged()
        }

        findViewById<View>(R.id.saveExpenseBtn).setOnClickListener { saveGroupExpense() }

        // load members + budget currency
        loadBudgetMembers()

        // exchange component setup
        loadCurrencies()
        exchangeSwap.setOnClickListener {
            triggerConversion()
        }
    }

    private fun triggerConversion() {
        val rawAmount = exchangeInput.text?.toString()?.toDoubleOrNull()
        val fromCode = exchangeSpinner.selectedItem?.toString()
        if (rawAmount != null && !fromCode.isNullOrBlank()) {
            if (fromCode == budgetCurrency) {
                amountInput.setText(df2.format(rawAmount))
                return
            }

            convertCurrency(rawAmount, fromCode, budgetCurrency) { converted ->
                runOnUiThread {
                    if (converted != null) {
                        amountInput.setText(df2.format(converted))
                    } else {
                        amountInput.setText(df2.format(rawAmount))
                    }
                }
            }
        }
    }

    private fun loadCurrencies() {
        Thread {
            try {
                val url = URL("https://api.frankfurter.app/currencies")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                val reader = conn.inputStream.bufferedReader()
                val response = reader.readText()
                reader.close()
                val regex = Regex("\"([A-Z]{3})\":\"[^\"]+\"")
                val found = regex.findAll(response).map { it.groupValues[1] }.sorted().toList()
                runOnUiThread {
                    currencies = found
                    val adapter =
                        ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    exchangeSpinner.adapter = adapter
                    val defaultIdx = currencies.indexOf(budgetCurrency)
                    if (defaultIdx >= 0) exchangeSpinner.setSelection(defaultIdx)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to load currencies", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun convertCurrency(
        amount: Double,
        from: String,
        to: String,
        callback: (Double?) -> Unit
    ) {
        Thread {
            try {
                val url = URL("https://api.frankfurter.app/latest?amount=$amount&from=$from&to=$to")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                val reader = conn.inputStream.bufferedReader()
                val response = reader.readText()
                reader.close()
                val regex = Regex("\"$to\":([0-9.]+)")
                val match = regex.find(response)
                val value = match?.groupValues?.get(1)?.toDoubleOrNull()
                callback(value)
            } catch (e: Exception) {
                callback(null)
            }
        }.start()
    }

    private fun parseFlexible(txt: String?): Double? {
        val s = (txt ?: "").trim().replace(" ", "").replace(',', '.')
        return s.toDoubleOrNull()
    }

    private fun readTotalOrZero(): Double = parseFlexible(amountInput.text?.toString()) ?: 0.0
    private fun normalizeTotalField() {
        val v = readTotalOrZero()
        suppressAmountWatcher = true
        amountInput.setText(df2.format(v))
        amountInput.setSelection(amountInput.text?.length ?: 0)
        suppressAmountWatcher = false
    }

    private fun loadBudgetMembers() {
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->
                val memberIds = doc.get("members") as? List<String> ?: emptyList()
                budgetName = doc.getString("name") ?: budgetName
                budgetCurrency = (doc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
                amountCurrencyText.text = budgetCurrency

                if (memberIds.isEmpty()) return@addOnSuccessListener

                var processed = 0
                membersList.clear()

                for (uid in memberIds) {
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                membersList.add(
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
                                selectedMembers.clear()
                                selectedMembers.addAll(memberIds)
                                recomputeSharesEqual()

                                splitAdapter = BudgetSplitMembersAdapter(
                                    members = membersList,
                                    selected = selectedMembers,
                                    sharesByUid = sharesByUid,
                                    totalProvider = { readTotalOrZero() },
                                    onCheckedChanged = { uid, checked ->
                                        if (checked) selectedMembers.add(uid) else selectedMembers.remove(
                                            uid
                                        )
                                        recomputeSharesEqual()
                                        splitAdapter.notifyDataSetChanged()
                                        syncSelectAllCheckbox()
                                    },
                                    onShareEditedValid = { editedUid, newValue ->
                                        val total = readTotalOrZero()
                                        applyBalancedEdit(editedUid, newValue, total)
                                        splitAdapter.refreshVisibleSharesExcept(
                                            membersRecycler,
                                            editedUid
                                        )
                                    },
                                    onStartEditing = { },
                                    onStopEditing = { }
                                )

                                membersRecycler.adapter = splitAdapter
                                splitAdapter.notifyDataSetChanged()
                                syncSelectAllCheckbox()
                            }
                        }
                }
            }
    }

    private fun syncSelectAllCheckbox() {
        selectAllCheckbox.setOnCheckedChangeListener(null)
        selectAllCheckbox.isChecked =
            selectedMembers.size == membersList.size && membersList.isNotEmpty()
        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (membersList.isEmpty()) return@setOnCheckedChangeListener
            selectedMembers.clear()
            if (isChecked) selectedMembers.addAll(membersList.map { it.uid })
            recomputeSharesEqual()
            if (::splitAdapter.isInitialized) splitAdapter.notifyDataSetChanged()
        }
    }

    private fun recomputeSharesEqual() {
        sharesByUid.clear()
        val total = readTotalOrZero()
        val sel = selectedMembers.toList()
        val count = sel.size
        if (count == 0) return

        val per = round2(total / count)
        var running = 0.0
        sel.forEachIndexed { index, uid ->
            val v = if (index == count - 1) {
                round2(total - running)
            } else {
                running += per
                per
            }
            sharesByUid[uid] = v
        }
    }

    private fun applyBalancedEdit(editedUid: String, newValue: Double, total: Double) {
        val sel = selectedMembers.toList()
        if (sel.isEmpty() || !sel.contains(editedUid)) return

        val others = sel.filter { it != editedUid }
        val remaining = round2(total - newValue)

        if (others.isEmpty()) {
            sharesByUid[editedUid] = round2(total); return
        }
        if (remaining <= 0.0) return

        val perOther = round2(remaining / others.size)
        var running = 0.0
        others.forEachIndexed { idx, uid ->
            val v = if (idx == others.lastIndex) round2(remaining - running) else {
                running += perOther; perOther
            }
            sharesByUid[uid] = v
        }
        sharesByUid[editedUid] = round2(newValue)
    }

    private fun buildPaidShares(total: Double): Map<String, Double> {
        val sel = selectedMembers.toList()
        if (sel.isEmpty()) return emptyMap()

        val tmp = LinkedHashMap<String, Double>()
        var sum = 0.0
        sel.forEachIndexed { idx, uid ->
            val v = sharesByUid[uid] ?: round2(total / sel.size)
            val vv = if (idx == sel.lastIndex) round2(total - sum) else round2(v)
            sum = round2(sum + vv)
            tmp[uid] = vv
        }
        return tmp
    }

    private fun saveGroupExpense() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            Toast.makeText(this, "Not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        val description = descriptionInput.text?.toString()?.trim()
        val amount = parseFlexible(amountInput.text?.toString())
        val dateStr = dateInput.text?.toString()?.trim()

        if (description.isNullOrEmpty() || amount == null) {
            Toast.makeText(this, "Please enter all data", Toast.LENGTH_SHORT).show()
            return
        }
        if (amount <= 0.0 || dateStr.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter valid data", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedMembers.isEmpty()) {
            Toast.makeText(this, "Select at least one participant", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val paidShares = buildPaidShares(amount)
        val expenseData = hashMapOf(
            "amount" to amount,
            "category" to "No Category",
            "description" to description,
            "date" to dateStr,
            "timestamp" to Timestamp.now(),
            "type" to "expense",
            "createdBy" to uid,
            "paidFor" to selectedMembers.toList(),
            "paidShares" to paidShares
        )

        db.collection("budgets").document(budgetId)
            .collection("expenses")
            .add(expenseData)
            .addOnSuccessListener {
                val batch = db.batch()
                val serverTime = FieldValue.serverTimestamp()

                // --- totals (new unified format: "with" map, positive = receivable, negative = debt) ---
                paidShares.forEach { (participantUid, share) ->
                    if (participantUid == uid) return@forEach

                    // payer: positive share against participant
                    val payerTotalsRef = db.collection("budgets").document(budgetId)
                        .collection("totals").document(uid)
                    batch.set(
                        payerTotalsRef,
                        mapOf(
                            "with.$participantUid" to FieldValue.increment(share),
                            "updatedAt" to serverTime
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )

                    // participant: negative share against payer
                    val participantTotalsRef = db.collection("budgets").document(budgetId)
                        .collection("totals").document(participantUid)
                    batch.set(
                        participantTotalsRef,
                        mapOf(
                            "with.$uid" to FieldValue.increment(-share),
                            "updatedAt" to serverTime
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                }

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Expense added to group", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Saved expense but failed to update totals: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val picker = DatePickerDialog(
            this,
            { _, y, m, d ->
                val selected = LocalDate.of(y, m + 1, d)
                dateInput.setText(selected.format(DateTimeFormatter.ISO_LOCAL_DATE))
            },
            year, month, day
        )
        picker.show()
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
}
