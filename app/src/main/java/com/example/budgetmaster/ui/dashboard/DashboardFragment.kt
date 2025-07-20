package com.example.budgetmaster.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.R
import com.example.budgetmaster.databinding.FragmentDashboardBinding
import com.example.budgetmaster.ui.activities.AddExpense
import com.example.budgetmaster.ui.activities.MyWallet
import com.example.budgetmaster.ui.components.ExpenseListItem
import com.example.budgetmaster.ui.components.ExpensesAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var expensesAdapter: ExpensesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root = binding.root

        binding.addExpenseButton.setOnClickListener {
            startActivity(Intent(requireContext(), AddExpense::class.java))
        }

        binding.myExpensesBlock.setOnClickListener {
            startActivity(Intent(requireContext(), MyWallet::class.java))
        }

        binding.seeAllButton.setOnClickListener {
            startActivity(Intent(requireContext(), MyWallet::class.java))
        }

        expensesAdapter = ExpensesAdapter(emptyList())
        binding.latestExpensesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expensesAdapter
        }

        return root
    }

    override fun onResume() {
        super.onResume()
        loadLatestExpenses()
    }

    private fun loadLatestExpenses() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("users")
            .document(uid)
            .collection("latest")
            .get()
            .addOnSuccessListener { result ->
                val grouped = result.documents
                    .mapNotNull { doc ->
                        val dateStr = doc.getString("date") ?: return@mapNotNull null
                        val parsedDate = LocalDate.parse(dateStr)
                        val name = doc.getString("description") ?: ""
                        val category = doc.getString("category") ?: ""
                        val amount = doc.getDouble("amount") ?: return@mapNotNull null
                        val type = doc.getString("type") ?: "expense"
                        val signedAmount = if (type == "expense") -amount else amount
                        listOf(parsedDate, name, category, signedAmount, type)
                    }
                    .map { (date, name, category, amount, type) ->
                        Quintuple(
                            date as LocalDate,
                            name as String,
                            category as String,
                            amount as Double,
                            type as String
                        )
                    }
                    .groupBy { it.first }
                    .toSortedMap(compareByDescending { it })

                val listItems = mutableListOf<ExpenseListItem>()
                val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

                for ((date, entries) in grouped) {
                    val total = entries.sumOf { it.fourth }
                    val label = "%.2f".format(total)
                    listItems.add(
                        ExpenseListItem.Header(
                            date.format(formatted),
                            label,
                            total >= 0
                        )
                    )

                    entries.forEach { (_, name, category, amount, type) ->
                        val displayAmount = "%.2f".format(amount)
                        listItems.add(
                            ExpenseListItem.Item(
                                R.drawable.ic_home_white_24dp,
                                name,
                                category,
                                displayAmount,
                                type
                            )
                        )
                    }
                }

                expensesAdapter.updateItems(listItems)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )
}
