package com.example.budgetmaster.ui.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.AddExpense
import com.example.budgetmaster.CreateBudget
import com.example.budgetmaster.ExpenseListItem
import com.example.budgetmaster.ExpensesAdapter
import com.example.budgetmaster.MyWallet
import com.example.budgetmaster.R
import com.example.budgetmaster.databinding.FragmentDashboardBinding

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var expensesAdapter: ExpensesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this).get(DashboardViewModel::class.java)

        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Launch Add Expense activity
        binding.addExpenseButton.setOnClickListener {
            val intent = Intent(requireContext(), AddExpense::class.java)
            startActivity(intent)
        }

        // Launch MyWallet activity
        binding.myExpensesBlock.setOnClickListener {
            val intent = Intent(requireContext(), MyWallet::class.java)
            startActivity(intent)
        }

        val dummyData = listOf(
            ExpenseListItem.Header("17 Jul 2025", "-300 PLN"),
            ExpenseListItem.Item(
                R.drawable.ic_home_white_24dp,
                "Dog food",
                "Budget1",
                "-229.90 PLN"
            ),
            ExpenseListItem.Item(R.drawable.ic_home_white_24dp, "Toys", "Budget1", "-70.10 PLN"),
            ExpenseListItem.Header("16 Jul 2025", "-120 PLN"),
            ExpenseListItem.Item(
                R.drawable.ic_home_white_24dp,
                "Vet visit",
                "Personal",
                "-120 PLN"
            ),
            ExpenseListItem.Header("17 Jul 2025", "-300 PLN"),
            ExpenseListItem.Item(
                R.drawable.ic_home_white_24dp,
                "Dog food",
                "Budget1",
                "-229.90 PLN"
            ),
            ExpenseListItem.Item(R.drawable.ic_home_white_24dp, "Toys", "Budget1", "-70.10 PLN"),
            ExpenseListItem.Header("16 Jul 2025", "-120 PLN"),
            ExpenseListItem.Item(R.drawable.ic_home_white_24dp, "Vet visit", "Personal", "-120 PLN")
        )

        expensesAdapter = ExpensesAdapter(dummyData)

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
