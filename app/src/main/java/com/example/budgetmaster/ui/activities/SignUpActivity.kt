package com.example.budgetmaster.ui.activities

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.budgetmaster.R
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SignUpActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    private lateinit var nameEditText: TextInputEditText
    private lateinit var emailEditText: TextInputEditText
    private lateinit var passwordEditText: TextInputEditText
    private lateinit var repeatPasswordEditText: TextInputEditText
    private lateinit var currencyDropdown: MaterialAutoCompleteTextView
    private lateinit var signUpButton: Button
    private lateinit var signUpSignInText: TextView

    private var currencies: Map<String, String> = emptyMap()
    private var defaultCurrency = "PLN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_sign_up)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        auth = Firebase.auth

        nameEditText = findViewById(R.id.nameEditText)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        repeatPasswordEditText = findViewById(R.id.repeatPasswordEditText)
        currencyDropdown = findViewById(R.id.currencyDropdown)
        signUpButton = findViewById(R.id.signUpButton)
        signUpSignInText = findViewById(R.id.signUpSignInText)

        loadCurrenciesAndBind()

        signUpSignInText.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
        }

        signUpButton.setOnClickListener {
            val name = nameEditText.text?.toString()?.trim().orEmpty()
            val email = emailEditText.text?.toString()?.trim().orEmpty()
            val password = passwordEditText.text?.toString()?.trim().orEmpty()
            val repeatPassword = repeatPasswordEditText.text?.toString()?.trim().orEmpty()
            val currencyCode = currentCurrencyCode()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || repeatPassword.isEmpty() || currencyCode.isEmpty()) {
                Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show()
            } else if (password != repeatPassword) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
            } else if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters.", Toast.LENGTH_SHORT)
                    .show()
            } else if (currencyCode.length !in 3..4) {
                Toast.makeText(this, "Please select a valid currency.", Toast.LENGTH_SHORT).show()
            } else {
                createAccount(email, password, name, currencyCode.uppercase())
            }
        }
    }

    public override fun onStart() {
        super.onStart()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            // already signed in flow if you need it
        }
    }

    private fun loadCurrenciesAndBind() {
        lifecycleScope.launch {
            val fetched = withContext(Dispatchers.IO) { fetchCurrenciesFrankfurter() }
            currencies = if (fetched.isNotEmpty()) fetched else fallbackCurrencies()

            val items = currencies.entries.sortedBy { it.key }.map { "${it.key} — ${it.value}" }
            val adapter =
                ArrayAdapter(this@SignUpActivity, android.R.layout.simple_list_item_1, items)
            currencyDropdown.setAdapter(adapter)

            val preselect = items.firstOrNull {
                it.startsWith("$defaultCurrency ") || it.startsWith("$defaultCurrency—") || it.startsWith(
                    "$defaultCurrency —"
                )
            } ?: "$defaultCurrency — ${currencies[defaultCurrency] ?: ""}"
            currencyDropdown.setText(preselect, false)
        }
    }

    private fun currentCurrencyCode(): String {
        val raw = currencyDropdown.text?.toString()?.trim().orEmpty()
        return raw.substringBefore("—").trim().ifEmpty { raw.trim() }
    }

    private fun createAccount(email: String, password: String, name: String, mainCurrency: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    sendEmailVerification()
                    Toast.makeText(this, "Sign-up successful!", Toast.LENGTH_SHORT).show()

                    val uid = auth.currentUser?.uid ?: return@addOnCompleteListener
                    val db = Firebase.firestore

                    val user = hashMapOf(
                        "uid" to uid,
                        "email" to email,
                        "name" to name,
                        "mainCurrency" to mainCurrency,
                        "createdAt" to System.currentTimeMillis()
                    )

                    db.collection("users").document(uid)
                        .set(user)
                        .addOnSuccessListener {
                            db.collection("usersByEmail").document(email)
                                .set(mapOf("uid" to uid))
                                .addOnSuccessListener { /* ok */ }
                                .addOnFailureListener { e ->
                                    Toast.makeText(
                                        this,
                                        "Mapping error: ${e.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }

                            startActivity(Intent(this, SignInActivity::class.java))
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                } else {
                    Toast.makeText(
                        this,
                        "Sign-up failed: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun sendEmailVerification() {
        val user = auth.currentUser!!
        user.sendEmailVerification().addOnCompleteListener(this) { /* no-op */ }
    }

    private fun updateUI(user: FirebaseUser?) {}
    private fun reload() {}

    private fun fetchCurrenciesFrankfurter(): Map<String, String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.frankfurter.dev/v1/currencies")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            conn.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val result = mutableMapOf<String, String>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val code = keys.next()
                    result[code] = json.getString(code)
                }
                result
            }
        } catch (_: Exception) {
            emptyMap()
        } finally {
            conn?.disconnect()
        }
    }

    private fun fallbackCurrencies(): Map<String, String> = mapOf(
        "PLN" to "Polish Złoty",
        "EUR" to "Euro",
        "USD" to "US Dollar",
        "GBP" to "British Pound",
        "CHF" to "Swiss Franc"
    )
}
