package com.example.budgetmaster.utils

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

fun updateLatestExpenses(
    uid: String,
    expenseData: HashMap<String, Comparable<*>>,
    limit: Long = 10
) {
    val db = FirebaseFirestore.getInstance()
    val latestRef = db.collection("users").document(uid).collection("latest")

    // Get newest first
    latestRef
        .orderBy("timestamp", Query.Direction.DESCENDING)
        .get()
        .addOnSuccessListener { snapshot ->
            val batch = db.batch()

            // Remove extras beyond `limit - 1` (because we're about to add 1 more)
            if (snapshot.size() >= limit) {
                val toRemove = snapshot.documents.drop((limit - 1).toInt())
                for (doc in toRemove) {
                    batch.delete(doc.reference)
                }
            }

            // Add the new expense to latest
            val newDoc = latestRef.document()
            batch.set(newDoc, expenseData)
            batch.commit()
        }
}
