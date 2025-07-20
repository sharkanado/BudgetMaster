package com.example.budgetmaster.ui.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetmaster.R
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePassword : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_password)

        auth = FirebaseAuth.getInstance()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val currentPassword = findViewById<EditText>(R.id.currentPasswordEditText)
        val newPassword = findViewById<EditText>(R.id.newPasswordEditText)
        val repeatPassword = findViewById<EditText>(R.id.repeatPasswordEditText)
        val changeButton = findViewById<Button>(R.id.signInButton)

        changeButton.setOnClickListener {
            val current = currentPassword.text.toString()
            val new = newPassword.text.toString()
            val repeat = repeatPassword.text.toString()

            if (current.isEmpty() || new.isEmpty() || repeat.isEmpty()) {
                showToast("Please, fill in all fields!")
                return@setOnClickListener
            }

            if (new != repeat) {
                showToast("New passwords do not match")
                return@setOnClickListener
            }

            val user = auth.currentUser
            val email = user?.email

            if (user != null && email != null) {
                val credential = EmailAuthProvider.getCredential(email, current)

                user.reauthenticate(credential)
                    .addOnSuccessListener {
                        user.updatePassword(new)
                            .addOnSuccessListener {
                                showToast("Password changed successfully")
                                finish()
                            }
                            .addOnFailureListener {
                                showToast("Failed to update password: ${it.message}")
                            }
                    }
                    .addOnFailureListener {
                        showToast("Re-authentication failed: ${it.message}")
                    }
            } else {
                showToast("User not logged in")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
