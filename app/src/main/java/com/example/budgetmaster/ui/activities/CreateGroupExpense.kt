package com.example.budgetmaster.ui.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.components.BudgetMemberItem
import com.example.budgetmaster.ui.components.BudgetSplitMembersAdapter
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

    private lateinit var membersRecycler: RecyclerView
    private lateinit var selectAllCheckbox: CheckBox
    private val membersList = mutableListOf<BudgetMemberItem>()
    private val selectedMembers = mutableSetOf<String>()

    private lateinit var dateInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var amountInput: TextInputEditText

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

        // IME/system bars padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, max(sys.bottom, ime.bottom))
            insets
        }

        // Get budget data
        budgetId = intent.getStringExtra("budgetId") ?: run {
            Toast.makeText(this, "No budget provided", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        budgetName = intent.getStringExtra("budgetName") ?: "Unknown Budget"

        // Views
        dateInput = findViewById(R.id.dateInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        amountInput = findViewById(R.id.amountInput)
        membersRecycler = findViewById(R.id.membersRecycler)
        selectAllCheckbox = findViewById(R.id.selectAllCheckbox)

        // Date
        prefillTodayDate()
        dateInput.setOnClickListener { showDatePicker() }

        // Recycler
        membersRecycler.layoutManager = LinearLayoutManager(this)

        // Amount input: recompute on text change; pretty-print on blur/Done
        amountInput.addTextChangedListener(
            afterTextChanged = {
                if (suppressAmountWatcher) return@addTextChangedListener

                val txt = amountInput.text?.toString().orEmpty()
                var normalized = txt

                // 1) Normalize commas to dots for the UI/content
                if (normalized.contains(',')) {
                    normalized = normalized.replace(',', '.')
                }

                // 2) Ensure only a single dot as decimal separator (optional but nice)
                val firstDot = normalized.indexOf('.')
                if (firstDot != -1) {
                    val withoutExtraDots =
                        normalized.substring(0, firstDot + 1) +
                                normalized.substring(firstDot + 1).replace(".", "")
                    normalized = withoutExtraDots
                }

                if (normalized != txt) {
                    suppressAmountWatcher = true
                    val cursor = amountInput.selectionStart.coerceAtLeast(0)
                    amountInput.setText(normalized)
                    // Keep cursor near where the user was typing
                    amountInput.setSelection(normalized.length.coerceAtMost(cursor))
                    suppressAmountWatcher = false
                }

                // Your existing logic
                recomputeSharesEqual()
                if (::splitAdapter.isInitialized) {
                    splitAdapter.refreshVisibleSharesExcept(membersRecycler, null)
                }
            }
        )

        amountInput.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) normalizeTotalField()
        }
        amountInput.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                normalizeTotalField()
                v.clearFocus()
                true
            } else false
        }

        // Select All
        selectAllCheckbox.setOnCheckedChangeListener { _, isChecked ->
            if (membersList.isEmpty()) return@setOnCheckedChangeListener
            selectedMembers.clear()
            if (isChecked) selectedMembers.addAll(membersList.map { it.uid })
            recomputeSharesEqual()
            if (::splitAdapter.isInitialized) splitAdapter.notifyDataSetChanged()
        }

        // Save
        findViewById<View>(R.id.saveExpenseBtn).setOnClickListener { saveGroupExpense() }

        // Load members + build adapter
        loadBudgetMembers()
    }

    // ---------- Amount helpers ----------
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

    // ---------- Members + shares ----------
    private fun loadBudgetMembers() {
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->
                val memberIds = doc.get("members") as? List<String> ?: emptyList()
                budgetName = doc.getString("name") ?: budgetName

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
                                // Default: select all
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
                                        splitAdapter.refreshVisibleSharesExcept(
                                            membersRecycler,
                                            null
                                        )
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
                                    onStartEditing = { /* no-op */ },
                                    onStopEditing = { /* no-op */ }
                                )

                                membersRecycler.adapter = splitAdapter
                                splitAdapter.notifyDataSetChanged()
                                syncSelectAllCheckbox()
                            }
                        }
                }
            }
    }

    /** Keep "Select all" in sync with current selection */
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

    /** Equal split into sharesByUid based on selection and current total. */
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

    /** Keep total constant while one share is edited. */
    private fun applyBalancedEdit(editedUid: String, newValue: Double, total: Double) {
        val sel = selectedMembers.toList()
        if (sel.isEmpty() || !sel.contains(editedUid)) return

        val others = sel.filter { it != editedUid }
        val remaining = round2(total - newValue)

        if (others.isEmpty()) {
            sharesByUid[editedUid] = round2(total)
            return
        }
        if (remaining <= 0.0) return

        val perOther = round2(remaining / others.size)
        var running = 0.0
        others.forEachIndexed { idx, uid ->
            val v = if (idx == others.lastIndex) {
                round2(remaining - running)
            } else {
                running += perOther
                perOther
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
            Toast.makeText(this, "Select at least one participant", Toast.LENGTH_SHORT).show()
            return
        }

        val paidShares = buildPaidShares(amount)

        val expenseData = hashMapOf(
            "amount" to amount,
            "category" to "No Category",
            "description" to description,
            "date" to dateStr,       // yyyy-MM-dd
            "timestamp" to Timestamp.now(),
            "type" to "expense",
            "createdBy" to uid,
            "paidFor" to selectedMembers.toList(),
            "paidShares" to paidShares            // NEW
        )

        db.collection("budgets").document(budgetId)
            .collection("expenses")
            .add(expenseData)
            .addOnSuccessListener {
                Toast.makeText(this, "Expense added to group", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ---------- Date ----------
    private fun prefillTodayDate() {
        val today = LocalDate.now()
        dateInput.setText(today.format(DateTimeFormatter.ISO_LOCAL_DATE)) // yyyy-MM-dd
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val currentDate = dateInput.text?.toString()
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE
        try {
            if (!currentDate.isNullOrEmpty()) {
                val parsedDate = LocalDate.parse(currentDate, formatter)
                calendar.set(parsedDate.year, parsedDate.monthValue - 1, parsedDate.dayOfMonth)
            }
        } catch (_: Exception) { /* ignore */
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
