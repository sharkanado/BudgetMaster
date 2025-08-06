package com.example.budgetmaster.ui.budgets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.databinding.FragmentBudgetsBinding
import com.example.budgetmaster.ui.activities.AddNewBudget
import com.example.budgetmaster.ui.activities.BudgetDetails
import com.example.budgetmaster.ui.activities.MyWallet
import com.example.budgetmaster.ui.components.BudgetsAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

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

        return root
    }

    override fun onResume() {
        super.onResume()
        // Refresh budgets whenever the fragment comes back into view
        loadBudgets()
    }

    private fun loadBudgets() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { userDoc ->
                val accessedBudgets = userDoc.get("budgetsAccessed") as? List<String> ?: emptyList()

                if (accessedBudgets.isEmpty()) {
                    budgets.clear()
                    adapter.notifyDataSetChanged()
                    return@addOnSuccessListener
                }

                budgets.clear()
                var processed = 0

                for (budgetId in accessedBudgets) {
                    db.collection("budgets").document(budgetId).get()
                        .addOnSuccessListener { budgetDoc ->
                            if (budgetDoc.exists()) {
                                val budget = BudgetItem(
                                    id = budgetId,
                                    name = budgetDoc.getString("name") ?: "",
                                    preferredCurrency = budgetDoc.getString("preferredCurrency")
                                        ?: "",
                                    members = budgetDoc.get("members") as? List<String>
                                        ?: emptyList(),
                                    ownerId = budgetDoc.getString("ownerId") ?: "",
                                    balance = budgetDoc.getDouble("balance") ?: 0.0
                                )
                                budgets.add(budget)
                            }
                        }
                        .addOnCompleteListener {
                            processed++
                            if (processed == accessedBudgets.size) {
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
