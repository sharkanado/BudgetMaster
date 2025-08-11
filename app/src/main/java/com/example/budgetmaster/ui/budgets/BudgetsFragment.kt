package com.example.budgetmaster.ui.budgets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.R
import com.example.budgetmaster.databinding.FragmentBudgetsBinding
import com.example.budgetmaster.ui.activities.AddNewBudget
import com.example.budgetmaster.ui.activities.BudgetDetails
import com.example.budgetmaster.ui.activities.MyWallet
import com.example.budgetmaster.ui.components.BudgetsAdapter
import com.example.budgetmaster.utils.ExpenseSumUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols

class BudgetsFragment : Fragment() {

    private var _binding: FragmentBudgetsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BudgetsAdapter
    private val budgets = mutableListOf<BudgetItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentBudgetsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Navigate to AddNewBudget activity
        binding.addWalletButton.setOnClickListener {
            val intent = Intent(requireContext(), AddNewBudget::class.java)
            startActivity(intent)
        }

        // Navigate to MyWallet activity
        binding.myWalletCard.setOnClickListener {
            val intent = Intent(requireContext(), MyWallet::class.java)
            startActivity(intent)
        }

        // Setup RecyclerView with click callback
        adapter = BudgetsAdapter(budgets) { clickedBudget ->
            val intent = Intent(requireContext(), BudgetDetails::class.java)
            intent.putExtra("budget", clickedBudget)
            startActivity(intent)
        }

        binding.budgetListRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BudgetsFragment.adapter
        }

        // Initial load
        loadBudgets()
        // Initial wallet balance
        refreshWalletBalance()

        return root
    }

    override fun onResume() {
        super.onResume()
        // Refresh budgets whenever the fragment comes back into view
        loadBudgets()
        // Also refresh wallet balance
        refreshWalletBalance()
    }

    private fun loadBudgets() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1) Read user's accessed budgets
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val accessed = (userDoc.get("budgetsAccessed") as? List<String>).orEmpty()
                if (accessed.isEmpty()) {
                    adapter.submitList(emptyList())
                    return@launch
                }

                // 2) Fetch budgets by chunks of 10 (Firestore whereIn limit)
                val byId = linkedMapOf<String, BudgetItem>()
                accessed.chunked(10).forEach { chunk ->
                    val snap = db.collection("budgets")
                        .whereIn(com.google.firebase.firestore.FieldPath.documentId(), chunk)
                        .get().await()

                    for (doc in snap.documents) {
                        val id = doc.id
                        byId[id] = BudgetItem(
                            id = id,
                            name = doc.getString("name") ?: "",
                            preferredCurrency = doc.getString("preferredCurrency") ?: "PLN",
                            members = (doc.get("members") as? List<String>).orEmpty(),
                            ownerId = doc.getString("ownerId") ?: "",
                            balance = 0.0 // we'll compute below
                        )
                    }
                }

                // 3) Submit the de-duplicated list once
                val list = byId.values.toList()
                adapter.submitList(list)

                // 4) Load totals per budget (sum of all expenses in budgets/{id}/expenses)
                list.forEach { budget ->
                    launch(Dispatchers.IO) {
                        val total = sumBudgetExpenses(db, budget.id)
                        withContext(Dispatchers.Main) {
                            adapter.updateBudgetTotal(budget.id, total)
                        }
                    }
                }
            } catch (e: Exception) {
                // in case of error, at least clear the UI list
                adapter.submitList(emptyList())
            }
        }
    }

    private suspend fun sumBudgetExpenses(db: FirebaseFirestore, budgetId: String): Double {
        val col = db.collection("budgets").document(budgetId).collection("expenses")
        // Try aggregate SUM first (fast/cheap)
        return try {
            val agg = col.aggregate(AggregateField.sum("amount"))
                .get(AggregateSource.SERVER).await()
            (agg.get(AggregateField.sum("amount")) as? Number)?.toDouble() ?: 0.0
        } catch (e: Exception) {
            // Fallback: client sum (handles any legacy string types)
            val snap = col.get().await()
            var total = 0.0
            for (doc in snap.documents) {
                val raw = doc.get("amount")
                total += when (raw) {
                    is Number -> raw.toDouble()
                    is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            }
            total
        }
    }

    /** Sums all years’ income & expenses for the current user and displays NET in walletBalanceText. */
    private fun refreshWalletBalance() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseFirestore.getInstance()

        // Safe: only set if view is still around
        _binding?.walletBalanceText?.text = getString(R.string.loading_ellipsis)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val perYearTotals = withContext(Dispatchers.IO) {
                    ExpenseSumUtils.sumAllYearsTotals(db, uid)
                }
                val totalIncome = perYearTotals.values.sumOf { it.income }
                val totalExpense = perYearTotals.values.sumOf { it.expense }
                val net = totalIncome - totalExpense

                val sign = if (net >= 0) "+" else "-"
                val b = _binding ?: return@launch  // view might be gone by now

                b.walletBalanceText.text = sign + formatPlMoney(kotlin.math.abs(net))
                val colorRes = if (net >= 0) R.color.green_success else R.color.red_error
                b.walletBalanceText.setTextColor(
                    ContextCompat.getColor(requireContext(), colorRes)
                )
            } catch (e: Exception) {
                _binding?.walletBalanceText?.text = "0,00"
            }
        }
    }


    /** Format with space as thousands separator and comma as decimal separator, always 2 decimals. */
    private fun formatPlMoney(value: Double): String {
        val symbols = DecimalFormatSymbols().apply {
            groupingSeparator = ' '
            decimalSeparator = ','
        }
        val df = DecimalFormat("#,##0.00", symbols).apply {
            isGroupingUsed = true
        }
        return df.format(value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
