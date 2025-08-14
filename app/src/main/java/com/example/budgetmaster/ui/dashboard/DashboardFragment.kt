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
import com.example.budgetmaster.ui.activities.MyWallet
import com.example.budgetmaster.ui.components.ExpensesAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var expensesAdapter: ExpensesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val dotSyms = DecimalFormatSymbols(Locale.ENGLISH).apply {
        decimalSeparator = '.'
        groupingSeparator = ','
    }
    private val df2 = DecimalFormat("0.00").apply {
        decimalFormatSymbols = dotSyms
        isGroupingUsed = false
    }

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

        binding.debtBlock.setOnClickListener {
            requireActivity()
                .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.navigationView)
                .selectedItemId = R.id.navigationBudgets
        }

        binding.receivableBlock.setOnClickListener {
            requireActivity()
                .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.navigationView)
                .selectedItemId = R.id.navigationBudgets
        }

        binding.seeAllButton.setOnClickListener {
            startActivity(Intent(requireContext(), MyWallet::class.java))
        }

        expensesAdapter = ExpensesAdapter(
            emptyList(),
            onItemClick = null
        )
        binding.latestExpensesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expensesAdapter
        }

        showTotalsLoading(true)

        return root
    }

    override fun onResume() {
        super.onResume()
        loadLatestExpenses()
        loadTotalsFromBudgets()
    }

    private fun loadTotalsFromBudgets() {
        val uid = auth.currentUser?.uid ?: return

        showTotalsLoading(true)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val budgets =
                    (userDoc.get("budgetsAccessed") as? List<*>)?.mapNotNull { it?.toString() }
                        ?: emptyList()

                if (budgets.isEmpty()) {
                    binding.receivableAmount.text = df2.format(0.0)
                    binding.debtAmount.text = df2.format(0.0)
                    showTotalsLoading(false)
                    return@addOnSuccessListener
                }

                var done = 0
                var sumReceivable = 0.0
                var sumDebt = 0.0

                budgets.forEach { bid ->
                    db.collection("budgets").document(bid)
                        .collection("totals").document(uid)
                        .get()
                        .addOnSuccessListener { tdoc ->
                            sumReceivable += tdoc.getDouble("receivable") ?: 0.0
                            sumDebt += tdoc.getDouble("debt") ?: 0.0
                        }
                        .addOnCompleteListener {
                            done++
                            if (done == budgets.size) {
                                binding.receivableAmount.text = df2.format(sumReceivable)
                                binding.debtAmount.text = df2.format(sumDebt)
                                showTotalsLoading(false)
                            }
                        }
                }
            }
            .addOnFailureListener {
                binding.receivableAmount.text = df2.format(0.0)
                binding.debtAmount.text = df2.format(0.0)
                showTotalsLoading(false)
            }
    }

    private fun showTotalsLoading(loading: Boolean) {
        // When loading: show the small spinners and HIDE the numbers completely
        binding.receivableLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.debtLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.receivableAmount.visibility = if (loading) View.GONE else View.VISIBLE
        binding.debtAmount.visibility = if (loading) View.GONE else View.VISIBLE
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
                    val label = df2.format(total)
                    listItems.add(
                        ExpenseListItem.Header(
                            date.format(formatted),
                            label,
                            total >= 0
                        )
                    )

                    entries.forEach { (date2, name, category, amount, type, expenseId) ->
                        val displayAmount = df2.format(amount)
                        listItems.add(
                            ExpenseListItem.Item(
                                R.drawable.ic_home_white_24dp,
                                name,
                                budgetId = "null",
                                expenseIdInBudget = "null",
                                category,
                                displayAmount,
                                date2.toString(),
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
