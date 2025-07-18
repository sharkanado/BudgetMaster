package com.example.budgetmaster

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MyWallet : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_my_wallet)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val dummyData = listOf(
            ExpenseListItem.Header("17 Jul 2025", "-300 PLN"),
            ExpenseListItem.Item(R.drawable.ic_home_white_24dp, "Dog food", "Budget1", "-229.90 PLN"),
            ExpenseListItem.Item(R.drawable.ic_home_white_24dp, "Toys", "Budget1", "-70.10 PLN"),
            ExpenseListItem.Header("16 Jul 2025", "-120 PLN"),
            ExpenseListItem.Item(R.drawable.ic_home_white_24dp, "Vet visit", "Budget2", "-120 PLN")
        )
        val recycler = findViewById<RecyclerView>(R.id.expensesRecyclerView)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = ExpensesAdapter(dummyData)
    }
}