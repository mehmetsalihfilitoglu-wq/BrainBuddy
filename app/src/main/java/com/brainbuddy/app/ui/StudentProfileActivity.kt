package com.brainbuddy.app.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.LockScreenActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.avatar.AvatarCatalog
import com.brainbuddy.app.avatar.AvatarCategory
import com.brainbuddy.app.avatar.AvatarItem
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.StudentProfileStore
import com.google.android.material.card.MaterialCardView

/**
 * Student profile: displayName, avatar selection, level/XP/streak.
 * No parent content - student only.
 */
class StudentProfileActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_student_profile)

        val store = StudentProfileStore(this)
        val gam = GamificationStore(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val etName = findViewById<android.widget.EditText>(R.id.etDisplayName)
        etName.setText(store.getDisplayName())
        etName.hint = getString(R.string.student_profile_name_hint)

        findViewById<android.widget.Button>(R.id.btnSaveName).setOnClickListener {
            store.setDisplayName(etName.text.toString().trim())
            android.widget.Toast.makeText(this, "Kaydedildi", android.widget.Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tvLevel).text = "Seviye ${gam.level()}"
        findViewById<TextView>(R.id.tvXp).text = "${gam.xp()} XP"
        findViewById<TextView>(R.id.tvStreak).text = "🔥 ${gam.streakDays()} gün"

        val ownedIds = store.getOwnedAvatarIds()
        val selectedId = store.getSelectedAvatarId()
        val mascots = AvatarCatalog.items().filter { it.category == AvatarCategory.MASCOT }

        val avatarPreview = findViewById<ImageView>(R.id.avatarPreview)
        val item = AvatarCatalog.items().find { it.id == selectedId }
        if (item != null && item.previewDrawableRes != 0) {
            avatarPreview.setImageResource(item.previewDrawableRes)
        } else {
            avatarPreview.setImageResource(R.drawable.avatar_mascot_brainy)
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerAvatars)
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.adapter = AvatarGridAdapter(mascots, ownedIds, selectedId) { avatarItem ->
            if (avatarItem.id in ownedIds) {
                store.setSelectedAvatarId(avatarItem.id)
                if (avatarItem.previewDrawableRes != 0) {
                    avatarPreview.setImageResource(avatarItem.previewDrawableRes)
                }
                recycler.adapter?.notifyDataSetChanged()
            }
        }
    }
}

class AvatarGridAdapter(
    private val items: List<AvatarItem>,
    private val ownedIds: Set<String>,
    private val selectedId: String,
    private val onSelect: (AvatarItem) -> Unit
) : RecyclerView.Adapter<AvatarGridAdapter.VH>() {

    class VH(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_student_avatar, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val card = holder.view.findViewById<MaterialCardView>(R.id.cardAvatar)
        val img = holder.view.findViewById<ImageView>(R.id.imgAvatar)
        val lock = holder.view.findViewById<View>(R.id.lockOverlay)

        if (item.previewDrawableRes != 0) {
            img.setImageResource(item.previewDrawableRes)
        }
        val owned = item.id in ownedIds
        lock.visibility = if (owned) View.GONE else View.VISIBLE
        val selected = item.id == selectedId
        card.strokeWidth = if (selected) 4 else 0
        card.strokeColor = holder.view.context.getColor(R.color.bb_primary)

        card.isClickable = owned
        card.setOnClickListener { if (owned) onSelect(item) }
    }

    override fun getItemCount() = items.size
}
