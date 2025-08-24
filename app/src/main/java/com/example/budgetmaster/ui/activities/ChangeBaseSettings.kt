package com.example.budgetmaster.ui.activities

import android.content.res.ColorStateList
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.example.budgetmaster.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ChangeBaseSettings : AppCompatActivity() {

    private lateinit var nameEdit: TextInputEditText
    private lateinit var currencyDropdown: MaterialAutoCompleteTextView
    private lateinit var saveBtn: MaterialButton

    private var currencies: Map<String, String> = emptyMap()
    private var preselectCurrency: String = "PLN"
    private var originalName: String = ""
    private var originalCurrency: String = "PLN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_change_base_settings)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        nameEdit = findViewById(R.id.currentPasswordEditText)
        currencyDropdown = findViewById(R.id.currencyDropdown)
        saveBtn = findViewById(R.id.signInButton)

        setSaveBtnEnabled(false)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Not signed in.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        FirebaseFirestore.getInstance()
            .collection("users").document(uid)
            .get()
            .addOnSuccessListener { doc ->
                originalName = doc.getString("name") ?: ""
                nameEdit.setText(originalName)
                preselectCurrency = doc.getString("mainCurrency") ?: "PLN"
                originalCurrency = preselectCurrency
                loadCurrenciesAndBind()
            }
            .addOnFailureListener {
                originalName = ""
                preselectCurrency = "PLN"
                originalCurrency = "PLN"
                loadCurrenciesAndBind()
            }

        nameEdit.addTextChangedListener { updateSaveEnabled() }
        currencyDropdown.addTextChangedListener { updateSaveEnabled() }
        currencyDropdown.setOnItemClickListener { _, _, _, _ -> updateSaveEnabled() }

        saveBtn.setOnClickListener { saveChanges() }
    }

    private fun loadCurrenciesAndBind() {
        lifecycleScope.launch {
            val fetched = withContext(Dispatchers.IO) { fetchCurrenciesFrankfurter() }
            currencies = if (fetched.isNotEmpty()) fetched else fallbackCurrencies()
            val items = currencies.entries.sortedBy { it.key }.map { "${it.key} — ${it.value}" }
            val adapter =
                ArrayAdapter(this@ChangeBaseSettings, android.R.layout.simple_list_item_1, items)
            currencyDropdown.setAdapter(adapter)
            val candidate = items.firstOrNull {
                it.startsWith("$preselectCurrency ") || it.startsWith("$preselectCurrency—") || it.startsWith(
                    "$preselectCurrency —"
                )
            }
            currencyDropdown.setText(
                candidate ?: "$preselectCurrency — ${currencies[preselectCurrency] ?: ""}", false
            )
            originalCurrency = preselectCurrency.uppercase()
            updateSaveEnabled()
        }
    }

    private fun updateSaveEnabled() {
        val currentName = nameEdit.text?.toString()?.trim().orEmpty()
        val currencyCode = currentCurrencyCode()
        val nameChanged = currentName != originalName
        val currencyChanged = currencyCode.uppercase() != originalCurrency.uppercase()
        val validName = currentName.isNotEmpty()
        val validCurrency = currencyCode.length in 3..4
        setSaveBtnEnabled((nameChanged || currencyChanged) && validName && validCurrency)
    }

    private fun currentCurrencyCode(): String {
        val raw = currencyDropdown.text?.toString()?.trim().orEmpty()
        return raw.substringBefore("—").trim().ifEmpty { raw.trim() }
    }

    private fun saveChanges() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val uid = user.uid
        val name = nameEdit.text?.toString()?.trim().orEmpty()
        val currencyCode = currentCurrencyCode().uppercase()
        if (!saveBtn.isEnabled) return

        toggleUi(false)

        val updates = hashMapOf<String, Any>(
            "name" to name,
            "mainCurrency" to currencyCode,
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val db = FirebaseFirestore.getInstance()
        db.collection("users").document(uid)
            .set(updates, com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener {
                user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build())
                    .addOnCompleteListener {
                        Toast.makeText(this, "Settings saved.", Toast.LENGTH_SHORT).show()
                        toggleUi(true)
                        finish()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to save. Check connection and try again.",
                    Toast.LENGTH_SHORT
                ).show()
                toggleUi(true)
            }
    }

    private fun toggleUi(enabled: Boolean) {
        nameEdit.isEnabled = enabled
        currencyDropdown.isEnabled = enabled
        if (enabled) updateSaveEnabled() else setSaveBtnEnabled(false)
    }

    private fun setSaveBtnEnabled(enabled: Boolean) {
        saveBtn.isEnabled = enabled
        val bg = if (enabled) R.color.orange else R.color.grey_dark
        val fg = if (enabled) android.R.color.white else R.color.grey_light
        saveBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bg))
        saveBtn.setTextColor(ContextCompat.getColor(this, fg))
    }

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
