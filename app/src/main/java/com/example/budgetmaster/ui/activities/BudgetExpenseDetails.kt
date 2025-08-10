package com.example.budgetmaster.ui.activities

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.example.budgetmaster.ui.budgets.BudgetExpenseItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Locale

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
    private lateinit var forWhomContainer: RecyclerView

    // Single toggle button (edit ↔ save)
    private lateinit var editBtn: AppCompatImageButton

    private var isEditMode = false
    private lateinit var expenseItem: BudgetExpenseItem
    private var userNames: MutableMap<String, String> = mutableMapOf()
    private var budgetId: String = ""

    private val db by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_budget_expense_details)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        bindViews()

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

        val passedMap: HashMap<String, String>? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(
                    "userNames",
                    HashMap::class.java
                ) as? HashMap<String, String>
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra("userNames") as? HashMap<String, String>
            }

        passedMap?.let { userNames.putAll(it) }

        fillFieldsOnce()
        ensurePayerNameLoaded(expenseItem.createdBy)

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
        forWhomContainer = findViewById(R.id.expenseParticipantsRecyclerView)

        editBtn = findViewById(R.id.editButton)

        toggleEdit(false, updateIcon = false)
    }

    private fun fillFieldsOnce() {
        val e = expenseItem
        amountView.text = String.format(Locale.ENGLISH, "%.2f", e.amount)
        descriptionView.text = e.description
        dateView.text = formatDate(e.date)
        paidByView.text = userNames[e.createdBy] ?: e.createdBy

        amountEdit.setText(String.format(Locale.ENGLISH, "%.2f", e.amount))
        descriptionEdit.setText(e.description)
        dateEdit.setText(e.date)
    }

    private fun toggleEdit(editMode: Boolean, updateIcon: Boolean = true) {
        isEditMode = editMode
        amountView.visibility = if (editMode) View.GONE else View.VISIBLE
        descriptionView.visibility = if (editMode) View.GONE else View.VISIBLE
        dateView.visibility = if (editMode) View.GONE else View.VISIBLE
        paidByView.visibility = if (editMode) View.GONE else View.VISIBLE

        amountEdit.visibility = if (editMode) View.VISIBLE else View.GONE
        descriptionEdit.visibility = if (editMode) View.VISIBLE else View.GONE
        dateEdit.visibility = if (editMode) View.VISIBLE else View.GONE

        if (updateIcon) {
            editBtn.setImageResource(if (editMode) R.drawable.ic_save else R.drawable.ic_edit)
        }
    }

    private fun saveChanges() {
        val newAmount = amountEdit.text.toString().replace(",", ".").toDoubleOrNull()
        if (newAmount == null) {
            Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return
        }
        val newDescription = descriptionEdit.text?.toString()?.trim().orEmpty()
        val newDate = dateEdit.text?.toString()?.trim().orEmpty()
        if (newDate.isBlank() || !isValidDate(newDate)) {
            Toast.makeText(this, "Enter a valid date (yyyy-MM-dd)", Toast.LENGTH_SHORT)
                .show(); return
        }

        // Update local model
        val oldDate = expenseItem.date
        expenseItem =
            expenseItem.copy(amount = newAmount, description = newDescription, date = newDate)

        // Persist budget expense
        val updates = mapOf(
            "amount" to newAmount,                     // number!
            "description" to newDescription,
            "date" to newDate,
            "timestamp" to Timestamp.now()
        )

        db.collection("budgets")
            .document(budgetId)
            .collection("expenses")
            .document(expenseItem.id)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                fillFieldsOnce()
                toggleEdit(false)
                Toast.makeText(this, "Expense updated", Toast.LENGTH_SHORT).show()

                // Cascade updates to personal expenses and latest
                cascadeUpdatePersonalAndLatest(
                    affectedUsers = buildAffectedUsers(),
                    budgetExpenseId = expenseItem.id,
                    oldDate = oldDate,
                    newDate = newDate,
                    newAmount = newAmount,
                    newDescription = newDescription
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            .addOnCompleteListener {
                editBtn.setImageResource(R.drawable.ic_edit)
            }
    }

    /** createdBy + all paidFor users (duplicates removed) */
    private fun buildAffectedUsers(): Set<String> {
        val set = LinkedHashSet<String>()
        set.add(expenseItem.createdBy)
        expenseItem.paidFor.forEach { set.add(it) }
        return set
    }

    /**
     * For each user in [affectedUsers], tries to find personal expense docs that were created from this budget expense:
     * - Looks in both the collection for [oldDate] and for [newDate],
     * - Queries for documents with field "expenseIdInBudget" == [budgetExpenseId],
     * - Updates those docs (amount, description, date, timestamp),
     * - Then finds in users/{uid}/latest where "expenseId" == <personalDoc.id> and updates them too.
     */
    private fun cascadeUpdatePersonalAndLatest(
        affectedUsers: Set<String>,
        budgetExpenseId: String,
        oldDate: String,
        newDate: String,
        newAmount: Double,
        newDescription: String
    ) {
        val (oldYear, oldMonthName) = parseYearAndEnglishMonth(oldDate)
        val (newYear, newMonthName) = parseYearAndEnglishMonth(newDate)

        val personalUpdates = mapOf(
            "amount" to newAmount,               // number
            "description" to newDescription,
            "date" to newDate,
            "timestamp" to Timestamp.now(),
            "type" to "expense"                  // keep type consistent for wallet
        )

        affectedUsers.forEach { uid ->
            // Search in both old and new month collections
            val targets = listOf(oldYear to oldMonthName, newYear to newMonthName).distinct()

            targets.forEach { (year, monthName) ->
                if (year.isBlank() || monthName.isBlank()) return@forEach

                db.collection("users").document(uid)
                    .collection("expenses").document(year)
                    .collection(monthName)
                    .whereEqualTo("expenseIdInBudget", budgetExpenseId)
                    .get()
                    .addOnSuccessListener { snap ->
                        if (snap.isEmpty) return@addOnSuccessListener

                        snap.documents.forEach { doc ->
                            // 1) Update personal expense doc
                            doc.reference.set(personalUpdates, SetOptions.merge())
                                .addOnSuccessListener {
                                    // 2) Update user's latest (by personal expense doc id)
                                    val personalExpenseId = doc.id
                                    val latestPatch = mapOf(
                                        "amount" to newAmount,
                                        "description" to newDescription,
                                        "date" to newDate,
                                        "type" to "expense",
                                        "timestamp" to Timestamp.now()
                                    )

                                    db.collection("users").document(uid)
                                        .collection("latest")
                                        .whereEqualTo("expenseId", personalExpenseId)
                                        .get()
                                        .addOnSuccessListener { latestSnap ->
                                            if (latestSnap.isEmpty) return@addOnSuccessListener
                                            val batch = db.batch()
                                            for (ld in latestSnap.documents) {
                                                batch.set(
                                                    ld.reference,
                                                    latestPatch,
                                                    SetOptions.merge()
                                                )
                                            }
                                            batch.commit()
                                        }
                                }
                        }
                    }
            }
        }
    }

    private fun getCheckedUsers(): MutableList<String> {
        val updatedList = mutableListOf<String>()
        for (i in 0 until forWhomContainer.childCount) {
            val cb = forWhomContainer.getChildAt(i) as? CheckBox ?: continue
            if (cb.isChecked) {
                val uid = userNames.entries.firstOrNull { it.value == cb.text }?.key
                if (uid != null) updatedList.add(uid)
            }
        }
        return updatedList
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

    private fun parseYearAndEnglishMonth(dateStr: String): Pair<String, String> = try {
        val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val date = inFmt.parse(dateStr)!!
        val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(date)
        val month = SimpleDateFormat("MMMM", Locale.ENGLISH).format(date)
        year to month
    } catch (_: Exception) {
        "" to ""
    }
}
