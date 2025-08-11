package com.example.budgetmaster.ui.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetmaster.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale
import kotlin.math.abs
import kotlin.math.round

class GroupSettlement : AppCompatActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private val auth by lazy { FirebaseAuth.getInstance() }

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: TextView
    private lateinit var youReceive: TextView
    private lateinit var youPay: TextView
    private lateinit var backButton: ImageButton

    private lateinit var adapter: SettlementAdapter
    private var budgetId: String = ""

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
        backButton = findViewById(R.id.backButton)
        backButton.setOnClickListener { finish() }

        adapter = SettlementAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadSettlement()
    }

    private fun loadSettlement() {
        val currentUid = auth.currentUser?.uid
        if (currentUid.isNullOrBlank()) {
            showEmpty()
            return
        }

        db.collection("budgets")
            .document(budgetId)
            .collection("expenses")
            .get()
            .addOnSuccessListener { qs ->
                // Pairwise map between YOU and each member:
                // > 0 -> they owe YOU ; < 0 -> YOU owe them
                val pair = mutableMapOf<String, Double>()

                for (doc in qs) {
                    if ((doc.getString("type") ?: "expense") != "expense") continue

                    val payer = doc.getString("createdBy") ?: continue

                    @Suppress("UNCHECKED_CAST")
                    val rawShares = doc.get("paidShares") as? Map<String, Any> ?: continue
                    val shares = rawShares.mapNotNull { (k, v) ->
                        (v as? Number)?.toDouble()?.let { k to it }
                    }.toMap()

                    if (currentUid == payer) {
                        // Others owe you their shares
                        shares.forEach { (uid, share) ->
                            if (uid != payer) pair[uid] = (pair[uid] ?: 0.0) + share
                        }
                    } else {
                        // You owe payer your share (if participated)
                        val yourShare = shares[currentUid]
                        if (yourShare != null && yourShare > 0.0) {
                            pair[payer] = (pair[payer] ?: 0.0) - yourShare
                        }
                    }
                }

                if (pair.isEmpty()) {
                    showEmpty()
                    return@addOnSuccessListener
                }

                // Fetch names/emails for those UIDs (whereIn limit 10 -> chunk it)
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

                    youReceive.text = getString(R.string.settlement_receive, format2(totalReceive))
                    youPay.text = getString(R.string.settlement_owe, format2(totalPay))
                }
            }
            .addOnFailureListener { showEmpty() }
    }

    private fun showEmpty() {
        adapter.submit(emptyList())
        emptyState.visibility = View.VISIBLE
        youReceive.text = getString(R.string.settlement_receive, "0.00")
        youPay.text = getString(R.string.settlement_owe, "0.00")
    }

    // whereIn supports max 10 values -> chunked fetching
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

    // --- utils ---
    private fun round2(v: Double) = round(v * 100.0) / 100.0
    private fun format2(v: Double) = String.format(Locale.US, "%.2f", v)

    // --- data & adapter ---
    data class SettlementRow(
        val uid: String,
        val name: String,
        val email: String,
        val amount: Double // >0 they owe you; <0 you owe them
    )

    private inner class SettlementAdapter : RecyclerView.Adapter<SettlementVH>() {
        private val data = mutableListOf<SettlementRow>()

        fun submit(items: List<SettlementRow>) {
            data.clear()
            data.addAll(items)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(
            parent: android.view.ViewGroup,
            viewType: Int
        ): SettlementVH {
            val view = layoutInflater.inflate(R.layout.item_settlement, parent, false)
            return SettlementVH(view)
        }

        override fun getItemCount(): Int = data.size

        override fun onBindViewHolder(holder: SettlementVH, position: Int) {
            holder.bind(data[position])
        }
    }

    private inner class SettlementVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.memberName)
        private val emailText: TextView = itemView.findViewById(R.id.memberEmail)
        private val amountText: TextView = itemView.findViewById(R.id.settlementAmount)
        private val labelText: TextView = itemView.findViewById(R.id.settlementLabel)

        fun bind(row: SettlementRow) {
            nameText.text = row.name
            emailText.text = row.email

            val formatted = String.format(Locale.US, "%.2f", abs(row.amount))
            when {
                row.amount > 0 -> {
                    // They owe you
                    amountText.text = formatted
                    amountText.setTextColor(getColorCompat(R.color.green_success))
                    labelText.text = getString(R.string.you_get_label)
                    labelText.setTextColor(getColorCompat(R.color.green_success))
                }

                row.amount < 0 -> {
                    // You owe them
                    amountText.text = formatted
                    amountText.setTextColor(getColorCompat(R.color.red_error))
                    labelText.text = getString(R.string.you_owe_label)
                    labelText.setTextColor(getColorCompat(R.color.red_error))
                }

                else -> {
                    amountText.text = "0.00"
                    amountText.setTextColor(getColorCompat(android.R.color.darker_gray))
                    labelText.text = getString(R.string.settlement_clear_label)
                    labelText.setTextColor(getColorCompat(android.R.color.darker_gray))
                }
            }
        }
    }

    private fun getColorCompat(resId: Int) =
        androidx.core.content.ContextCompat.getColor(this, resId)
}
