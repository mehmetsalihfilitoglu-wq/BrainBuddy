package com.mioacademy.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.R
import com.mioacademy.app.auth.AuthProvider
import com.mioacademy.app.auth.AuthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.loginEmail)
        val passwordInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.loginPassword)
        val errorText = findViewById<android.widget.TextView>(R.id.loginError)
        val btnLogin = findViewById<android.widget.Button>(R.id.btnLogin)
        val btnRegister = findViewById<android.widget.TextView>(R.id.btnGoRegister)
        val btnForgot = findViewById<android.widget.TextView>(R.id.btnForgotPassword)

        btnForgot?.visibility = android.view.View.GONE  // Hide when using stub (no Firebase)

        btnLogin.setOnClickListener {
            errorText.text = ""
            val email = emailInput.text?.toString()?.trim() ?: ""
            val password = passwordInput.text?.toString() ?: ""
            if (email.isBlank() || password.isBlank()) {
                errorText.text = getString(R.string.auth_fill_all)
                return@setOnClickListener
            }
            btnLogin.isEnabled = false
            CoroutineScope(Dispatchers.Main).launch {
                when (val r = AuthProvider.get(this@LoginActivity).signIn(email, password)) {
                    is AuthResult.Success -> {
                        setResult(RESULT_OK)
                        finish()
                    }
                    is AuthResult.Error -> {
                        errorText.text = r.message
                        btnLogin.isEnabled = true
                    }
                }
            }
        }

        btnRegister?.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
