// ExpenseUtils.kt
package com.example.budgetmaster.utils

import com.google.firebase.firestore.FirebaseFirestore

fun updateLatestExpenses(
    uid: String,
    expenseData: HashMap<String, Comparable<*>>,
    limit: Long = 10
) {
    val db = FirebaseFirestore.getInstance()
    val latestRef = db.collection("users").document(uid).collection("latest")

    latestRef
        .orderBy("timestamp")
        .get()
        .addOnSuccessListener { snapshot ->
            val batch = db.batch()

            if (snapshot.size() >= limit) {
                val toRemove = snapshot.documents.take((snapshot.size() - (limit - 1)).toInt())
                for (doc in toRemove) {
                    batch.delete(doc.reference)
                }
            }

            val newDoc = latestRef.document()
            batch.set(newDoc, expenseData)
            batch.commit()
        }
}
