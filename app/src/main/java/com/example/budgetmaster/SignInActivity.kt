package com.example.budgetmaster

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SignInActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_sign_in)
        val signInButton = findViewById<Button>(R.id.sign_in_button)
        signInButton.setOnClickListener {

        }
    }
}
