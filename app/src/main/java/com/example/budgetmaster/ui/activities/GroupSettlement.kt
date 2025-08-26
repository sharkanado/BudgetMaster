package com.example.budgetmaster.ui.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.roundToInt

class GroupSettlement : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var youReceive: TextView
    private lateinit var youPay: TextView
    private lateinit var backButton: ImageButton
    private lateinit var allGroupExpenses: TextView

    private lateinit var adapter: SettlementAdapter
    private var budgetId: String = ""

    // Currency for this budget (code like "CZK", "PLN")
    private var budgetCurrencyCode: String = "EUR"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_group_settlement)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sys.left, sys.top, sys.right, sys.bottom)
            insets
        }

        budgetId = intent.getStringExtra("budgetId") ?: ""
        if (budgetId.isBlank()) {
            finish(); return
        }

        recycler = findViewById(R.id.settlementRecycler)
        emptyState = findViewById(R.id.emptyState)
        youReceive = findViewById(R.id.youReceive)
        youPay = findViewById(R.id.youPay)
        allGroupExpenses = findViewById(R.id.allGroupExpenses)
        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        adapter = SettlementAdapter(onItemClick = { row ->
            if (row.amount < 0) {
                val intent =
                    android.content.Intent(this, SettleUpPayments::class.java).apply {
                        putExtra("budgetId", budgetId)
                        putExtra("receiverId", row.uid)
                        putExtra("receiverName", row.name)
                        putExtra("amount", abs(row.amount))
                        putExtra("currency", budgetCurrencyCode)
                    }
                startActivity(intent)
            }
        })

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        // Initial load
        loadBudgetCurrency {
            loadSettlement()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh on return
        loadBudgetCurrency {
            loadSettlement()
        }
    }

    /** Fetch the budget's currency code (e.g., "CZK"). */
    private fun loadBudgetCurrency(onReady: () -> Unit) {
        db.collection("budgets").document(budgetId)
            .get()
            .addOnSuccessListener { doc ->
                val code = (doc.getString("currency")
                    ?: doc.getString("baseCurrency")
                    ?: "EUR").uppercase(Locale.ENGLISH)
                budgetCurrencyCode = code
            }
            .addOnFailureListener {
                budgetCurrencyCode = "EUR"
            }
            .addOnCompleteListener { onReady() }
    }

    private fun loadSettlement() {
        val currentUid = auth.currentUser?.uid ?: run {
            showNoData()
            return
        }

        db.collection("budgets")
            .document(budgetId)
            .collection("expenses")
            .get()
            .addOnSuccessListener { qs ->
                if (qs.isEmpty) {
                    showNoData()
                    return@addOnSuccessListener
                }

                val pair = mutableMapOf<String, Double>() // otherUid -> (+ you receive / - you pay)
                var totalExpenses = 0.0

                for (doc in qs) {
                    if ((doc.getString("type") ?: "expense") != "expense") continue
                    val payer = doc.getString("createdBy") ?: continue
                    val amount = doc.getDouble("amount") ?: 0.0
                    totalExpenses += amount

                    @Suppress("UNCHECKED_CAST")
                    val rawShares = doc.get("paidShares") as? Map<String, Any> ?: continue
                    val shares = rawShares.mapNotNull { (k, v) ->
                        (v as? Number)?.toDouble()?.let { k to it }
                    }.toMap()

                    if (currentUid == payer) {
                        // You paid. Others' shares are your receivables (+)
                        shares.forEach { (uid, share) ->
                            if (uid != payer) pair[uid] = (pair[uid] ?: 0.0) + share
                        }
                    } else {
                        // Someone else paid. Your share is your payable (-) to them
                        val yourShare = shares[currentUid]
                        if (yourShare != null && yourShare > 0.0) {
                            pair[payer] = (pair[payer] ?: 0.0) - yourShare
                        }
                    }
                }

                // Show the total group spend with currency code
                allGroupExpenses.text = "${format2(totalExpenses)} $budgetCurrencyCode"

                if (pair.isEmpty()) {
                    adapter.submit(emptyList())
                    emptyState.visibility = View.VISIBLE
                    emptyState.text = getString(R.string.no_balances_to_settle)
                    youReceive.text = "0.00 $budgetCurrencyCode"
                    youPay.text = "0.00 $budgetCurrencyCode"
                    return@addOnSuccessListener
                }

                val uids = pair.keys.toList()
                fetchUsersByIds(uids) { people ->
                    val items = uids.map { uid ->
                        val (name, email) = people[uid] ?: ("Unknown" to "")
                        SettlementRow(
                            uid = uid,
                            name = name,
                            email = email,
                            amount = round2(pair[uid] ?: 0.0)
                        )
                    }.sortedBy { it.name.lowercase(Locale.getDefault()) }

                    adapter.submit(items)
                    emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE

                    val totalReceive = items.filter { it.amount > 0 }.sumOf { it.amount }
                    val totalPay = items.filter { it.amount < 0 }.sumOf { abs(it.amount) }

                    youReceive.text = "${format2(totalReceive)} $budgetCurrencyCode"
                    youPay.text = "${format2(totalPay)} $budgetCurrencyCode"
                }
            }
            .addOnFailureListener {
                showNoData()
            }
    }

    private fun showNoData() {
        adapter.submit(emptyList())
        emptyState.visibility = View.VISIBLE
        emptyState.text = getString(R.string.no_group_expenses_yet)
        youReceive.text = "0.00 $budgetCurrencyCode"
        youPay.text = "0.00 $budgetCurrencyCode"
        allGroupExpenses.text = "0.00 $budgetCurrencyCode"
    }

    private fun fetchUsersByIds(
        uids: List<String>,
        onResult: (Map<String, Pair<String, String>>) -> Unit
    ) {
        if (uids.isEmpty()) {
            onResult(emptyMap()); return
        }

        val chunks = uids.chunked(10)
        val results = mutableMapOf<String, Pair<String, String>>()
        var completed = 0

        chunks.forEach { chunk ->
            db.collection("users")
                .whereIn(FieldPath.documentId(), chunk)
                .get()
                .addOnSuccessListener { snap ->
                    for (d in snap.documents) {
                        val name = d.getString("name") ?: "Unknown"
                        val email = d.getString("email") ?: ""
                        results[d.id] = name to email
                    }
                }
                .addOnCompleteListener {
                    completed++
                    if (completed == chunks.size) onResult(results)
                }
        }
    }

    private fun round2(v: Double) = round(v * 100.0) / 100.0
    private fun format2(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun dp(px: Int) = (px * resources.displayMetrics.density).roundToInt()

    data class SettlementRow(
        val uid: String,
        val name: String,
        val email: String,
        val amount: Double
    )

    private inner class SettlementAdapter(
        private val onItemClick: ((SettlementRow) -> Unit)? = null
    ) : RecyclerView.Adapter<SettlementVH>() {

        private val data = mutableListOf<SettlementRow>()

        fun submit(items: List<SettlementRow>) {
            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = data.size
                override fun getNewListSize() = items.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return data[oldItemPosition].uid == items[newItemPosition].uid
                }

                override fun areContentsTheSame(
                    oldItemPosition: Int,
                    newItemPosition: Int
                ): Boolean {
                    return data[oldItemPosition] == items[newItemPosition]
                }
            })
            data.clear()
            data.addAll(items)
            diff.dispatchUpdatesTo(this)
        }

        override fun onCreateViewHolder(
            parent: android.view.ViewGroup,
            viewType: Int
        ): SettlementVH {
            val view =
                layoutInflater.inflate(R.layout.item_budget_member_balance_tile, parent, false)
            return SettlementVH(view, onItemClick)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: SettlementVH, position: Int) {
            holder.bind(data[position])
        }
    }

    private inner class SettlementVH(
        itemView: View,
        private val onItemClick: ((SettlementRow) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {

        private val nameText: TextView = itemView.findViewById(R.id.memberName)
        private val emailText: TextView = itemView.findViewById(R.id.memberEmail)
        private val amountText: TextView = itemView.findViewById(R.id.memberBalance)

        fun bind(row: SettlementRow) {
            nameText.text = row.name
            emailText.text = row.email

            val formatted = "${format2(abs(row.amount))} $budgetCurrencyCode"
            val owes = row.amount < 0

            when {
                row.amount > 0 -> {
                    amountText.text = formatted
                    amountText.setTextColor(getColorCompat(R.color.green_success))
                }

                owes -> {
                    amountText.text = formatted
                    amountText.setTextColor(getColorCompat(R.color.red_error))
                }

                else -> {
                    amountText.text = "0.00 $budgetCurrencyCode"
                    amountText.setTextColor(getColorCompat(android.R.color.darker_gray))
                }
            }

            val arrowRes = if (owes) R.drawable.ic_send_money else 0
            amountText.setCompoundDrawablesWithIntrinsicBounds(0, 0, arrowRes, 0)
            amountText.compoundDrawablePadding = dp(6)

            if (owes && onItemClick != null) {
                itemView.isClickable = true
                itemView.isFocusable = true
                itemView.setBackgroundResource(R.drawable.expense_ripple)
                itemView.setOnClickListener { onItemClick.invoke(row) }
            } else {
                itemView.isClickable = false
                itemView.isFocusable = false
                itemView.setOnClickListener(null)
                itemView.setBackgroundResource(R.drawable.expense_bg)
            }
        }
    }

    private fun getColorCompat(resId: Int) =
        androidx.core.content.ContextCompat.getColor(this, resId)
}
