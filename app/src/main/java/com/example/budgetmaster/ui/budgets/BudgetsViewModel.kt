package com.example.budgetmaster.ui.budgets

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class BudgetsViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is budgets Fragment"
    }
    val text: LiveData<String> = _text
}