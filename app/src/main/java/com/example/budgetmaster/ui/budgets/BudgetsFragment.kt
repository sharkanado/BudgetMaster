package com.example.budgetmaster.ui.budgets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.databinding.FragmentBudgetsBinding
import com.example.budgetmaster.ui.activities.AddNewBudget
import com.example.budgetmaster.ui.activities.BudgetDetails
import com.example.budgetmaster.ui.activities.MyWallet
import com.example.budgetmaster.ui.components.BudgetsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BudgetsFragment : Fragment() {

    private var _binding: FragmentBudgetsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BudgetsAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBudgetsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.addWalletButton.setOnClickListener {
            startActivity(Intent(requireContext(), AddNewBudget::class.java))
        }

        binding.myWalletCard.setOnClickListener {
            startActivity(Intent(requireContext(), MyWallet::class.java))
        }

        adapter = BudgetsAdapter(emptyList()) { clickedBudget ->
            val intent = Intent(requireContext(), BudgetDetails::class.java)
            intent.putExtra("budget", clickedBudget)
            startActivity(intent)
        }

        binding.budgetListRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BudgetsFragment.adapter
        }

        // Load once; show spinner during fetch
        loadBudgets()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadBudgets() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        // show loading
        binding.loadingProgressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val accessed = (userDoc.get("budgetsAccessed") as? List<String>).orEmpty()
                if (accessed.isEmpty()) {
                    adapter.submitList(emptyList())
                    binding.loadingProgressBar.visibility = View.GONE
                    return@launch
                }

                // Fetch budgets in chunks of 10 (whereIn limit)
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
                            balance = 0.0 // (totals loaded separately)
                        )
                    }
                }

                val list = byId.values.toList()
                adapter.submitList(list)
                // hide loading once list is shown
                binding.loadingProgressBar.visibility = View.GONE

                // Load totals asynchronously per budget, update with payload (no full rebind)
                list.forEach { budget ->
                    launch(Dispatchers.IO) {
                        val total = sumBudgetExpenses(db, budget.id)
                        withContext(Dispatchers.Main) {
                            adapter.updateBudgetTotal(budget.id, total)
                        }
                    }
                }
            } catch (_: Exception) {
                adapter.submitList(emptyList())
                binding.loadingProgressBar.visibility = View.GONE
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
        } catch (_: Exception) {
            // Fallback: client sum (handles legacy string types, if any)
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
}
