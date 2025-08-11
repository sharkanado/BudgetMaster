package com.example.budgetmaster.ui.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

class BudgetExpenseDetails : AppCompatActivity() {

    // View-mode fields
    private lateinit var amountView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var dateView: TextView
    private lateinit var paidByView: TextView

    // Edit-mode fields
    private lateinit var amountEdit: EditText
    private lateinit var descriptionEdit: EditText
    private lateinit var dateEdit: EditText

    // One recycler view (we swap adapters)
    private lateinit var participantsRecycler: RecyclerView

    // Adapters & backing lists
    private val allMembers = mutableListOf<BudgetMemberItem>()       // all members in the budget
    private val selectedMembers = mutableSetOf<String>()             // currently selected (paidFor)
    private val participantsReadOnly =
        mutableListOf<BudgetMemberItem>() // for view mode (balance = share)

    private lateinit var readOnlyAdapter: BudgetMembersAdapter
    private lateinit var splitAdapter: BudgetSplitMembersAdapter

    private lateinit var editBtn: AppCompatImageButton
    private var isEditMode = false

    private lateinit var expenseItem: BudgetExpenseItem
    private var userNames: MutableMap<String, String> = mutableMapOf()
    private var budgetId: String = ""

    private val db by lazy { FirebaseFirestore.getInstance() }

    // Current per-uid shares used in EDIT mode (kept in memory only)
    private val sharesByUid = linkedMapOf<String, Double>()

    private val df2 = DecimalFormat("0.00")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_expense_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        // Get parcelable expense
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

        // Budget id
        budgetId = intent.getStringExtra("budgetId") ?: ""
        if (budgetId.isEmpty()) {
            Toast.makeText(this, "No budgetId provided", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        // Optional map of user names
        val passedMap: HashMap<String, String>? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(
                    "userNames",
                    HashMap::class.java
                ) as? HashMap<String, String>
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("userNames") as? HashMap<String, String>
            }
        passedMap?.let { userNames.putAll(it) }

        // Seed selection from the expense
        selectedMembers.clear()
        selectedMembers.addAll(expenseItem.paidFor)

        bindViews()

        // Recycler & adapters
        participantsRecycler.layoutManager = LinearLayoutManager(this)

        readOnlyAdapter = BudgetMembersAdapter(participantsReadOnly)

        splitAdapter = BudgetSplitMembersAdapter(
            members = allMembers,
            selected = selectedMembers,
            sharesByUid = sharesByUid,
            // Total provider pulls live total from amountEdit
            totalProvider = {
                amountEdit.text.toString()
                    .replace(',', '.')
                    .toDoubleOrNull() ?: expenseItem.amount
            },
            onCheckedChanged = { uid, checked ->
                // Update selected set
                if (checked) selectedMembers.add(uid) else selectedMembers.remove(uid)
                // Rebuild shares (equal split with rounding)
                recomputeSharesEqual()
                // Ask adapter to refresh others, defering notify to avoid layout crash
                splitAdapter.refreshVisibleSharesExcept(participantsRecycler, null)
            },
            onShareEditedValid = { editedUid, newValue ->
                // Balance the rest to keep the same total
                val total = amountEdit.text.toString().replace(',', '.').toDoubleOrNull()
                    ?: expenseItem.amount
                applyBalancedEdit(editedUid, newValue, total)
                splitAdapter.refreshVisibleSharesExcept(participantsRecycler, editedUid)
            },
            onStartEditing = { /* no-op */ },
            onStopEditing = { /* no-op */ }
        )

        // Start in VIEW mode
        setUiForViewMode()
        participantsRecycler.adapter = readOnlyAdapter

        fillFieldsOnce()
        ensurePayerNameLoaded(expenseItem.createdBy)

        // Prepare initial edit-mode shares (equal split)
        recomputeSharesEqual()

        // Load full member list and rebuild the UI lists
        loadBudgetMembers()

        editBtn.setOnClickListener {
            if (!isEditMode) toggleEdit(true) else saveChanges()
        }
    }

    private fun bindViews() {
        amountView = findViewById(R.id.expenseAmount)
        descriptionView = findViewById(R.id.expenseDescription)
        dateView = findViewById(R.id.expenseDate)
        paidByView = findViewById(R.id.whoPaidName)

        amountEdit = findViewById(R.id.expenseAmountEdit)
        descriptionEdit = findViewById(R.id.expenseDescriptionEdit)
        dateEdit = findViewById(R.id.expenseDateEdit)

        participantsRecycler = findViewById(R.id.expenseParticipantsRecyclerView)
        editBtn = findViewById(R.id.editButton)

        // Initial UI (view mode)
        setUiForViewMode()
        editBtn.setImageResource(R.drawable.ic_edit)
    }

    private fun setUiForViewMode() {
        amountView.visibility = View.VISIBLE
        descriptionView.visibility = View.VISIBLE
        dateView.visibility = View.VISIBLE
        paidByView.visibility = View.VISIBLE

        amountEdit.visibility = View.GONE
        descriptionEdit.visibility = View.GONE
        dateEdit.visibility = View.GONE

        isEditMode = false
    }

    private fun setUiForEditMode() {
        amountView.visibility = View.GONE
        descriptionView.visibility = View.GONE
        dateView.visibility = View.GONE
        paidByView.visibility = View.GONE

        amountEdit.visibility = View.VISIBLE
        descriptionEdit.visibility = View.VISIBLE
        dateEdit.visibility = View.VISIBLE

        isEditMode = true
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
                                allMembers.add(
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
                                // Ensure selection is within available members
                                selectedMembers.retainAll(memberIds.toSet())

                                // If nothing selected (edge case), default to all members
                                if (selectedMembers.isEmpty()) {
                                    selectedMembers.addAll(memberIds)
                                }

                                // Rebuild edit shares & view items
                                recomputeSharesEqual()
                                rebuildReadOnlyParticipants()

                                // Hook correct adapter
                                participantsRecycler.adapter =
                                    if (isEditMode) splitAdapter else readOnlyAdapter

                                // Notify
                                readOnlyAdapter.notifyDataSetChanged()
                                splitAdapter.notifyDataSetChanged()
                            }
                        }
                }
            }
    }

    /** Rebuild the read-only list from current selection, dividing equally from total expense. */
    private fun rebuildReadOnlyParticipants() {
        participantsReadOnly.clear()

        val sel = selectedMembers.toList()
        val count = sel.size
        if (count == 0) return

        val perHead = round2(expenseItem.amount / count)
        val byUid = allMembers.associateBy { it.uid }

        var running = 0.0
        sel.forEachIndexed { idx, uid ->
            byUid[uid]?.let { src ->
                val value = if (idx == count - 1) {
                    // last one gets the remainder correction to match total exactly
                    round2(expenseItem.amount - running)
                } else {
                    running += perHead
                    perHead
                }
                participantsReadOnly.add(src.copy(balance = value))
            }
        }
    }

    /** Equal split into sharesByUid based on the *current* selected set and current total. */
    private fun recomputeSharesEqual() {
        sharesByUid.clear()
        val total = amountEdit.text.toString()
            .replace(',', '.')
            .toDoubleOrNull() ?: expenseItem.amount

        val count = selectedMembers.size
        if (count == 0) return

        val per = round2(total / count)
        var running = 0.0
        selectedMembers.toList().forEachIndexed { index, uid ->
            val v = if (index == count - 1) {
                round2(total - running)
            } else {
                running += per
                per
            }
            sharesByUid[uid] = v
        }
    }

    /**
     * When one member’s share is edited, keep the total constant and distribute
     * the remainder equally among the other selected members.
     */
    private fun applyBalancedEdit(editedUid: String, newValue: Double, total: Double) {
        val sel = selectedMembers.toList()
        if (sel.isEmpty()) return

        if (!sel.contains(editedUid)) return

        val others = sel.filter { it != editedUid }
        val remaining = round2(total - newValue)

        if (others.isEmpty()) {
            // Only one selected => that one must equal total (do not alter cursor here; adapter will clamp)
            sharesByUid[editedUid] = round2(total)
            return
        }

        if (remaining <= 0.0) {
            // Adapter should have blocked this; keep current sharesByUid unchanged.
            return
        }

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

    private fun fillFieldsOnce() {
        val e = expenseItem
        amountView.text = df2.format(e.amount)
        descriptionView.text = e.description
        dateView.text = formatDate(e.date)
        paidByView.text = userNames[e.createdBy] ?: e.createdBy

        amountEdit.setText(df2.format(e.amount))
        descriptionEdit.setText(e.description)
        dateEdit.setText(e.date)

        // Also prepare initial view-mode list
        rebuildReadOnlyParticipants()
        readOnlyAdapter.notifyDataSetChanged()
    }

    private fun toggleEdit(editMode: Boolean) {
        if (editMode) {
            setUiForEditMode()
            editBtn.setImageResource(R.drawable.ic_save)
            participantsRecycler.adapter = splitAdapter
            // Make sure edit-mode shares reflect current total + selection
            recomputeSharesEqual()
            splitAdapter.notifyDataSetChanged()
        } else {
            // View mode: reflect saved item values
            rebuildReadOnlyParticipants()
            setUiForViewMode()
            editBtn.setImageResource(R.drawable.ic_edit)
            participantsRecycler.adapter = readOnlyAdapter
            readOnlyAdapter.notifyDataSetChanged()
        }
    }

    private fun saveChanges() {
        val newAmount = amountEdit.text.toString().replace(",", ".").toDoubleOrNull()
        if (newAmount == null || newAmount <= 0.0) {
            Toast.makeText(this, "Amount must be greater than 0", Toast.LENGTH_SHORT).show()
            return
        }

        val newDescription = descriptionEdit.text?.toString()?.trim().orEmpty()
        val newDate = dateEdit.text?.toString()?.trim().orEmpty()
        if (newDate.isBlank() || !isValidDate(newDate)) {
            Toast.makeText(this, "Enter a valid date (yyyy-MM-dd)", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedMembers.isEmpty()) {
            Toast.makeText(this, "Select at least one participant", Toast.LENGTH_SHORT).show()
            return
        }

        // Update local model
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
            "timestamp" to Timestamp.now()
        )

        db.collection("budgets")
            .document(budgetId)
            .collection("expenses")
            .document(expenseItem.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                // Recompute view list with the new total
                rebuildReadOnlyParticipants()
                readOnlyAdapter.notifyDataSetChanged()

                fillFieldsOnce()
                toggleEdit(false)
                Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun ensurePayerNameLoaded(uid: String) {
        if (userNames[uid] != null) {
            paidByView.text = userNames[uid]; return
        }
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = doc.getString("name") ?: "Unknown"
                userNames[uid] = name
                paidByView.text = name
            }
            .addOnFailureListener { /* keep fallback */ }
    }

    private fun formatDate(dateStr: String): String = try {
        val input = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val output = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        val date = input.parse(dateStr)
        output.format(date!!)
    } catch (_: Exception) {
        dateStr
    }

    private fun isValidDate(dateStr: String): Boolean = try {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).apply { isLenient = false }
        fmt.parse(dateStr); true
    } catch (_: Exception) {
        false
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0

    @Suppress("unused")
    private fun abs2(v: Double): String = df2.format(abs(v))
}
