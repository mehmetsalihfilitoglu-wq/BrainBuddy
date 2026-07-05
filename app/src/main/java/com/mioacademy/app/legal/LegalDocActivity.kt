package com.mioacademy.app.legal

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.R

/**
 * Generic legal document viewer. Loads an HTML asset in a WebView.
 * Reusable for all legal screens (privacy, terms, data usage, ads, parent info).
 */
class LegalDocActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_legal_doc)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val title = intent.getStringExtra(EXTRA_TITLE) ?: getString(R.string.legal_privacy_policy_title)
        val assetUrl = intent.getStringExtra(EXTRA_ASSET_URL) ?: LegalConfig.ASSET_PRIVACY_POLICY
        val webUrl = intent.getStringExtra(EXTRA_WEB_URL)

        supportActionBar?.title = title

        val web = findViewById<WebView>(R.id.webView)
        web.settings.javaScriptEnabled = false
        web.webViewClient = WebViewClient()
        web.loadUrl(assetUrl)

        val btnViewOnWeb = findViewById<Button>(R.id.btnViewOnWeb)
        if (!webUrl.isNullOrBlank()) {
            btnViewOnWeb.visibility = View.VISIBLE
            btnViewOnWeb.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
            }
        } else {
            btnViewOnWeb.visibility = View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_TITLE = "legal_title"
        const val EXTRA_ASSET_URL = "legal_asset_url"
        const val EXTRA_WEB_URL = "legal_web_url"

        fun newIntent(context: Context, title: String, assetUrl: String, webUrl: String? = null): Intent {
            return Intent(context, LegalDocActivity::class.java).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ASSET_URL, assetUrl)
                if (!webUrl.isNullOrBlank()) putExtra(EXTRA_WEB_URL, webUrl)
            }
        }
    }
}
