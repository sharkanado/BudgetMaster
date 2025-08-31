package com.example.budgetmaster.ui.fragments.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.budgetmaster.R
import com.example.budgetmaster.databinding.FragmentDashboardBinding
import com.example.budgetmaster.ui.activities.AddExpense
import com.example.budgetmaster.ui.activities.MyWallet
import com.example.budgetmaster.utils.ExpenseListItem
import com.example.budgetmaster.utils.ExpensesAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var expensesAdapter: ExpensesAdapter
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val dotSyms = DecimalFormatSymbols(Locale.ENGLISH).apply {
        decimalSeparator = '.'
        groupingSeparator = ','
    }
    private val df2 = DecimalFormat("0.00").apply {
        decimalFormatSymbols = dotSyms
        isGroupingUsed = false
    }

    private var mainCurrency: String = "PLN"
    private var eurRatesLatest: Map<String, Double> = emptyMap() // EUR -> CODE
    private var eurToMainRate: Double = 1.0                      // EUR -> main

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        val root = binding.root

        binding.addExpenseButton.setOnClickListener {
            startActivity(Intent(requireContext(), AddExpense::class.java))
        }
        binding.myExpensesBlock.setOnClickListener {
            startActivity(Intent(requireContext(), MyWallet::class.java))
        }
        binding.debtBlock.setOnClickListener {
            requireActivity()
                .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.navigationView)
                .selectedItemId = R.id.navigationBudgets
        }
        binding.receivableBlock.setOnClickListener {
            requireActivity()
                .findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.navigationView)
                .selectedItemId = R.id.navigationBudgets
        }
        binding.seeAllButton.setOnClickListener {
            startActivity(Intent(requireContext(), MyWallet::class.java))
        }

        expensesAdapter = ExpensesAdapter(
            emptyList(),
            currencyCode = mainCurrency,
            onItemClick = null
        )
        binding.latestExpensesRecycler.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = expensesAdapter
        }

        showTotalsLoading(true)

        return root
    }

    override fun onResume() {
        super.onResume()
        refreshCurrencyAndRatesThen {
            loadLatestExpenses()
            loadTotalsFromBudgets()
        }
    }

    private fun refreshCurrencyAndRatesThen(afterReady: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                mainCurrency =
                    (userDoc.getString("mainCurrency") ?: "PLN").uppercase(Locale.ENGLISH)
                expensesAdapter.updateCurrency(mainCurrency) // for latest headers (if used)
                // Titles like "Debt (CZK)" / "Receivable (CZK)"
                binding.root.findViewById<TextView>(R.id.debtLabel)?.text =
                    getString(R.string.dashboard_debt_title_fmt, mainCurrency)
                binding.root.findViewById<TextView>(R.id.receivableLabel)?.text =
                    getString(R.string.dashboard_receivable_title_fmt, mainCurrency)
            }
            .addOnFailureListener {
                mainCurrency = "PLN"
                expensesAdapter.updateCurrency(mainCurrency)
                binding.root.findViewById<TextView>(R.id.debtLabel)?.text =
                    getString(R.string.dashboard_debt_title_fmt, mainCurrency)
                binding.root.findViewById<TextView>(R.id.receivableLabel)?.text =
                    getString(R.string.dashboard_receivable_title_fmt, mainCurrency)
            }
            .addOnCompleteListener {
                MainScope().launch {
                    eurRatesLatest =
                        withContext(Dispatchers.IO) { fetchEurRatesLatest() } ?: emptyMap()
                    eurToMainRate =
                        if (mainCurrency == "EUR") 1.0 else (eurRatesLatest[mainCurrency] ?: 1.0)
                    afterReady()
                }
            }
    }

    private fun loadTotalsFromBudgets() {
        val uid = auth.currentUser?.uid ?: return

        showTotalsLoading(true)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { userDoc ->
                val budgets =
                    (userDoc.get("budgetsAccessed") as? List<*>)?.mapNotNull { it?.toString() }
                        ?: emptyList()

                if (budgets.isEmpty()) {
                    binding.receivableAmount.text = df2.format(0.0)
                    binding.debtAmount.text = df2.format(0.0)
                    showTotalsLoading(false)
                    return@addOnSuccessListener
                }

                val netPerBudget = mutableMapOf<String, Double>()
                var done = 0

                budgets.forEach { bid ->
                    db.collection("budgets").document(bid)
                        .collection("totals").document(uid)
                        .get()
                        .addOnSuccessListener { tdoc ->
                            val receivable = readBudgetAmountInMain(tdoc, "receivable")
                            val debt = readBudgetAmountInMain(tdoc, "debt")

                            // net for this budget = receivable - debt
                            val net = receivable - debt
                            netPerBudget[bid] = net
                        }
                        .addOnCompleteListener {
                            done++
                            if (done == budgets.size) {
                                // after all budgets loaded
                                var sumReceivableMain = 0.0
                                var sumDebtMain = 0.0

                                for (net in netPerBudget.values) {
                                    if (net > 0) sumReceivableMain += net
                                    if (net < 0) sumDebtMain += -net
                                }

                                binding.receivableAmount.text = df2.format(sumReceivableMain)
                                binding.debtAmount.text = df2.format(sumDebtMain)
                                showTotalsLoading(false)
                            }
                        }
                }
            }
            .addOnFailureListener {
                binding.receivableAmount.text = df2.format(0.0)
                binding.debtAmount.text = df2.format(0.0)
                showTotalsLoading(false)
            }
    }


    private fun showTotalsLoading(loading: Boolean) {
        binding.receivableLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.debtLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.receivableAmount.visibility = if (loading) View.GONE else View.VISIBLE
        binding.debtAmount.visibility = if (loading) View.GONE else View.VISIBLE
    }


    private fun loadLatestExpenses() {
        val uid = auth.currentUser?.uid ?: return

        binding.loadingProgressBar.visibility = View.VISIBLE
        binding.noDataText.visibility = View.GONE
        binding.latestExpensesRecycler.visibility = View.GONE

        db.collection("users")
            .document(uid)
            .collection("latest")
            .get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    binding.loadingProgressBar.visibility = View.GONE
                    binding.noDataText.visibility = View.VISIBLE
                    binding.latestExpensesRecycler.visibility = View.GONE
                    expensesAdapter.updateItems(emptyList())
                    return@addOnSuccessListener
                }

                val latestDocs = result.documents
                val enriched = mutableListOf<LatestEnriched>()
                var done = 0

                for (doc in latestDocs) {
                    val dateStr = doc.getString("date")
                    if (dateStr == null) {
                        done++
                        if (done == latestDocs.size) renderLatest(enriched)
                        continue
                    }
                    val expenseId = doc.getString("expenseId")
                    if (expenseId == null) {
                        done++
                        if (done == latestDocs.size) renderLatest(enriched)
                        continue
                    }
                    val type = (doc.getString("type") ?: "expense").lowercase(Locale.ENGLISH)
                    val name = doc.getString("description") ?: ""
                    val category = doc.getString("category") ?: "Other"

                    val parsed = LocalDate.parse(dateStr)
                    val year = parsed.year.toString()
                    val monthName =
                        parsed.month.name.lowercase().replaceFirstChar { it.uppercase() }

                    db.collection("users").document(uid)
                        .collection("expenses").document(year)
                        .collection(monthName).document(expenseId)
                        .get()
                        .addOnSuccessListener { expDoc ->
                            val curOrig =
                                (expDoc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
                            val amountOrig = readAmount(expDoc.get("amount"))
                            val signedOrig = if (type == "expense") -amountOrig else amountOrig

                            val unsignedMain = amountInMainUnsigned(expDoc) ?: 0.0
                            val signedMain = if (type == "expense") -unsignedMain else unsignedMain

                            enriched.add(
                                LatestEnriched(
                                    date = parsed,
                                    name = name,
                                    category = category,
                                    displayAmountOriginal = "${df2.format(signedOrig)} $curOrig",
                                    signedMain = signedMain,
                                    type = type
                                )
                            )
                        }
                        .addOnFailureListener {
                            // Fallback if source expense missing
                            val amountLatest = readAmount(doc.get("amount"))
                            val signedOrig = if (type == "expense") -amountLatest else amountLatest
                            enriched.add(
                                LatestEnriched(
                                    date = parsed,
                                    name = name,
                                    category = category,
                                    displayAmountOriginal = df2.format(signedOrig),
                                    signedMain = signedOrig,
                                    type = type
                                )
                            )
                        }
                        .addOnCompleteListener {
                            done++
                            if (done == latestDocs.size) {
                                renderLatest(enriched)
                            }
                        }
                }
            }
            .addOnFailureListener {
                binding.loadingProgressBar.visibility = View.GONE
                binding.noDataText.visibility = View.VISIBLE
                binding.latestExpensesRecycler.visibility = View.GONE
            }
    }

    private fun renderLatest(rows: List<LatestEnriched>) {
        val grouped = rows.groupBy { it.date }.toSortedMap(compareByDescending { it })
        val listItems = mutableListOf<ExpenseListItem>()
        val formatted = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())

        for ((date, entries) in grouped) {
            val totalMain = entries.sumOf { it.signedMain }
            val headerLabel = df2.format(totalMain)
            listItems.add(
                ExpenseListItem.Header(
                    date.format(formatted),
                    headerLabel,
                    totalMain >= 0
                )
            )

            entries.forEach { e ->
                listItems.add(
                    ExpenseListItem.Item(
                        R.drawable.ic_home_white_24dp,
                        e.name,
                        budgetId = "null",
                        expenseIdInBudget = "null",
                        e.category,
                        e.displayAmountOriginal,  // original only
                        e.date.toString(),
                        e.type,
                        id = "" // no click from dashboard
                    )
                )
            }
        }

        binding.loadingProgressBar.visibility = View.GONE
        if (listItems.isEmpty()) {
            binding.noDataText.visibility = View.VISIBLE
            binding.latestExpensesRecycler.visibility = View.GONE
        } else {
            binding.noDataText.visibility = View.GONE
            binding.latestExpensesRecycler.visibility = View.VISIBLE
            expensesAdapter.updateItems(listItems)
        }
    }

    // ---------- conversion helpers (same logic as MyWallet) ----------

    /** Amount in MAIN currency, unsigned. No-recalc if original currency == main. */
    private fun amountInMainUnsigned(expenseDoc: DocumentSnapshot): Double? {
        val cur = (expenseDoc.getString("currency") ?: "EUR").uppercase(Locale.ENGLISH)
        val amountOrig = readAmount(expenseDoc.get("amount"))
        if (cur == mainCurrency.uppercase(Locale.ENGLISH)) {
            return abs(amountOrig)
        }
        val amountBase = (expenseDoc.get("amountBase") as? Number)?.toDouble()
            ?: run {
                if (cur == "EUR") amountOrig
                else {
                    val eurToCur = eurRatesLatest[cur] ?: return null
                    val curToEur = 1.0 / eurToCur
                    amountOrig * curToEur
                }
            }
        return abs(amountBase * eurToMainRate)
    }

    private fun readBudgetAmountInMain(doc: DocumentSnapshot, field: String): Double {
        val baseVal = (doc.get("${field}Base") as? Number)?.toDouble()
        if (baseVal != null) return baseVal * eurToMainRate

        val amount = (doc.get(field) as? Number)?.toDouble() ?: 0.0
        val cur = (doc.getString("${field}Currency")
            ?: doc.getString("currency")
            ?: "EUR").uppercase(Locale.ENGLISH)

        return when {
            cur == mainCurrency.uppercase(Locale.ENGLISH) -> amount
            cur == "EUR" -> amount * eurToMainRate
            else -> {
                val eurToCur =
                    eurRatesLatest[cur] ?: return amount
                val curToEur = 1.0 / eurToCur
                (amount * curToEur) * eurToMainRate
            }
        }
    }

    private fun readAmount(raw: Any?): Double = when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.replace(",", ".").toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun fetchEurRatesLatest(): Map<String, Double>? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://api.frankfurter.dev/v1/latest?from=EUR")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val rates = json.optJSONObject("rates") ?: return emptyMap()
            val out = mutableMapOf<String, Double>()
            val keys = rates.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                out[k.trim().uppercase(Locale.ENGLISH)] = rates.getDouble(k)
            }
            out
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class LatestEnriched(
        val date: LocalDate,
        val name: String,
        val category: String,
        val displayAmountOriginal: String, // e.g., "-155.00 CZK"
        val signedMain: Double,            // for header totals in main currency
        val type: String
    )
}
