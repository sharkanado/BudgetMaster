package com.example.budgetmaster.ui.activities

import ExpenseListItem
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.budgetmaster.R

class ExpenseDetailsWallet : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val expenseItem = intent.getParcelableExtra<ExpenseListItem.Item>("expense_item")

        if (expenseItem != null) {
            Toast.makeText(this, "Received item: ${expenseItem.amount}", Toast.LENGTH_SHORT).show()
        }
        setContentView(R.layout.activity_expense_details_wallet)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}