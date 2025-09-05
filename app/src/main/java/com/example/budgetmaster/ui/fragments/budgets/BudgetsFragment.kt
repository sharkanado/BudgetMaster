package com.example.budgetmaster.ui.fragments.budgets

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
import com.example.budgetmaster.utils.BudgetItem
import com.example.budgetmaster.utils.BudgetsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class BudgetsFragment : Fragment() {

    private var _binding: FragmentBudgetsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: BudgetsAdapter
    private var loadJob: Job? = null

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
            intent.putExtra("budgetId", clickedBudget.id)
            intent.putExtra("budgetName", clickedBudget.name)
            startActivity(intent)
        }

        binding.budgetListRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@BudgetsFragment.adapter
        }

        loadBudgets()
        return root
    }

    override fun onResume() {
        super.onResume()
        loadBudgets()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadJob?.cancel()
        _binding = null
    }

    private fun loadBudgets() {
        loadJob?.cancel()

        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: run {
            if (isAdded && _binding != null) {
                adapter.submitList(emptyList())
                binding.loadingProgressBar.visibility = View.GONE
            }
            return
        }

        if (!isAdded || _binding == null) return

        binding.loadingProgressBar.visibility = View.VISIBLE

        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val userDoc = db.collection("users").document(currentUser.uid).get().await()
                val accessed = (userDoc.get("budgetsAccessed") as? List<String>).orEmpty()
                if (!isAdded || _binding == null) return@launch

                if (accessed.isEmpty()) {
                    adapter.submitList(emptyList())
                    binding.loadingProgressBar.visibility = View.GONE
                    return@launch
                }

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
                            preferredCurrency = doc.getString("currency") ?: "PLN",
                            members = (doc.get("members") as? List<String>).orEmpty(),
                            ownerId = doc.getString("ownerId") ?: "",
                            balance = 0.0
                        )
                    }
                }

                val list = byId.values.toList()
                if (!isAdded || _binding == null) return@launch
                adapter.submitList(list)
                binding.loadingProgressBar.visibility = View.GONE

                list.forEach { budget ->
                    launch(Dispatchers.IO) {
                        val total = sumBudgetExpenses(db, budget.id)
                        withContext(Dispatchers.Main) {
                            if (isAdded && _binding != null) {
                                adapter.updateBudgetTotal(budget.id, total)
                            }
                        }
                    }
                }

                list.forEach { budget ->
                    launch(Dispatchers.IO) {
                        val (recv, debt) = fetchUserTotals(db, budget.id, currentUser.uid)
                        withContext(Dispatchers.Main) {
                            if (isAdded && _binding != null) {
                                adapter.updateBudgetStatus(budget.id, recv, debt)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                if (isAdded && _binding != null) {
                    adapter.submitList(emptyList())
                    binding.loadingProgressBar.visibility = View.GONE
                }
            }
        }
    }

    // only sumBudgetExpenses changed to skip settlement
    private suspend fun sumBudgetExpenses(db: FirebaseFirestore, budgetId: String): Double {
        val col = db.collection("budgets").document(budgetId).collection("expenses")
        return try {
            // aggregation cannot filter -> fallback to manual
            val snap = col.get().await()
            var total = 0.0
            for (doc in snap.documents) {
                if ((doc.getString("type") ?: "expense") == "settlement") continue // 👈 skip
                val raw = doc.get("amount")
                total += when (raw) {
                    is Number -> raw.toDouble()
                    is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
                    else -> 0.0
                }
            }
            total
        } catch (_: Exception) {
            0.0
        }
    }

    private suspend fun fetchUserTotals(
        db: FirebaseFirestore,
        budgetId: String,
        uid: String
    ): Pair<Double, Double> {
        return try {
            val doc = db.collection("budgets").document(budgetId)
                .collection("totals").document(uid)
                .get().await()
            val receivable = (doc.getDouble("receivable") ?: 0.0).coerceAtLeast(0.0)
            val debt = (doc.getDouble("debt") ?: 0.0).coerceAtLeast(0.0)
            receivable to debt
        } catch (_: Exception) {
            0.0 to 0.0
        }
    }
}
