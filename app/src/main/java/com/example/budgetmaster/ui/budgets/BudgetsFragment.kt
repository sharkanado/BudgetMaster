package com.example.budgetmaster.ui.budgets

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.budgetmaster.databinding.FragmentBudgetsBinding
import com.example.budgetmaster.CreateBudget

class BudgetsFragment : Fragment() {

    private var _binding: FragmentBudgetsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val budgetsViewModel =
            ViewModelProvider(this).get(BudgetsViewModel::class.java)

        _binding = FragmentBudgetsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.greetingText
        budgetsViewModel.text.observe(viewLifecycleOwner) {
            textView.text = it
        }

        binding.addWalletButton.setOnClickListener {
            val intent = Intent(requireContext(), CreateBudget::class.java)
            startActivity(intent)
        }

        return root


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}