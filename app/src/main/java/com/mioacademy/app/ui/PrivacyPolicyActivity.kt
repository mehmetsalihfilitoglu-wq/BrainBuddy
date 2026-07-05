package com.mioacademy.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.mioacademy.app.BuildConfig
import com.mioacademy.app.R

class PrivacyPolicyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.privacy_policy_title)

        val web = findViewById<WebView>(R.id.webView)
        web.settings.javaScriptEnabled = false
        web.webViewClient = WebViewClient()
        web.loadUrl("file:///android_asset/privacy_policy_tr.html")

        val webUrl = BuildConfig.PRIVACY_POLICY_URL
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
}
