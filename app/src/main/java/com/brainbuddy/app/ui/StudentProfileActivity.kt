package com.brainbuddy.app.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.LockScreenActivity
import com.brainbuddy.app.R
import com.brainbuddy.app.avatar.AvatarCatalog
import com.brainbuddy.app.avatar.AvatarCategory
import com.brainbuddy.app.avatar.AvatarItem
import com.brainbuddy.app.avatar.AvatarRarity
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.core.StudentProfileStore
import com.brainbuddy.app.core.UserProfileProvider
import com.google.android.material.card.MaterialCardView

/**
 * Student profile: displayName, avatar selection, level/XP/streak.
 * No parent content - student only.
 */
class StudentProfileActivity : AppCompatActivity() {

    private lateinit var store: StudentProfileStore
    private lateinit var avatarPreview: ImageView
    private lateinit var tvAvatarName: TextView
    private var currentSelectedId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_student_profile)

        store = StudentProfileStore(this)
        // All read-only profile stats (xp, level, streak, displayName, avatar) come from
        // the unified aggregate; store is kept only for write operations.
        val userProfile = UserProfileProvider.get(this)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        val etName = findViewById<android.widget.EditText>(R.id.etDisplayName)
        etName.setText(userProfile.displayName)
        etName.hint = getString(R.string.student_profile_name_hint)

        findViewById<android.widget.Button>(R.id.btnSaveName).setOnClickListener {
            store.setDisplayName(etName.text.toString().trim())
            android.widget.Toast.makeText(this, "Kaydedildi", android.widget.Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.tvLevel).text = "Seviye ${userProfile.level}"
        findViewById<TextView>(R.id.tvXp).text = "${userProfile.xp} XP"
        findViewById<TextView>(R.id.tvStreak).text = "\uD83D\uDD25 ${userProfile.streakDays} gün"

        val ownedIds = store.getOwnedAvatarIds()
        currentSelectedId = store.getSelectedAvatarId()
        val mascots = AvatarCatalog.items().filter { it.category == AvatarCategory.MASCOT }

        avatarPreview = findViewById(R.id.avatarPreview)
        tvAvatarName = findViewById(R.id.tvAvatarName)

        updatePreview(mascots.find { it.id == currentSelectedId })

        // Show avatar count
        val ownedCount = mascots.count { it.id in ownedIds }
        findViewById<TextView>(R.id.tvAvatarCount).text = "$ownedCount/${mascots.size}"

        val recycler = findViewById<RecyclerView>(R.id.recyclerAvatars)
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.itemAnimator = null

        val adapter = AvatarGridAdapter(mascots, ownedIds, currentSelectedId, userProfile.xp, userProfile.level) { avatarItem ->
            if (avatarItem.id in ownedIds && avatarItem.id != currentSelectedId) {
                currentSelectedId = avatarItem.id
                store.setSelectedAvatarId(avatarItem.id)
                animatePreviewChange(avatarItem)
                recycler.adapter?.notifyDataSetChanged()
            }
        }
        recycler.adapter = adapter

        // Next-unlock progress banner
        val tvNextUnlock = findViewById<TextView>(R.id.tvNextUnlock)
        val nextLocked = mascots
            .filter { it.id !in ownedIds && it.id != "mascot_30d" }
            .minByOrNull { it.priceXp + it.requiredLevel * 100 }
        if (tvNextUnlock != null) {
            if (nextLocked != null) {
                val xpNeeded = (nextLocked.priceXp - userProfile.xp).coerceAtLeast(0)
                val levelNeeded = (nextLocked.requiredLevel - userProfile.level).coerceAtLeast(0)
                val progressText = when {
                    levelNeeded > 0 -> "Sonraki avatar için $levelNeeded seviye daha gerekiyor."
                    xpNeeded > 0 -> "Sonraki avatar için $xpNeeded XP daha gerekiyor."
                    else -> "Mağazadan yeni avatarların kilidini açabilirsin."
                }
                tvNextUnlock.text = progressText
                tvNextUnlock.visibility = View.VISIBLE
            } else {
                tvNextUnlock.visibility = View.GONE
            }
        }
    }

    private fun updatePreview(item: AvatarItem?) {
        if (item != null && item.previewDrawableRes != 0) {
            avatarPreview.setImageResource(item.previewDrawableRes)
            tvAvatarName.text = item.displayName
        } else {
            avatarPreview.setImageResource(R.drawable.avatar_mascot_brainy)
            tvAvatarName.text = "Brainy"
        }
    }

    private fun animatePreviewChange(item: AvatarItem) {
        val scaleDown = ObjectAnimator.ofFloat(avatarPreview, "scaleX", 1f, 0.7f)
        val scaleDownY = ObjectAnimator.ofFloat(avatarPreview, "scaleY", 1f, 0.7f)
        val fadeOut = ObjectAnimator.ofFloat(avatarPreview, "alpha", 1f, 0f)

        val shrink = AnimatorSet().apply {
            playTogether(scaleDown, scaleDownY, fadeOut)
            duration = 120
        }

        shrink.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                updatePreview(item)

                val scaleUp = ObjectAnimator.ofFloat(avatarPreview, "scaleX", 0.7f, 1f)
                val scaleUpY = ObjectAnimator.ofFloat(avatarPreview, "scaleY", 0.7f, 1f)
                val fadeIn = ObjectAnimator.ofFloat(avatarPreview, "alpha", 0f, 1f)

                AnimatorSet().apply {
                    playTogether(scaleUp, scaleUpY, fadeIn)
                    duration = 250
                    interpolator = OvershootInterpolator(1.5f)
                    start()
                }
            }
        })

        shrink.start()
    }
}

class AvatarGridAdapter(
    private val items: List<AvatarItem>,
    private val ownedIds: Set<String>,
    private var selectedId: String,
    private val userXp: Int,
    private val userLevel: Int,
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
        val ctx = holder.view.context
        val card = holder.view.findViewById<MaterialCardView>(R.id.cardAvatar)
        val img = holder.view.findViewById<ImageView>(R.id.imgAvatar)
        val lock = holder.view.findViewById<View>(R.id.lockOverlay)
        val lockIcon = holder.view.findViewById<ImageView>(R.id.lockIcon)
        val tvName = holder.view.findViewById<TextView>(R.id.tvAvatarName)
        val rarityDot = holder.view.findViewById<View>(R.id.rarityDot)

        if (item.previewDrawableRes != 0) {
            img.setImageResource(item.previewDrawableRes)
        }

        tvName.text = item.displayName

        val owned = item.id in ownedIds
        val selected = item.id == selectedId

        // Lock state
        lock.visibility = if (owned) View.GONE else View.VISIBLE
        lockIcon.visibility = if (owned) View.GONE else View.VISIBLE
        img.alpha = if (owned) 1f else 0.4f

        // Unlock label
        val tvUnlockLabel = holder.view.findViewById<TextView>(R.id.tvUnlockLabel)
        if (!owned) {
            tvUnlockLabel.text = buildUnlockLabel(item)
            tvUnlockLabel.visibility = View.VISIBLE
        } else {
            tvUnlockLabel.visibility = View.GONE
        }

        // Selection state
        if (selected) {
            card.strokeWidth = ctx.resources.getDimensionPixelSize(R.dimen.avatar_stroke_selected)
            card.strokeColor = ContextCompat.getColor(ctx, R.color.bb_primary)
            card.cardElevation = 6f * ctx.resources.displayMetrics.density
            card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.avatar_selected_bg))
            tvName.setTextColor(ContextCompat.getColor(ctx, R.color.bb_primary))
            tvName.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            card.strokeWidth = if (owned) 0 else ctx.resources.getDimensionPixelSize(R.dimen.avatar_stroke_normal)
            card.strokeColor = ContextCompat.getColor(ctx, R.color.divider_light)
            card.cardElevation = 2f * ctx.resources.displayMetrics.density
            card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.bb_card))
            tvName.setTextColor(ContextCompat.getColor(ctx,
                if (owned) R.color.bb_text else R.color.bb_text_muted))
            tvName.setTypeface(null, android.graphics.Typeface.NORMAL)
        }

        // Rarity dot
        if (item.rarity != AvatarRarity.COMMON) {
            rarityDot.visibility = View.VISIBLE
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(ctx, when (item.rarity) {
                    AvatarRarity.RARE -> R.color.rarity_rare
                    AvatarRarity.EPIC -> R.color.rarity_epic
                    AvatarRarity.LEGENDARY -> R.color.rarity_legendary
                    else -> R.color.rarity_common
                }))
            }
            rarityDot.background = dotBg
        } else {
            rarityDot.visibility = View.GONE
        }

        // Click handling — always enabled; locked items show info message
        card.isClickable = true
        card.isFocusable = true

        if (owned) {
            card.setOnClickListener { v ->
                val bounceX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.92f, 1.05f, 1f)
                val bounceY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.92f, 1.05f, 1f)
                AnimatorSet().apply {
                    playTogether(bounceX, bounceY)
                    duration = 200
                    start()
                }
                selectedId = item.id
                onSelect(item)
            }
        } else {
            card.setOnClickListener {
                android.widget.Toast.makeText(
                    holder.view.context,
                    buildLockedMessage(item),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun getItemCount() = items.size

    private fun buildUnlockLabel(item: AvatarItem): String = when {
        item.id == "mascot_30d" -> "30 gün seri ile açılır"
        item.requiredLevel > 1 && item.priceXp > 0 ->
            "Seviye ${item.requiredLevel} ve ${item.priceXp} XP ile açılır"
        item.requiredLevel > 1 -> "Seviye ${item.requiredLevel}'de açılır"
        item.priceXp > 0 -> "${item.priceXp} XP'de açılır"
        else -> "Ücretsiz"
    }

    private fun buildLockedMessage(item: AvatarItem): String = when {
        item.id == "mascot_30d" ->
            "Bu avatar 30 günlük seri tamamlandığında açılır."
        item.requiredLevel > userLevel ->
            "Bu avatar Seviye ${item.requiredLevel}'de açılır. Şu an Seviye $userLevel'sin."
        item.priceXp > userXp ->
            "Bu avatar ${item.priceXp} XP'de açılır. Şu an ${userXp} XP'n var."
        else ->
            "Bu avatar için gereken seviye veya XP'ye ulaştın. Avatar Mağazası'ndan kuşanabilirsin."
    }
}
