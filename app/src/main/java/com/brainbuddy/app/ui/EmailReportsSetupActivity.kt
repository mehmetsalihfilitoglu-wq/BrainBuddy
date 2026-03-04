package com.brainbuddy.app.ui

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.core.EmailReportPrefs
import com.brainbuddy.app.core.ParentAccessGuard
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * E-posta raporları kurulum ekranı.
 * Aç/kapat, alıcı e-posta(lar), gönderim sıklığı (haftalık / aylık), saat seçimi.
 * Gerçek mail gönderimi henüz yok; UI + validasyon + DataStore kaydı hazır.
 */
class EmailReportsSetupActivity : AppCompatActivity() {

    private lateinit var prefs: EmailReportPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ParentAccessGuard.checkAndRedirect(this)) return

        setContentView(R.layout.activity_email_reports_setup)
        prefs = EmailReportPrefs(this)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.email_reports_setup_title)

        val switchWeekly = findViewById<android.widget.Switch>(R.id.switchWeeklyReport)
        val emailInput = findViewById<TextInputEditText>(R.id.emailInput)
        val emailLayout = findViewById<TextInputLayout>(R.id.emailLayout)
        val spinnerFrequency = findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.spinnerFrequency)
        val spinnerHour = findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.spinnerHour)

        switchWeekly.isChecked = prefs.isWeeklyReportEnabled()
        emailInput.setText(prefs.reportEmail())

        val frequencyOptions = arrayOf(
            getString(R.string.email_freq_weekly),
            getString(R.string.email_freq_monthly)
        )
        spinnerFrequency.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, frequencyOptions))
        spinnerFrequency.setText(prefs.reportFrequencyLabel(), false)

        val hours = (0..23).map { "%02d:00".format(it) }
        spinnerHour.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, hours))
        spinnerHour.setText(prefs.reportHourFormatted(), false)

        switchWeekly.setOnCheckedChangeListener { _, isChecked -> prefs.setWeeklyReportEnabled(isChecked) }

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar).setNavigationOnClickListener { onSupportNavigateUp() }

        val switchAttachPdf = findViewById<android.widget.Switch>(R.id.switchAttachPdf)
        switchAttachPdf.isChecked = prefs.isAttachPdfEnabled()
        switchAttachPdf.setOnCheckedChangeListener { _, isChecked ->
            prefs.setAttachPdfEnabled(isChecked)
        }

        findViewById<View>(R.id.btnTestMail).setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            if (email.isBlank()) {
                Toast.makeText(this, R.string.email_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(this, R.string.email_invalid, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(this, R.string.email_reports_test_mail_sent, Toast.LENGTH_LONG).show()
        }

        findViewById<View>(R.id.btnSave).setOnClickListener {
            val email = emailInput.text?.toString()?.trim().orEmpty()
            if (prefs.isDailyReportEnabled() || prefs.isWeeklyReportEnabled()) {
                if (email.isBlank()) {
                    emailLayout.error = getString(R.string.email_required)
                    return@setOnClickListener
                }
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailLayout.error = getString(R.string.email_invalid)
                    return@setOnClickListener
                }
            }
            emailLayout.error = null
            prefs.setReportEmail(email)
            prefs.setReportFrequencyFromLabel(spinnerFrequency.text.toString())
            prefs.setReportHourFromFormatted(spinnerHour.text.toString())
            com.brainbuddy.app.report.ReportScheduler.schedule(this)
            Toast.makeText(this, R.string.email_reports_saved, Toast.LENGTH_SHORT).show()
            finish()
        }

    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
