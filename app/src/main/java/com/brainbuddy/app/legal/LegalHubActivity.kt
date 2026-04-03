package com.brainbuddy.app.legal

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.databinding.ActivityLegalHubBinding
import com.brainbuddy.app.databinding.ItemLegalDocBinding

/**
 * Lists all legal documents in a clean, scrollable screen.
 * Accessible from Parent Hub → Gizlilik ve Güvenlik.
 */
class LegalHubActivity : AppCompatActivity() {

    private lateinit var b: ActivityLegalHubBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLegalHubBinding.inflate(layoutInflater)
        setContentView(b.root)

        setSupportActionBar(b.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.legal_hub_title)

        val docs = LegalConfig.allDocuments()
        b.recyclerLegalDocs.layoutManager = LinearLayoutManager(this)
        b.recyclerLegalDocs.adapter = LegalDocAdapter(docs) { doc ->
            // Pass web URL for privacy and terms so "View on web" button works
            val webUrl = when (doc.assetUrl) {
                LegalConfig.ASSET_PRIVACY_POLICY -> LegalConfig.WEB_PRIVACY_POLICY
                LegalConfig.ASSET_TERMS_OF_USE -> LegalConfig.WEB_TERMS_OF_USE
                else -> null
            }
            startActivity(
                LegalDocActivity.newIntent(
                    this,
                    getString(doc.titleRes),
                    doc.assetUrl,
                    webUrl
                )
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}

private class LegalDocAdapter(
    private val items: List<LegalConfig.LegalDoc>,
    private val onClick: (LegalConfig.LegalDoc) -> Unit
) : RecyclerView.Adapter<LegalDocAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemLegalDocBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size

    inner class VH(private val b: ItemLegalDocBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(doc: LegalConfig.LegalDoc) {
            b.legalDocTitle.setText(doc.titleRes)
            b.legalDocSubtitle.setText(doc.subtitleRes)
            b.legalDocIcon.setImageResource(doc.iconRes)
            b.legalDocIcon.imageTintList =
                ContextCompat.getColorStateList(b.root.context, R.color.emerald_primary)
            b.root.setOnClickListener { onClick(doc) }
        }
    }
}
