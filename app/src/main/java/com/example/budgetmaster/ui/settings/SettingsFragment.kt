package com.example.budgetmaster.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.databinding.FragmentSettingsBinding
import com.example.budgetmaster.ui.notifications.MenuAdapter
import com.example.budgetmaster.ui.notifications.MenuItem

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // Define menu actions here
    private val menuItems by lazy {
        listOf(
            MenuItem("Sign Out") { signOut() },
            MenuItem("Change Password") { changePassword() }
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)

        binding.menuRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.menuRecyclerView.adapter = MenuAdapter(menuItems)

        return binding.root
    }

    private fun signOut() {
        // TODO: Replace with real logic
        println("User signed out")
    }

    private fun changePassword() {
        // TODO: Replace with real logic
        println("Change password clicked")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
