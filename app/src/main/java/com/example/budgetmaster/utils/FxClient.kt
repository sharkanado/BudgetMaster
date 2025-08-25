package com.example.budgetmaster.fx

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class FxSnapshot(
    val asOf: String,                // e.g., "2025-08-22"
    val rates: Map<String, Double>   // EUR->CODE (e.g., "PLN" to 4.32)
)

class FxClient {
    private val memory = mutableMapOf<String, FxSnapshot>()  // key: "date|latest"

    suspend fun getRatesEUR(asOf: String? = null): FxSnapshot? {
        val key = asOf ?: "latest"
        memory[key]?.let { return it }
        val snap = fetch(asOf) ?: return null
        memory[key] = snap
        return snap
    }

    private suspend fun fetch(asOf: String?): FxSnapshot? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = if (asOf != null)
                URL("https://api.frankfurter.dev/v1/$asOf?from=EUR")
            else
                URL("https://api.frankfurter.dev/v1/latest?from=EUR")

            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8000; readTimeout = 8000
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val date = json.optString("date", asOf ?: "latest")
            val rj = json.optJSONObject("rates") ?: return@withContext null
            val rates = rj.keys().asSequence().associateWith { k -> rj.getDouble(k) }
            FxSnapshot(asOf = date, rates = rates)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
