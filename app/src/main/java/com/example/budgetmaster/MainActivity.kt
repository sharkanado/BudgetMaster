package com.example.budgetmaster

import android.content.Intent
import android.os.Bundle
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.example.budgetmaster.databinding.ActivityMainBinding
import androidx.core.view.WindowCompat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)

        // Example login check — replace with real logic
        val userIsSignedIn = false

        if (!userIsSignedIn) {
            startActivity(Intent(this, SignUpActivity::class.java))
            finish()
            return // prevent any further setup
        }

        // Proceed with normal MainActivity setup
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        navView.setupWithNavController(navController)
    }
}
