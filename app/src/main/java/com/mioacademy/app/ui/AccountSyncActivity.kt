package com.mioacademy.app.ui

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.mioacademy.app.R
import com.mioacademy.app.analytics.AnalyticsEvents
import com.mioacademy.app.analytics.AnalyticsProvider
import com.mioacademy.app.auth.AuthProvider
import com.mioacademy.app.auth.AuthProviderType
import com.mioacademy.app.sync.SyncManager
import com.mioacademy.app.sync.SyncResult
import com.mioacademy.app.sync.SyncStateStore
import kotlinx.coroutines.runBlocking

/**
 * "Hesap ve Senkronizasyon" — the account hub. Reflects the current session, lets the
 * user create/sign in/out, run a manual sync (real on-device sync today), and reach the
 * existing data-rights + legal screens. Honest copy about cloud sync being prepared;
 * nothing here fakes cloud behaviour.
 */
class AccountSyncActivity : AppCompatActivity() {

    private val dp get() = resources.displayMetrics.density
    private fun dpi(v: Float) = (v * dp + 0.5f).toInt()

    private lateinit var container: LinearLayout
    private var syncing = false
    private var lastSyncFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.white)); isFillViewport = true
        }
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(20f), dpi(20f), dpi(20f), dpi(28f))
        }
        scroll.addView(container); setContentView(scroll)
        AnalyticsProvider.track(AnalyticsEvents.ACCOUNT_SCREEN_VIEWED)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        container.removeAllViews()
        container.addView(text("‹", 26f, R.color.textSecondary, bold = true).apply {
            setPadding(0, 0, dpi(12f), dpi(8f)); isClickable = true; setOnClickListener { finish() }
        })
        container.addView(text(getString(R.string.account_title), 24f, R.color.textPrimary, bold = true))

        if (AuthProvider.isSignedIn(this)) renderSignedIn() else renderSignedOut()
    }

    // ── Signed out ──────────────────────────────────────────────────────────────
    private fun renderSignedOut() {
        container.addView(text(getString(R.string.account_why_title), 15f, R.color.textPrimary,
            bold = true, topMargin = dpi(20f)))
        listOf(R.string.account_why_1, R.string.account_why_2, R.string.account_why_3).forEach {
            container.addView(bullet(getString(it)))
        }

        container.addView(filledButton(getString(R.string.account_create)) {
            startActivity(Intent(this, AuthActivity::class.java)
                .putExtra(AuthActivity.EXTRA_MODE, AuthActivity.MODE_SIGN_UP))
        })
        container.addView(secondaryCard(getString(R.string.account_signin), getString(R.string.account_signin_sub)) {
            startActivity(Intent(this, AuthActivity::class.java)
                .putExtra(AuthActivity.EXTRA_MODE, AuthActivity.MODE_SIGN_IN))
        })

        container.addView(infoCard(getString(R.string.account_local_only_note)))
    }

    // ── Signed in ───────────────────────────────────────────────────────────────
    private fun renderSignedIn() {
        val user = AuthProvider.currentUser(this) ?: return renderSignedOut()

        // Account card
        val accCol = paddedCol()
        accCol.addView(text(getString(R.string.account_signed_in_as), 12f, R.color.textSecondary))
        accCol.addView(text(user.email ?: providerLabel(user.provider), 17f, R.color.textPrimary,
            bold = true, topMargin = dpi(2f)))
        val verified = user.isEmailVerified
        accCol.addView(text(
            getString(if (verified) R.string.account_verified else R.string.account_verify_pending),
            12f, if (verified) R.color.emeraldDark else R.color.textSecondary,
            topMargin = dpi(8f), lineMultiplier = 1.35f))
        container.addView(cardWrap(accCol))

        // Sync status card
        container.addView(sectionLabel(getString(R.string.account_sync_section)))
        container.addView(syncCard())

        // Data & privacy links (reuse existing screens — no duplicated logic)
        container.addView(sectionLabel(getString(R.string.account_data_section)))
        container.addView(rowCard(getString(R.string.data_rights_export), null) {
            startActivity(Intent(this, DataRightsActivity::class.java))
        })
        container.addView(rowCard(getString(R.string.data_rights_delete), null) {
            startActivity(Intent(this, DataRightsActivity::class.java))
        })
        container.addView(rowCard(getString(R.string.data_rights_privacy), null) {
            startActivity(Intent(this, com.mioacademy.app.legal.LegalHubActivity::class.java))
        })

        // Sign out
        container.addView(rowCard(getString(R.string.account_signout), null, danger = true) { signOut() })
    }

    private fun syncCard(): MaterialCardView {
        val col = paddedCol()
        val user = AuthProvider.currentUser(this)
        val lastSync = user?.let { SyncStateStore(this).pullCursor(it.userId) } ?: 0L

        val (stateLabel, stateColor) = when {
            syncing -> getString(R.string.account_sync_running) to R.color.emeraldDark
            lastSyncFailed -> getString(R.string.account_sync_failed) to R.color.warning_text
            lastSync > 0L -> getString(R.string.account_sync_last,
                DateUtils.getRelativeTimeSpanString(lastSync, System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS).toString()) to R.color.textPrimary
            else -> getString(R.string.account_sync_ready) to R.color.textPrimary
        }
        col.addView(text(stateLabel, 14f, stateColor, bold = true, lineMultiplier = 1.35f))
        col.addView(text(getString(R.string.account_sync_cloud_note), 12f, R.color.textSecondary,
            topMargin = dpi(6f), lineMultiplier = 1.4f))

        col.addView(MaterialButton(this).apply {
            text = getString(if (syncing) R.string.account_sync_running_btn else R.string.account_sync_now)
            isEnabled = !syncing
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpi(48f)
            ).also { it.topMargin = dpi(12f) }
            cornerRadius = dpi(12f)
            setOnClickListener { manualSync() }
        })
        return cardWrap(col)
    }

    private fun manualSync() {
        if (syncing) return
        syncing = true; lastSyncFailed = false
        render()
        AnalyticsProvider.track(AnalyticsEvents.MANUAL_SYNC_STARTED)
        Thread {
            val result = runBlocking { SyncManager.syncNow(this@AccountSyncActivity, "manual") }
            runOnUiThread {
                syncing = false
                when (result) {
                    is SyncResult.Error -> {
                        lastSyncFailed = true
                        AnalyticsProvider.track(AnalyticsEvents.MANUAL_SYNC_FAILED)
                    }
                    else -> {
                        lastSyncFailed = false
                        AnalyticsProvider.track(AnalyticsEvents.MANUAL_SYNC_COMPLETED)
                        Toast.makeText(this, getString(R.string.account_sync_done), Toast.LENGTH_LONG).show()
                    }
                }
                render()
            }
        }.start()
    }

    private fun signOut() {
        AuthProvider.repository(this).signOut()
        AnalyticsProvider.track(AnalyticsEvents.LOGOUT)
        AnalyticsProvider.tracker().setUserId(null)
        render()
    }

    private fun providerLabel(p: AuthProviderType) = when (p) {
        AuthProviderType.GOOGLE -> "Google"
        AuthProviderType.APPLE -> "Apple"
        else -> getString(R.string.account_provider_email)
    }

    // ── view builders ──
    private fun filledButton(label: String, onClick: () -> Unit) = MaterialButton(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dpi(52f)
        ).also { it.topMargin = dpi(20f) }
        cornerRadius = dpi(14f)
        setOnClickListener { onClick() }
    }

    private fun secondaryCard(title: String, sub: String, onClick: () -> Unit) =
        rowCard(title, sub, danger = false, onClick = onClick)

    private fun rowCard(title: String, sub: String?, danger: Boolean = false, onClick: () -> Unit): MaterialCardView {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(text(title, 16f, if (danger) R.color.warning_text else R.color.textPrimary, bold = true))
        if (sub != null) texts.addView(text(sub, 13f, R.color.textSecondary, topMargin = dpi(2f), lineMultiplier = 1.35f))
        inner.addView(texts)
        inner.addView(text("›", 22f, R.color.textSecondary))
        return cardWrap(inner).apply {
            isClickable = true; isFocusable = true
            setStrokeColor(getColor(if (danger) R.color.warning_text else R.color.border))
            setOnClickListener { onClick() }
        }
    }

    private fun infoCard(t: String): MaterialCardView {
        val tv = text(t, 13f, R.color.textSecondary, lineMultiplier = 1.5f).apply {
            setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        }
        return MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.topMargin = dpi(20f) }
            radius = 16 * dp; cardElevation = 0f; strokeWidth = 0
            setCardBackgroundColor(getColor(R.color.emeraldSoft))
            addView(tv)
        }
    }

    private fun cardWrap(child: View): MaterialCardView = MaterialCardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpi(12f) }
        radius = 16 * dp; cardElevation = 0f; strokeWidth = dpi(1f)
        setStrokeColor(getColor(R.color.border))
        setCardBackgroundColor(getColor(R.color.white))
        addView(child)
    }

    private fun paddedCol() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(16f), dpi(16f), dpi(16f), dpi(16f))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun bullet(t: String): TextView =
        text("•  $t", 14f, R.color.textSecondary, topMargin = dpi(8f), lineMultiplier = 1.45f)

    private fun sectionLabel(t: String) =
        text(t, 11f, R.color.emeraldDark, bold = true, topMargin = dpi(24f), letterSpacing = 0.1f)

    private fun text(
        t: String, size: Float, colorRes: Int, bold: Boolean = false,
        topMargin: Int = 0, lineMultiplier: Float = 1.15f, letterSpacing: Float = 0f
    ): TextView = TextView(this).apply {
        text = t
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(getColor(colorRes))
        if (bold) setTypeface(null, Typeface.BOLD)
        if (letterSpacing > 0) this.letterSpacing = letterSpacing
        setLineSpacing(0f, lineMultiplier)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = topMargin }
    }
}
