package com.mioacademy.app.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.mioacademy.app.R
import com.mioacademy.app.analytics.AnalyticsEvents
import com.mioacademy.app.analytics.AnalyticsProvider
import com.mioacademy.app.auth.AuthProvider
import com.mioacademy.app.auth.AuthProviderType
import com.mioacademy.app.auth.AuthResult
import kotlinx.coroutines.runBlocking

/**
 * Email/password sign-in and sign-up on the provider-agnostic [AuthProvider].
 * Federated (Google/Apple) buttons appear only when the credential provider reports
 * support — never as broken/fake buttons. Honest copy throughout; no backend required.
 */
class AuthActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density
    private fun dpi(v: Float) = (v * dp + 0.5f).toInt()

    private var signUpMode = false

    private lateinit var emailField: EditText
    private lateinit var passwordField: EditText
    private lateinit var errorText: TextView
    private lateinit var primaryButton: MaterialButton
    private lateinit var switchModeLink: TextView
    private lateinit var forgotLink: TextView
    private lateinit var titleView: TextView
    private lateinit var subtitleView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        signUpMode = intent.getStringExtra(EXTRA_MODE) == MODE_SIGN_UP

        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.white)); isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(20f), dpi(20f), dpi(24f))
        }
        scroll.addView(col); setContentView(scroll)

        col.addView(text("‹", 26f, R.color.textSecondary, bold = true).apply {
            setPadding(0, 0, dpi(12f), dpi(8f)); isClickable = true; setOnClickListener { finish() }
        })

        titleView = text("", 24f, R.color.textPrimary, bold = true)
        subtitleView = text("", 14f, R.color.textSecondary, topMargin = dpi(6f), lineMultiplier = 1.4f)
        col.addView(titleView); col.addView(subtitleView)

        col.addView(fieldLabel(getString(R.string.auth_email)))
        emailField = field(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_CLASS_TEXT)
        col.addView(emailField)

        col.addView(fieldLabel(getString(R.string.auth_password)))
        passwordField = field(InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT).apply {
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
        }
        col.addView(passwordField)

        errorText = text("", 13f, R.color.warning_text, topMargin = dpi(10f), lineMultiplier = 1.35f).apply {
            visibility = TextView.GONE
        }
        col.addView(errorText)

        primaryButton = MaterialButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpi(52f)
            ).also { it.topMargin = dpi(16f) }
            cornerRadius = dpi(14f)
            setOnClickListener { submit() }
        }
        col.addView(primaryButton)

        forgotLink = linkText(getString(R.string.auth_forgot)) { showResetDialog() }
        col.addView(forgotLink)

        // Federated buttons only if genuinely supported (none today) — never broken.
        val supported = AuthProvider.credentialProvider().supportedProviders()
        if (AuthProviderType.GOOGLE in supported) {
            col.addView(MaterialButton(this).apply {
                text = getString(R.string.auth_google)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dpi(52f)
                ).also { it.topMargin = dpi(12f) }
                cornerRadius = dpi(14f)
                setOnClickListener { /* wired when a real credential provider exists */ }
            })
        }

        switchModeLink = linkText("") { toggleMode() }
        col.addView(switchModeLink)

        applyMode()
    }

    private fun applyMode() {
        titleView.text = getString(if (signUpMode) R.string.auth_signup_title else R.string.auth_signin_title)
        subtitleView.text = getString(if (signUpMode) R.string.auth_signup_sub else R.string.auth_signin_sub)
        primaryButton.text = getString(if (signUpMode) R.string.auth_signup_cta else R.string.auth_signin_cta)
        switchModeLink.text = getString(if (signUpMode) R.string.auth_switch_to_signin else R.string.auth_switch_to_signup)
        forgotLink.visibility = if (signUpMode) TextView.GONE else TextView.VISIBLE
        errorText.visibility = TextView.GONE
    }

    private fun toggleMode() { signUpMode = !signUpMode; applyMode() }

    private fun submit() {
        val email = emailField.text.toString().trim()
        val password = passwordField.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.auth_fill_all)); return
        }
        setLoading(true)
        AnalyticsProvider.track(if (signUpMode) AnalyticsEvents.SIGN_UP_STARTED else AnalyticsEvents.SIGN_IN_STARTED)

        Thread {
            val repo = AuthProvider.repository(this@AuthActivity)
            val result = runBlocking {
                if (signUpMode) repo.signUpWithEmail(email, password, null)
                else repo.signInWithEmail(email, password)
            }
            runOnUiThread {
                setLoading(false)
                when (result) {
                    is AuthResult.Success -> {
                        AnalyticsProvider.track(
                            if (signUpMode) AnalyticsEvents.SIGN_UP_COMPLETED else AnalyticsEvents.SIGN_IN_COMPLETED)
                        AnalyticsProvider.tracker().setUserId(result.user.userId)
                        finish()
                    }
                    is AuthResult.Error -> showError(result.message)
                }
            }
        }.start()
    }

    private fun showResetDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS or InputType.TYPE_CLASS_TEXT
            setText(emailField.text.toString().trim())
            hint = getString(R.string.auth_email)
        }
        val pad = dpi(20f)
        val wrap = LinearLayout(this).apply { setPadding(pad, dpi(8f), pad, 0); addView(input) }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.auth_reset_title))
            .setMessage(getString(R.string.auth_reset_message))
            .setView(wrap)
            .setNegativeButton(getString(R.string.auth_cancel), null)
            .setPositiveButton(getString(R.string.auth_reset_cta)) { _, _ ->
                val email = input.text.toString().trim()
                AnalyticsProvider.track(AnalyticsEvents.PASSWORD_RESET_REQUESTED)
                Thread {
                    runBlocking { AuthProvider.repository(this@AuthActivity).sendPasswordReset(email) }
                    runOnUiThread {
                        Toast.makeText(this, getString(R.string.auth_reset_sent), Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
            .show()
    }

    private fun setLoading(loading: Boolean) {
        primaryButton.isEnabled = !loading
        primaryButton.text = getString(
            if (loading) R.string.auth_please_wait
            else if (signUpMode) R.string.auth_signup_cta else R.string.auth_signin_cta
        )
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = TextView.VISIBLE
    }

    // ── view builders ──
    private fun field(inputType: Int): EditText = EditText(this).apply {
        this.inputType = inputType
        setTextColor(getColor(R.color.textPrimary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setPadding(dpi(14f), dpi(14f), dpi(14f), dpi(14f))
        background = GradientDrawable().apply {
            cornerRadius = dpi(12f).toFloat()
            setColor(getColor(R.color.white))
            setStroke(dpi(1f), getColor(R.color.border))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(6f) }
    }

    private fun fieldLabel(t: String) =
        text(t, 13f, R.color.textSecondary, bold = true, topMargin = dpi(16f))

    private fun linkText(t: String, onClick: () -> Unit): TextView =
        text(t, 14f, R.color.emeraldDark, bold = true, topMargin = dpi(16f), gravityCenter = true).apply {
            isClickable = true; setOnClickListener { onClick() }
        }

    private fun text(
        t: String, size: Float, colorRes: Int, bold: Boolean = false,
        topMargin: Int = 0, lineMultiplier: Float = 1.15f, gravityCenter: Boolean = false
    ): TextView = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(getColor(colorRes))
        if (bold) setTypeface(null, Typeface.BOLD)
        setLineSpacing(0f, lineMultiplier)
        if (gravityCenter) gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = topMargin }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_SIGN_IN = "sign_in"
        const val MODE_SIGN_UP = "sign_up"
    }
}
