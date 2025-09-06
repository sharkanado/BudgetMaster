package com.example.budgetmaster.ui.activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetmaster.R
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AddNewBudget : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val currentUser = FirebaseAuth.getInstance().currentUser

    private lateinit var currencySpinner: AutoCompleteTextView
    private lateinit var addMemberBtn: Button
    private lateinit var membersContainer: LinearLayout
    private lateinit var budgetNameInput: TextInputEditText
    private lateinit var saveBudgetBtn: Button

    private var currencyMap: Map<String, String> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_create_budget)

        val contentView = findViewById<View>(android.R.id.content)
        val rootChild = (contentView as? ViewGroup)?.getChildAt(0)
        ViewCompat.setOnApplyWindowInsetsListener(contentView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            rootChild?.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

        currencySpinner = findViewById(R.id.currencySpinner)
        addMemberBtn = findViewById(R.id.addMemberBtn)
        membersContainer = findViewById(R.id.membersContainer)
        budgetNameInput = findViewById(R.id.budgetNameInput)
        saveBudgetBtn = findViewById(R.id.saveBudgetBtn)

        setupCurrencyDropdown()
        setupAddMemberButton()

        saveBudgetBtn.setOnClickListener {
            createBudget()
        }
    }

    private fun setupCurrencyDropdown() {
        CoroutineScope(Dispatchers.IO).launch {
            val map = fetchCurrencies()
            withContext(Dispatchers.Main) {
                if (map.isNotEmpty()) {
                    currencyMap = map
                    val displayList = map.entries.map { "${it.key} — ${it.value}" }.sorted()
                    val adapter = ArrayAdapter(
                        this@AddNewBudget,
                        androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
                        displayList
                    )
                    currencySpinner.setAdapter(adapter)
                } else {
                    val fallback = mapOf(
                        "EUR" to "Euro",
                        "USD" to "US Dollar",
                        "GBP" to "British Pound",
                        "PLN" to "Polish Zloty"
                    )
                    currencyMap = fallback
                    val displayList = fallback.entries.map { "${it.key} — ${it.value}" }
                    val adapter = ArrayAdapter(
                        this@AddNewBudget,
                        androidx.appcompat.R.layout.support_simple_spinner_dropdown_item,
                        displayList
                    )
                    currencySpinner.setAdapter(adapter)
                }
            }
        }
    }

    private fun setupAddMemberButton() {
        addMemberBtn.setOnClickListener {
            val newMemberView = LayoutInflater.from(this)
                .inflate(R.layout.item_member_input, membersContainer, false)

            val removeBtn = newMemberView.findViewById<ImageView>(R.id.removeMemberBtn)
            removeBtn.setOnClickListener {
                membersContainer.removeView(newMemberView)
            }

            membersContainer.addView(newMemberView)
        }
    }

    private fun createBudget() {
        val budgetName = budgetNameInput.text.toString().trim()
        val raw = currencySpinner.text.toString().trim()
        val code = raw.substringBefore("—").trim().uppercase()

        if (budgetName.isEmpty()) {
            Toast.makeText(this, "Please enter a budget name", Toast.LENGTH_SHORT).show()
            return
        }
        if (code.isEmpty()) {
            Toast.makeText(this, "Please select a currency", Toast.LENGTH_SHORT).show()
            return
        }

        val memberEmails = mutableListOf<String>()
        for (i in 0 until membersContainer.childCount) {
            val memberView = membersContainer.getChildAt(i)
            val emailField = memberView.findViewById<TextInputEditText>(R.id.memberEmailInput)
            val email = emailField.text?.toString()?.trim()
            if (!email.isNullOrEmpty()) {
                if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    memberEmails.add(email)
                } else {
                    Toast.makeText(this, "Invalid email: $email", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        val ownerId = currentUser?.uid ?: return
        val budgetId = UUID.randomUUID().toString()

        resolveEmailsToUids(memberEmails) { resolvedMemberIds ->
            val allMembers = listOf(ownerId) + resolvedMemberIds

            val metadata = hashMapOf(
                "name" to budgetName,
                "ownerId" to ownerId,
                "members" to allMembers,
                "currency" to code,
                "createdAt" to com.google.firebase.Timestamp.now(),
                "budget" to "personal"
            )

            db.collection("budgets").document(budgetId)
                .set(metadata)
                .addOnSuccessListener {
                    updateBudgetsAccessedForMembers(allMembers, budgetId)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error creating budget: ${e.message}", Toast.LENGTH_SHORT)
                        .show()
                }
        }
    }

    private fun resolveEmailsToUids(emails: List<String>, callback: (List<String>) -> Unit) {
        if (emails.isEmpty()) {
            callback(emptyList())
            return
        }

        val uids = mutableListOf<String>()
        var processed = 0

        for (email in emails) {
            db.collection("usersByEmail").document(email).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        val uid = doc.getString("uid")
                        if (uid != null) uids.add(uid)
                    } else {
                        Toast.makeText(this, "User not found: $email", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Lookup failed for $email", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    processed++
                    if (processed == emails.size) {
                        callback(uids)
                    }
                }
        }
    }

    private fun updateBudgetsAccessedForMembers(memberIds: List<String>, budgetId: String) {
        var updated = 0
        for (uid in memberIds) {
            db.collection("users")
                .document(uid)
                .update(
                    "budgetsAccessed",
                    com.google.firebase.firestore.FieldValue.arrayUnion(budgetId)
                )
                .addOnCompleteListener {
                    updated++
                    if (updated == memberIds.size) {
                        Toast.makeText(this, "Budget created successfully!", Toast.LENGTH_SHORT)
                            .show()
                        finish()
                    }
                }
        }
    }

    private fun fetchCurrencies(): Map<String, String> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.frankfurter.dev/v1/currencies")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val map = mutableMapOf<String, String>()
            val keys = json.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val name = json.getString(code)
                map[code.uppercase()] = name
            }
            map
        } catch (_: Exception) {
            emptyMap()
        } finally {
            conn?.disconnect()
        }
    }
}
