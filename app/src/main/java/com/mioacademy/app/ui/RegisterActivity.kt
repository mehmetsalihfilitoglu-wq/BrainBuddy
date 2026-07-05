package com.mioacademy.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.R
import com.mioacademy.app.auth.AuthProvider
import com.mioacademy.app.auth.AuthResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val emailInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.registerEmail)
        val passwordInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.registerPassword)
        val confirmInput = findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.registerConfirm)
        val errorText = findViewById<android.widget.TextView>(R.id.registerError)
        val btnRegister = findViewById<android.widget.Button>(R.id.btnRegister)
        val btnLogin = findViewById<android.widget.TextView>(R.id.btnGoLogin)

        btnRegister.setOnClickListener {
            errorText.text = ""
            val email = emailInput.text?.toString()?.trim() ?: ""
            val password = passwordInput.text?.toString() ?: ""
            val confirm = confirmInput.text?.toString() ?: ""
            if (email.isBlank() || password.isBlank()) {
                errorText.text = getString(R.string.auth_fill_all)
                return@setOnClickListener
            }
            if (password.length < 6) {
                errorText.text = getString(R.string.auth_password_short)
                return@setOnClickListener
            }
            if (password != confirm) {
                errorText.text = getString(R.string.auth_password_mismatch)
                return@setOnClickListener
            }
            btnRegister.isEnabled = false
            CoroutineScope(Dispatchers.Main).launch {
                when (val r = AuthProvider.get(this@RegisterActivity).signUp(email, password)) {
                    is AuthResult.Success -> {
                        setResult(RESULT_OK)
                        finish()
                    }
                    is AuthResult.Error -> {
                        errorText.text = r.message
                        btnRegister.isEnabled = true
                    }
                }
            }
        }

        btnLogin?.setOnClickListener { finish() }
    }
}
