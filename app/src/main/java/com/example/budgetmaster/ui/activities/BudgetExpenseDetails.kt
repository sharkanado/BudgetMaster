package com.example.budgetmaster.ui.activities

import android.app.DatePickerDialog
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetExpenseItem
import com.example.budgetmaster.ui.components.BudgetMemberItem
import com.example.budgetmaster.ui.components.BudgetMembersAdapter
import com.example.budgetmaster.ui.components.BudgetSplitMembersAdapter
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round

class BudgetExpenseDetails : AppCompatActivity() {

    private lateinit var amountView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var dateView: TextView
    private lateinit var paidByView: TextView
    private lateinit var paidByMailView: TextView

    private lateinit var amountEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var dateEdit: EditText
    private lateinit var amountCurrencyView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var editBtn: ImageButton
    private var budgetCurrency: String = "EUR"


    private lateinit var participantsRecycler: RecyclerView

    private val allMembers = mutableListOf<BudgetMemberItem>()
    private val selectedMembers = mutableSetOf<String>()
    private val participantsReadOnly = mutableListOf<BudgetMemberItem>()

    private lateinit var readOnlyAdapter: BudgetMembersAdapter
    private lateinit var splitAdapter: BudgetSplitMembersAdapter


    private var isEditMode = false
    private var isRowEditing = false

    private lateinit var expenseItem: BudgetExpenseItem
    private val userNames: MutableMap<String, String> = mutableMapOf()
    private val userEmails: MutableMap<String, String> = mutableMapOf()
    private var budgetId: String = ""


    private val db by lazy { FirebaseFirestore.getInstance() }

    private val sharesByUid = linkedMapOf<String, Double>()
    private val savedPaidShares = linkedMapOf<String, Double>()

    private val df2 = DecimalFormat("0.00").apply {
        decimalFormatSymbols = DecimalFormatSymbols(Locale.ENGLISH).apply {
            decimalSeparator = '.'
            groupingSeparator = ' '
        }
        isGroupingUsed = false
    }

    private var editSnapshot: EditSnapshot? = null

    // NEW: track if the current user is the creator (owner) of the expense
    private var isOwner: Boolean = false

    private data class EditSnapshot(
        val amount: String,
        val description: String,
        val date: String,
        val selected: List<String>,
        val shares: Map<String, Double>
    )

    private fun String.toAmount(): Double? = replace(',', '.').toDoubleOrNull()
    private fun round2(v: Double): Double = round(v * 100.0) / 100.0
    private fun EditText.str() = text?.toString().orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_expense_details)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(sys.left, sys.top, sys.right, max(sys.bottom, ime.bottom))
            insets
        }

        val item: BudgetExpenseItem? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("expenseItem", BudgetExpenseItem::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("expenseItem")
            }
        if (item == null) {
            Toast.makeText(this, "No expense data received", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        expenseItem = item

        budgetId = intent.getStringExtra("budgetId") ?: ""
        if (budgetId.isEmpty()) {
            Toast.makeText(this, "No budgetId provided", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        @Suppress("DEPRECATION")
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            intent.getSerializableExtra("userNames", HashMap::class.java)
        else
            intent.getSerializableExtra("userNames") as? HashMap<*, *>
                )?.let { map -> userNames.putAll(map as HashMap<String, String>) }

        selectedMembers.apply {
            clear()
            addAll(expenseItem.paidFor)
        }

        bindViews()

        // Determine ownership right after we have expenseItem
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        isOwner = (currentUid != null && currentUid == expenseItem.createdBy)
        // Show/hide the edit button based on ownership
        editBtn.visibility = if (isOwner) View.VISIBLE else View.GONE

        participantsRecycler.layoutManager = LinearLayoutManager(this)
        readOnlyAdapter = BudgetMembersAdapter(participantsReadOnly)
        splitAdapter = BudgetSplitMembersAdapter(
            members = allMembers,
            selected = selectedMembers,
            sharesByUid = sharesByUid,
            totalProvider = { amountEdit.str().toAmount() ?: expenseItem.amount },
            onCheckedChanged = { uid, checked ->
                if (checked) selectedMembers.add(uid) else selectedMembers.remove(uid)
                recomputeSharesEqual()
                splitAdapter.refreshVisibleSharesExcept(participantsRecycler, null)
            },
            onShareEditedValid = { editedUid, newValue ->
                val total = amountEdit.str().toAmount() ?: expenseItem.amount
                applyBalancedEdit(editedUid, newValue, total)
                splitAdapter.refreshVisibleSharesExcept(participantsRecycler, editedUid)
            },
            onStartEditing = { isRowEditing = true },
            onStopEditing = { isRowEditing = false }
        )

        setUiMode(false)
        participantsRecycler.adapter = readOnlyAdapter
        updateTopIcons()

        fillFieldsOnce()
        ensurePayerInfoLoaded(expenseItem.createdBy)

        recomputeSharesEqual()
        loadBudgetMembers()
        loadPaidSharesOnce()

        editBtn.setOnClickListener {
            if (!isOwner) {
                Toast.makeText(this, "You can edit only your own expense.", Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }
            if (!isEditMode) showOverflowMenu() else saveChanges()
        }
        backButton.setOnClickListener { if (isEditMode) cancelEdit() else finish() }
        dateEdit.setOnClickListener { showDatePicker() }

        amountEdit.addTextChangedListener(object : android.text.TextWatcher {
            private var suppress = false
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isEditMode || suppress) return
                val raw = s?.toString() ?: return

                if (raw.contains(',')) {
                    val cur = amountEdit.selectionStart
                    val fixed = raw.replace(',', '.')
                    suppress = true
                    amountEdit.setText(fixed)
                    amountEdit.setSelection(
                        (cur + (fixed.length - raw.length)).coerceIn(0, fixed.length)
                    )
                    suppress = false
                    return
                }
                val i = raw.indexOf('.')
                if (i != -1) {
                    val dedup = raw.substring(0, i + 1) + raw.substring(i + 1).replace(".", "")
                    if (dedup != raw) {
                        val cur = amountEdit.selectionStart
                        suppress = true
                        amountEdit.setText(dedup)
                        amountEdit.setSelection(cur.coerceIn(0, dedup.length))
                        suppress = false
                    }
                }

                val total = raw.toDoubleOrNull() ?: return
                amountEdit.error =
                    if (total <= 0.0) getString(R.string.error_negative_not_allowed) else null
                if (!isRowEditing && total > 0.0) {
                    recomputeSharesEqual()
                    splitAdapter.refreshVisibleSharesExcept(participantsRecycler, null)
                }
            }
        })

        amountEdit.filters = arrayOf<InputFilter>(object : InputFilter {
            private val pattern = Regex("^\\d+([.,][0-9]{0,2})?$")
            override fun filter(
                source: CharSequence?, start: Int, end: Int,
                dest: Spanned?, dstart: Int, dend: Int
            ): CharSequence? {
                val next =
                    dest?.replaceRange(dstart, dend, source?.subSequence(start, end) ?: "") ?: ""
                return if (pattern.matches(next)) null else ""
            }
        })

        amountEdit.setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) normalizeTotalField() }
        amountEdit.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                normalizeTotalField(); v.clearFocus(); true
            } else false
        }
    }

    private fun bindViews() {
        backButton = findViewById(R.id.backButton)
        editBtn = findViewById(R.id.editButton)

        amountView = findViewById(R.id.expenseAmount)
        amountEdit = findViewById(R.id.expenseAmountEdit)
        amountCurrencyView = findViewById(R.id.expenseAmountCurrency)   // ⬅️ add this

        descriptionView = findViewById(R.id.expenseDescription)
        dateView = findViewById(R.id.expenseDate)
        paidByView = findViewById(R.id.whoPaidName)
        paidByMailView = findViewById(R.id.whoPaidEmail)

        descriptionEdit = findViewById(R.id.expenseDescriptionEdit)
        dateEdit = findViewById(R.id.expenseDateEdit)

        participantsRecycler = findViewById(R.id.expenseParticipantsRecyclerView)
    }


    private fun updateTopIcons() {
        if (!isOwner) {
            // Ensure the edit button stays hidden for non-owners
            editBtn.visibility = View.GONE
            backButton.setImageResource(R.drawable.ic_chevron_left)
            return
        }
        if (isEditMode) {
            editBtn.setImageResource(R.drawable.ic_save)
            backButton.setImageResource(R.drawable.ic_remove)
        } else {
            editBtn.setImageResource(R.drawable.ic_more_vert)
            backButton.setImageResource(R.drawable.ic_chevron_left)
        }
    }

    private fun showOverflowMenu() {
        if (!isOwner) {
            Toast.makeText(this, "You can edit only your own expense.", Toast.LENGTH_SHORT).show()
            return
        }
        PopupMenu(this, editBtn).apply {
            menu.add(0, MENU_EDIT, 0, getString(R.string.edit))
            menu.add(0, MENU_DELETE, 1, getString(R.string.delete))
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_EDIT -> {
                        toggleEdit(true); true
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

    private fun setUiMode(edit: Boolean) {
        isEditMode = edit
        val visEdit = if (edit) View.VISIBLE else View.GONE
        val visView = if (edit) View.GONE else View.VISIBLE

        amountEdit.visibility = visEdit
        descriptionEdit.visibility = visEdit
        dateEdit.visibility = visEdit
        findViewById<View>(R.id.amountEditRow).visibility = visEdit

        amountView.visibility = visView
        amountView.visibility = visView
        descriptionView.visibility = visView
        dateView.visibility = visView

        paidByView.visibility = View.VISIBLE
        paidByMailView.visibility = View.VISIBLE

        updateTopIcons()
    }

    private fun loadBudgetMembers() {
        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->
                val memberIds = (doc.get("members") as? List<String>).orEmpty()
                allMembers.clear()
                if (memberIds.isEmpty()) {
                    participantsReadOnly.clear()
                    readOnlyAdapter.notifyDataSetChanged()
                    splitAdapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                var processed = 0
                memberIds.forEach { uid ->
                    db.collection("users").document(uid).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val name = userDoc.getString("name") ?: "Unknown"
                                val email = userDoc.getString("email") ?: ""
                                userNames[uid] = name
                                userEmails[uid] = email
                                allMembers.add(
                                    BudgetMemberItem(
                                        uid = uid,
                                        name = name,
                                        email = email,
                                        balance = 0.0
                                    )
                                )
                            }
                        }
                        .addOnCompleteListener {
                            processed++
                            if (processed == memberIds.size) {
                                selectedMembers.retainAll(memberIds.toSet())
                                if (selectedMembers.isEmpty()) selectedMembers.addAll(memberIds)
                                recomputeSharesEqual()
                                rebuildReadOnlyParticipants()
                                participantsRecycler.adapter =
                                    if (isEditMode) splitAdapter else readOnlyAdapter
                                readOnlyAdapter.notifyDataSetChanged()
                                splitAdapter.notifyDataSetChanged()
                            }
                        }
                }
            }

        db.collection("budgets").document(budgetId).get()
            .addOnSuccessListener { doc ->

                budgetCurrency = (doc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
                amountCurrencyView.text = budgetCurrency

            }
    }

    private fun loadPaidSharesOnce() {
        db.collection("budgets").document(budgetId)
            .collection("expenses").document(expenseItem.id)
            .get()
            .addOnSuccessListener { doc ->
                val map = doc.get("paidShares") as? Map<String, Number>
                savedPaidShares.clear()
                map?.forEach { (k, v) -> savedPaidShares[k] = round2(v.toDouble()) }

                if (savedPaidShares.isNotEmpty()) {
                    selectedMembers.clear()
                    selectedMembers.addAll(savedPaidShares.keys)
                    sharesByUid.clear()
                    sharesByUid.putAll(savedPaidShares)
                }
                rebuildReadOnlyParticipants()
                readOnlyAdapter.notifyDataSetChanged()
            }
    }

    private fun rebuildReadOnlyParticipants() {
        participantsReadOnly.clear()
        val byUid = allMembers.associateBy { it.uid }
        val source: Map<String, Double> =
            if (savedPaidShares.isNotEmpty()) savedPaidShares
            else {
                val sel = selectedMembers.toList()
                val count = sel.size
                if (count == 0) return
                val per = round2(expenseItem.amount / count)
                val tmp = LinkedHashMap<String, Double>()
                var running = 0.0
                sel.forEachIndexed { idx, uid ->
                    val v = if (idx == sel.lastIndex) round2(expenseItem.amount - running) else {
                        running = round2(running + per); per
                    }
                    tmp[uid] = v
                }
                tmp
            }

        source.forEach { (uid, share) ->
            byUid[uid]?.let { m -> participantsReadOnly.add(m.copy(balance = round2(share))) }
        }
    }

    private fun recomputeSharesEqual() {
        sharesByUid.clear()
        val total = amountEdit.str().toAmount() ?: expenseItem.amount
        val count = selectedMembers.size
        if (count == 0) return

        val per = round2(total / count)
        var running = 0.0
        selectedMembers.toList().forEachIndexed { i, uid ->
            val v = if (i == count - 1) round2(total - running) else {
                running += per; per
            }
            sharesByUid[uid] = v
        }
    }

    private fun applyBalancedEdit(editedUid: String, newValue: Double, total: Double) {
        val sel = selectedMembers.toList()
        if (sel.isEmpty() || editedUid !in sel) return

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

    private fun fillFieldsOnce() {
        val e = expenseItem
        amountView.text = df2.format(e.amount)
        descriptionView.text = e.description
        dateView.text = formatDate(e.date)

        paidByView.text = userNames[e.createdBy] ?: e.createdBy
        paidByMailView.text = userEmails[e.createdBy] ?: ""

        amountView.text = "${df2.format(e.amount)} $budgetCurrency"
        amountEdit.setText(df2.format(e.amount))
        amountCurrencyView.text = budgetCurrency

        descriptionEdit.setText(e.description)
        dateEdit.setText(e.date)

        rebuildReadOnlyParticipants()
        readOnlyAdapter.notifyDataSetChanged()
    }

    private fun toggleEdit(editMode: Boolean) {
        if (editMode && !isOwner) {
            Toast.makeText(this, "You can edit only your own expense.", Toast.LENGTH_SHORT).show()
            return
        }
        if (editMode) {
            editSnapshot = EditSnapshot(
                amount = amountEdit.str(),
                description = descriptionEdit.str(),
                date = dateEdit.str(),
                selected = selectedMembers.toList(),
                shares = HashMap(sharesByUid)
            )
            setUiMode(true)
            participantsRecycler.adapter = splitAdapter
            if (savedPaidShares.isNotEmpty()) {
                sharesByUid.clear(); sharesByUid.putAll(savedPaidShares)
            } else recomputeSharesEqual()
            splitAdapter.notifyDataSetChanged()
        } else {
            rebuildReadOnlyParticipants()
            setUiMode(false)
            participantsRecycler.adapter = readOnlyAdapter
            readOnlyAdapter.notifyDataSetChanged()
        }
    }

    private fun cancelEdit() {
        editSnapshot?.let { s ->
            amountEdit.setText(s.amount)
            descriptionEdit.setText(s.description)
            dateEdit.setText(s.date)
            selectedMembers.clear(); selectedMembers.addAll(s.selected)
            sharesByUid.clear(); sharesByUid.putAll(s.shares)
        } ?: run {
            amountEdit.setText(df2.format(expenseItem.amount))
            descriptionEdit.setText(expenseItem.description)
            dateEdit.setText(expenseItem.date)
            selectedMembers.clear(); selectedMembers.addAll(expenseItem.paidFor)
            recomputeSharesEqual()
        }
        toggleEdit(false)
    }

    private fun saveChanges() {
        if (!isOwner) {
            Toast.makeText(this, "You can edit only your own expense.", Toast.LENGTH_SHORT).show()
            return
        }

        val newAmount = amountEdit.str().toAmount()
        if (newAmount == null || newAmount <= 0.0) {
            Toast.makeText(this, getString(R.string.error_negative_not_allowed), Toast.LENGTH_SHORT)
                .show()
            return
        }

        val newDescription = descriptionEdit.str().trim()
        val newDate = dateEdit.str().trim()
        if (newDate.isBlank() || !isValidDate(newDate)) {
            Toast.makeText(this, "Enter a valid date (yyyy-MM-dd)", Toast.LENGTH_SHORT).show()
            return
        }
        if (selectedMembers.isEmpty()) {
            Toast.makeText(this, "Select at least one participant", Toast.LENGTH_SHORT).show()
            return
        }

        val normalizedPaidShares = run {
            val sel = selectedMembers.toList()
            val tmp = LinkedHashMap<String, Double>()
            var sum = 0.0
            sel.forEachIndexed { idx, uid ->
                val v = sharesByUid[uid] ?: round2(newAmount / sel.size)
                val vv = if (idx == sel.lastIndex) round2(newAmount - sum) else round2(v)
                sum = round2(sum + vv)
                tmp[uid] = vv
            }
            tmp
        }

        expenseItem = expenseItem.copy(
            amount = newAmount,
            description = newDescription,
            date = newDate,
            paidFor = selectedMembers.toList()
        )

        val updates = mapOf(
            "amount" to newAmount,
            "description" to newDescription,
            "date" to newDate,
            "paidFor" to selectedMembers.toList(),
            "paidShares" to normalizedPaidShares,
            "timestamp" to Timestamp.now()
        )

        val budgetRef = db.collection("budgets").document(budgetId)
        val expenseRef = budgetRef.collection("expenses").document(expenseItem.id)
        val splitsRef = budgetRef.collection("expenseSplits").document(expenseItem.id)
        val totalsCol = budgetRef.collection("totals")
        val payer = expenseItem.createdBy

        db.runTransaction { tx ->
            val old = tx.get(splitsRef)
            val oldPayer = old.getString("payer") ?: payer
            val oldShares: Map<String, Double> =
                (old.get("shares") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val id = k?.toString() ?: return@mapNotNull null
                    val d = (v as? Number)?.toDouble() ?: return@mapNotNull null
                    id to d
                }?.toMap().orEmpty()

            var oldOthers = 0.0
            oldShares.forEach { (uid, share) ->
                if (uid == oldPayer) return@forEach
                oldOthers += share
                tx.set(
                    totalsCol.document(uid),
                    mapOf(
                        "debt" to FieldValue.increment(-share),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            if (oldOthers != 0.0) {
                tx.set(
                    totalsCol.document(oldPayer),
                    mapOf(
                        "receivable" to FieldValue.increment(-oldOthers),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }

            var newOthers = 0.0
            normalizedPaidShares.forEach { (uid, share) ->
                if (uid == payer) return@forEach
                newOthers += share
                tx.set(
                    totalsCol.document(uid),
                    mapOf(
                        "debt" to FieldValue.increment(share),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            if (newOthers != 0.0) {
                tx.set(
                    totalsCol.document(payer),
                    mapOf(
                        "receivable" to FieldValue.increment(newOthers),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }

            tx.update(expenseRef, updates)

            tx.set(splitsRef, mapOf("payer" to payer, "shares" to normalizedPaidShares))

            null
        }.addOnSuccessListener {
            savedPaidShares.clear()
            savedPaidShares.putAll(normalizedPaidShares)
            rebuildReadOnlyParticipants()
            readOnlyAdapter.notifyDataSetChanged()
            fillFieldsOnce()
            toggleEdit(false)
            Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteExpense() {
        if (!isOwner) {
            Toast.makeText(this, "You can delete only your own expense.", Toast.LENGTH_SHORT).show()
            return
        }

        val budgetRef = db.collection("budgets").document(budgetId)
        val expenseRef = budgetRef.collection("expenses").document(expenseItem.id)
        val splitsRef = budgetRef.collection("expenseSplits").document(expenseItem.id)
        val totalsCol = budgetRef.collection("totals")

        db.runTransaction { tx ->
            val old = tx.get(splitsRef)
            val payer = old.getString("payer") ?: expenseItem.createdBy
            val shares: Map<String, Double> =
                (old.get("shares") as? Map<*, *>)?.mapNotNull { (k, v) ->
                    val id = k?.toString() ?: return@mapNotNull null
                    val d = (v as? Number)?.toDouble() ?: return@mapNotNull null
                    id to d
                }?.toMap().orEmpty()

            var others = 0.0
            shares.forEach { (uid, share) ->
                if (uid == payer) return@forEach
                others += share
                tx.set(
                    totalsCol.document(uid),
                    mapOf(
                        "debt" to FieldValue.increment(-share),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            if (others != 0.0) {
                tx.set(
                    totalsCol.document(payer),
                    mapOf(
                        "receivable" to FieldValue.increment(-others),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }

            tx.delete(expenseRef)
            tx.delete(splitsRef)
            null
        }.addOnSuccessListener {
            Toast.makeText(this, "Expense deleted", Toast.LENGTH_SHORT).show()
            finish()
        }.addOnFailureListener { e ->
            Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        try {
            val current = dateEdit.str().takeIf { it.isNotBlank() }
            if (current != null && isValidDate(current)) {
                cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).parse(current)!!
            }
        } catch (_: Exception) {
        }

        DatePickerDialog(this, { _, yy, mm, dd ->
            dateEdit.setText(String.format(Locale.ENGLISH, "%04d-%02d-%02d", yy, mm + 1, dd))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun ensurePayerInfoLoaded(uid: String) {
        userNames[uid]?.let { paidByView.text = it }
        userEmails[uid]?.let { paidByMailView.text = it }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: userNames[uid] ?: "Unknown"
                val email = doc.getString("email") ?: userEmails[uid] ?: ""
                userNames[uid] = name
                userEmails[uid] = email
                paidByView.text = name
                paidByMailView.text = email
            }
            .addOnFailureListener { exception ->
                Log.w("BudgetExpenseDetails", "Failed to fetch payer info for uid=$uid", exception)
            }
    }

    private fun normalizeTotalField() {
        val v = amountEdit.str().toAmount() ?: 0.0
        amountEdit.setText(df2.format(v))
        amountEdit.setSelection(amountEdit.text?.length ?: 0)
    }

    private fun formatDate(dateStr: String): String = try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val output = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        output.format(input.parse(dateStr)!!)
    } catch (_: Exception) {
        dateStr
    }

    private fun isValidDate(dateStr: String): Boolean = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { isLenient = false }
            .parse(dateStr); true
    } catch (_: Exception) {
        false
    }

    @Suppress("unused")
    private fun abs2(v: Double): String = df2.format(abs(v))

    companion object {
        private const val MENU_EDIT = 1
        private const val MENU_DELETE = 2
    }
}
