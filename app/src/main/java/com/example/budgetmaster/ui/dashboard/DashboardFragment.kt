package com.example.budgetmaster.ui.dashboard

import ExpenseListItem
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.R
import com.example.budgetmaster.databinding.FragmentDashboardBinding
import com.example.budgetmaster.ui.activities.AddExpense
import com.example.budgetmaster.ui.activities.ExpenseDetailsWallet
import com.example.budgetmaster.ui.activities.MyWallet
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

        expensesAdapter = ExpensesAdapter(
            emptyList(),
            onItemClick = { item ->
                if (item is ExpenseListItem.Item) {
                    openExpenseDetails(item)
                }
            }
        )

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

        binding.loadingProgressBar.visibility = View.VISIBLE
        binding.noDataText.visibility = View.GONE
        binding.latestExpensesRecycler.visibility = View.GONE

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
                        val expenseId = doc.getString("expenseId") ?: ""

                        ExpenseDetails(parsedDate, name, category, signedAmount, type, expenseId)
                    }
                    .groupBy { it.date }
                    .toSortedMap(compareByDescending { it })

                val listItems = mutableListOf<ExpenseListItem>()
                val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

                for ((date, entries) in grouped) {
                    val total = entries.sumOf { it.amount }
                    val label = "%.2f".format(total)
                    listItems.add(
                        ExpenseListItem.Header(
                            date.format(formatted),
                            label,
                            total >= 0
                        )
                    )

                    entries.forEach { (date, name, category, amount, type, expenseId) ->
                        val displayAmount = "%.2f".format(amount)
                        listItems.add(
                            ExpenseListItem.Item(
                                R.drawable.ic_home_white_24dp,
                                name,
                                category,
                                displayAmount,
                                date.toString(),
                                type,
                                expenseId
                            )
                        )
                    }
                }

                binding.loadingProgressBar.visibility = View.GONE

                if (listItems.isEmpty()) {
                    binding.noDataText.visibility = View.VISIBLE
                    binding.latestExpensesRecycler.visibility = View.GONE
                } else {
                    binding.noDataText.visibility = View.GONE
                    binding.latestExpensesRecycler.visibility = View.VISIBLE
                    expensesAdapter.updateItems(listItems)
                }
            }
            .addOnFailureListener {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataText.visibility = View.VISIBLE
                binding.latestExpensesRecycler.visibility = View.GONE
            }
    }

    private fun openExpenseDetails(item: ExpenseListItem.Item) {
        val intent = Intent(requireContext(), ExpenseDetailsWallet::class.java)
        intent.putExtra("expense_item", item)

        val date = LocalDate.parse(item.date)
        val year = date.year
        val month = date.month.name.lowercase().replaceFirstChar { it.uppercase() }

        intent.putExtra("selectedYear", year)
        intent.putExtra("selectedMonth", month)

        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class ExpenseDetails(
        val date: LocalDate,
        val name: String,
        val category: String,
        val amount: Double,
        val type: String,
        val expenseId: String
    )
}
