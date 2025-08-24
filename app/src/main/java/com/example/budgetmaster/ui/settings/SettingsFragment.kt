package com.example.budgetmaster.ui.settings

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.databinding.FragmentSettingsBinding
import com.example.budgetmaster.ui.activities.ChangeBaseSettings
import com.example.budgetmaster.ui.activities.ChangePassword
import com.example.budgetmaster.ui.activities.SignInActivity
import com.google.firebase.auth.FirebaseAuth

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val menuItems by lazy {
        listOf(
            MenuItem("Change Password") { navigateToChangePassword() },
            MenuItem("Sign Out") { signOut() },
            MenuItem("Base Settings") { navigateToBaseSettings() },

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
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(requireContext(), SignInActivity::class.java)
        startActivity(intent)
        requireActivity().finish()

    }

    private fun navigateToChangePassword() {
        val intent = Intent(requireContext(), ChangePassword::class.java)
        startActivity(intent)


    }

    private fun navigateToBaseSettings() {
        val intent = Intent(requireContext(), ChangeBaseSettings::class.java)
        startActivity(intent)


    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
