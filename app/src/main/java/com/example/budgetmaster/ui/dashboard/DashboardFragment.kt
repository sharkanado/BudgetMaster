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
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        binding.addExpenseButton.setOnClickListener {
            val intent = Intent(requireContext(), AddExpense::class.java)
            startActivity(intent)
        }

        binding.myExpensesBlock.setOnClickListener {
            val intent = Intent(requireContext(), MyWallet::class.java)
            startActivity(intent)
        }

        expensesAdapter = ExpensesAdapter(emptyList())

        binding.seeAllButton.setOnClickListener {
            val intent = Intent(requireContext(), MyWallet::class.java)
            startActivity(intent)
        }

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
        val now = LocalDate.now()
        val year = now.year.toString()
        val month = now.month.name.lowercase().replaceFirstChar { it.uppercase() }

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
                        val amount = doc.getDouble("amount") ?: 0.0
                        Triple(
                            parsedDate,
                            name,
                            Pair(category, String.format("%.2f", amount))
                        )
                    }
                    .groupBy { it.first }
                    .toSortedMap(compareByDescending { it })

                val listItems = mutableListOf<ExpenseListItem>()
                val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

                for ((date, entries) in grouped) {
                    val dailyTotal = entries.sumOf { (_, _, amountPair) ->
                        amountPair.second.split(" ")[0].replace(",", ".").toDoubleOrNull() ?: 0.0
                    }

                    val dailyLabel = "-${"%.2f".format(dailyTotal)}"

                    listItems.add(ExpenseListItem.Header(date.format(formatted), dailyLabel))

                    entries.forEach { (_, name, amountPair) ->
                        listItems.add(
                            ExpenseListItem.Item(
                                R.drawable.ic_home_white_24dp,
                                name,
                                amountPair.first,
                                "-${amountPair.second}"
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
}
