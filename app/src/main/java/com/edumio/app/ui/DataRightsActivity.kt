package com.edumio.app.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.edumio.app.R
import com.edumio.app.analytics.AnalyticsEvents
import com.edumio.app.analytics.AnalyticsProvider
import com.edumio.app.privacy.DataRightsProvider
import com.edumio.app.privacy.ExportResult
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Privacy & My Data — informational screen with one real control: export all local data as a
 * shareable JSON file. v1 has no account, no cloud sync and no server-side user data, so the app
 * ships no in-app deletion action; uninstalling removes the on-device data. Backed by
 * [DataRightsProvider]; no fake backend behaviour.
 */
class DataRightsActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density
    private fun dpi(v: Float) = (v * dp + 0.5f).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.white))
            isFillViewport = true
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(24f), dpi(20f), dpi(24f))
        }
        scroll.addView(col)
        setContentView(scroll)

        col.addView(title(getString(R.string.data_rights_title)))
        col.addView(body(getString(R.string.data_rights_intro), topMargin = dpi(8f)))

        col.addView(actionCard(
            getString(R.string.data_rights_export),
            getString(R.string.data_rights_export_sub),
        ) { exportData() }, cardParams())

        col.addView(actionCard(
            getString(R.string.data_rights_privacy),
            getString(R.string.data_rights_privacy_sub),
        ) { startActivity(Intent(this, com.edumio.app.legal.LegalHubActivity::class.java)) }, cardParams())

        col.addView(body(getString(R.string.data_rights_cloud_note), topMargin = dpi(20f)))
    }

    private fun exportData() {
        Thread {
            val result = runBlocking { DataRightsProvider.service(this@DataRightsActivity).exportData() }
            runOnUiThread {
                when (result) {
                    is ExportResult.Success -> shareExport(result.filePath)
                    is ExportResult.Error ->
                        Toast.makeText(this, getString(R.string.data_rights_export_error), Toast.LENGTH_LONG).show()
                }
            }
        }.start()
        AnalyticsProvider.track(AnalyticsEvents.GDPR_EXPORT_REQUESTED)
    }

    private fun shareExport(filePath: String) {
        try {
            // FileProvider only exposes cacheDir; copy the export there to share it.
            val shareFile = File(cacheDir, "edumio_original_export.json")
            File(filePath).copyTo(shareFile, overwrite = true)
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", shareFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "EDUmio — Verilerim")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.data_rights_export)))
        } catch (_: Exception) {
            Toast.makeText(this, getString(R.string.data_rights_export_error), Toast.LENGTH_LONG).show()
        }
    }

    // ── small view builders ──
    private fun cardParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    ).also { it.topMargin = dpi(12f) }

    private fun actionCard(
        titleText: String, subText: String, onClick: () -> Unit
    ): com.google.android.material.card.MaterialCardView {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        }
        inner.addView(text(titleText, 16f, R.color.textPrimary, bold = true))
        inner.addView(text(subText, 13f, R.color.textSecondary, topMargin = dpi(2f), lineMultiplier = 1.4f))
        return com.google.android.material.card.MaterialCardView(this).apply {
            radius = 16 * dp
            cardElevation = 0f
            strokeWidth = dpi(1f)
            setStrokeColor(getColor(R.color.border))
            setCardBackgroundColor(getColor(R.color.white))
            isClickable = true
            isFocusable = true
            addView(inner)
            setOnClickListener { onClick() }
        }
    }

    private fun title(t: String) = text(t, 22f, R.color.textPrimary, bold = true)
    private fun body(t: String, topMargin: Int = 0) =
        text(t, 14f, R.color.textSecondary, topMargin = topMargin, lineMultiplier = 1.5f)

    private fun text(
        t: String, size: Float, colorRes: Int, bold: Boolean = false,
        topMargin: Int = 0, lineMultiplier: Float = 1.15f
    ): TextView = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(getColor(colorRes))
        if (bold) setTypeface(null, Typeface.BOLD)
        setLineSpacing(0f, lineMultiplier)
        gravity = Gravity.START
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = topMargin }
    }
}
