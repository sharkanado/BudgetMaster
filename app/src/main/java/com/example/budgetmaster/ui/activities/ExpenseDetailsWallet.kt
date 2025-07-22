package com.example.budgetmaster.ui.activities

import ExpenseListItem
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
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
        setContentView(R.layout.activity_expense_details_wallet)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val expenseItem: ExpenseListItem.Item? =
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra("expense_item", ExpenseListItem.Item::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra("expense_item")
            }


        if (expenseItem == null) {
            Toast.makeText(this, "No expense data received", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val titleText = if (expenseItem.type == "income") "Income Details" else "Expense Details"
        findViewById<TextView>(R.id.topBarTitle).text = titleText

        findViewById<TextView>(R.id.expenseTitle).text = expenseItem.name
        findViewById<TextView>(R.id.expenseAmount).text = expenseItem.amount
        findViewById<TextView>(R.id.expenseDate).text = expenseItem.date
        findViewById<TextView>(R.id.expenseCategory).text = expenseItem.budget
        findViewById<TextView>(R.id.expenseType).text =
            expenseItem.type.replaceFirstChar { it.uppercase() }
        findViewById<TextView>(R.id.expenseDescription).text = expenseItem.name

        findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        findViewById<ImageButton>(R.id.editButton).setOnClickListener {
            Toast.makeText(this, "Edit action not implemented", Toast.LENGTH_SHORT).show()
        }
    }


}
