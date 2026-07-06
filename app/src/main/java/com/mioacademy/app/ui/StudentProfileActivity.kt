package com.mioacademy.app.ui

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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mioacademy.app.R
import com.mioacademy.app.avatar.AvatarCatalog
import com.mioacademy.app.avatar.AvatarCategory
import com.mioacademy.app.avatar.AvatarItem
import com.mioacademy.app.avatar.AvatarRarity
import com.mioacademy.app.avatar.AvatarShopScreen
import com.mioacademy.app.core.BadgeDisplayHelper
import com.mioacademy.app.core.GamificationStore
import com.mioacademy.app.core.StudentProfileStore
import com.mioacademy.app.core.UserGoalPrefs
import com.mioacademy.app.quiz.PremiumPaywallSheet
import com.mioacademy.app.social.LeagueScreen
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class StudentProfileActivity : AppCompatActivity() {

    private lateinit var store: StudentProfileStore
    private lateinit var gam: GamificationStore
    private lateinit var goalPrefs: UserGoalPrefs
    private lateinit var avatarPreview: ImageView
    private lateinit var tvAvatarName: TextView
    private lateinit var tvDisplayName: TextView
    private var currentSelectedId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_profile)

        store = StudentProfileStore(this)
        gam = GamificationStore(this)
        goalPrefs = UserGoalPrefs(this)

        avatarPreview = findViewById(R.id.avatarPreview)
        tvAvatarName = findViewById(R.id.tvAvatarName)
        tvDisplayName = findViewById(R.id.tvDisplayName)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        val content = findViewById<android.view.View>(R.id.scrollContent)
        val origBottom = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
            val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, origBottom + navBottom)
            insets
        }

        refreshAll()
        setupAvatarGrid()
        setupNavigation()
    }

    override fun onResume() {
        super.onResume()
        refreshIdentityHero()
    }

    private fun refreshAll() {
        refreshIdentityHero()
        refreshNameEdit()
        refreshGoalCard()
        refreshAchievements()
    }

    private fun refreshIdentityHero() {
        val displayName = store.getDisplayName().ifBlank { goalPrefs.getStudentName() }
        tvDisplayName.text = displayName.ifBlank { "—" }

        val freezeSuffix = if (gam.freezeTokens() > 0) " 🧊" else ""
        findViewById<TextView>(R.id.tvStreak).text = "${gam.streakDays()}$freezeSuffix"
        findViewById<TextView>(R.id.tvXp).text = "${gam.xp()}"
        findViewById<TextView>(R.id.tvLevel).text = "${gam.level()}"
    }

    private fun refreshNameEdit() {
        val etName = findViewById<android.widget.EditText>(R.id.etDisplayName)
        etName.setText(store.getDisplayName())
        etName.hint = getString(R.string.student_profile_name_hint)

        findViewById<View>(R.id.btnSaveName).setOnClickListener {
            val name = etName.text.toString().trim()
            store.setDisplayName(name)
            tvDisplayName.text = name.ifBlank { "—" }
            android.widget.Toast.makeText(this, getString(R.string.profile_name_saved), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshGoalCard() {
        val goal = goalPrefs.getGoal()
        val career = goal.careerPath
        val italianLevel = goal.italianLevel

        // Career identity
        findViewById<TextView>(R.id.tvCareerGoal).text = "${career.emoji} ${journeyTitleFor(career)}"
        findViewById<TextView>(R.id.tvGoalDegree).text = career.italianDegreeName

        // Destination cities
        val citiesView = findViewById<TextView>(R.id.tvGoalCities)
        if (goal.destinationCities.isNotEmpty()) {
            citiesView.text = "📍 " + goal.destinationCities.joinToString(", ")
            citiesView.visibility = View.VISIBLE
        } else {
            citiesView.visibility = View.GONE
        }

        // Meta chips: exam type + Italian level
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupGoalMeta)
        chipGroup.removeAllViews()
        chipGroup.addView(makeReadOnlyChip("${career.examType.code}"))
        chipGroup.addView(makeReadOnlyChip("${italianLevel.emoji} ${italianLevel.ceferLevel}"))
    }

    private fun refreshAchievements() {
        val allBadges = (gam.badges() + gam.milestoneBadges()).distinct()
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupBadges)
        val tvEmpty = findViewById<TextView>(R.id.tvBadgesEmpty)

        chipGroup.removeAllViews()
        if (allBadges.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            allBadges.forEach { badgeId ->
                val emoji = BadgeDisplayHelper.getBadgeEmoji(badgeId)
                val name = BadgeDisplayHelper.getDisplayName(badgeId)
                chipGroup.addView(makeReadOnlyChip("$emoji $name"))
            }
        }
    }

    private fun makeReadOnlyChip(text: String): Chip {
        return Chip(this, null, R.style.Widget_BrainBuddy_Chip_Stat).apply {
            this.text = text
            isClickable = false
            isCheckable = false
        }
    }

    private fun setupAvatarGrid() {
        val ownedIds = store.getOwnedAvatarIds()
        currentSelectedId = store.getSelectedAvatarId()
        val mascots = AvatarCatalog.items().filter { it.category == AvatarCategory.MASCOT }

        updatePreview(mascots.find { it.id == currentSelectedId })

        val ownedCount = mascots.count { it.id in ownedIds }
        findViewById<TextView>(R.id.tvAvatarCount).text = "$ownedCount/${mascots.size}"

        val recycler = findViewById<RecyclerView>(R.id.recyclerAvatars)
        recycler.layoutManager = GridLayoutManager(this, 3)
        recycler.itemAnimator = null

        val adapter = AvatarGridAdapter(mascots, ownedIds, currentSelectedId) { avatarItem ->
            if (avatarItem.id in ownedIds && avatarItem.id != currentSelectedId) {
                currentSelectedId = avatarItem.id
                store.setSelectedAvatarId(avatarItem.id)
                animatePreviewChange(avatarItem)
                recycler.adapter?.notifyDataSetChanged()
            }
        }
        recycler.adapter = adapter
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.btnAvatarShop).setOnClickListener {
            startActivity(Intent(this, AvatarShopScreen::class.java))
        }
        findViewById<View>(R.id.tvGoalEdit).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardLeague).setOnClickListener {
            startActivity(Intent(this, LeagueScreen::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardPremium).setOnClickListener {
            PremiumPaywallSheet().show(supportFragmentManager, PremiumPaywallSheet.TAG)
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

    private fun journeyTitleFor(career: com.mioacademy.app.core.CareerPath): String = when (career) {
        com.mioacademy.app.core.CareerPath.MEDICINE -> "Tıp Yolculuğu"
        com.mioacademy.app.core.CareerPath.DENTISTRY -> "Diş Hekimliği Yolculuğu"
        com.mioacademy.app.core.CareerPath.ENGINEERING -> "Mühendislik Yolculuğu"
        com.mioacademy.app.core.CareerPath.COMPUTER_SCIENCE -> "Bilgisayar Bilimi Yolculuğu"
        com.mioacademy.app.core.CareerPath.ARCHITECTURE -> "Mimarlık Yolculuğu"
        com.mioacademy.app.core.CareerPath.ECONOMICS -> "Ekonomi Yolculuğu"
        com.mioacademy.app.core.CareerPath.LAW -> "Hukuk Yolculuğu"
        com.mioacademy.app.core.CareerPath.PHARMACY -> "Eczacılık Yolculuğu"
        com.mioacademy.app.core.CareerPath.BIOLOGY -> "Biyoloji Yolculuğu"
        com.mioacademy.app.core.CareerPath.PSYCHOLOGY -> "Psikoloji Yolculuğu"
        com.mioacademy.app.core.CareerPath.VETERINARY -> "Veteriner Yolculuğu"
        com.mioacademy.app.core.CareerPath.MATHEMATICS -> "Matematik Yolculuğu"
        com.mioacademy.app.core.CareerPath.DESIGN -> "Tasarım Yolculuğu"
        com.mioacademy.app.core.CareerPath.OTHER -> "İtalya Yolculuğu"
    }
}

class AvatarGridAdapter(
    private val items: List<AvatarItem>,
    private val ownedIds: Set<String>,
    private var selectedId: String,
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

        // Click with press animation
        card.isClickable = owned
        card.isFocusable = owned

        if (owned) {
            card.setOnClickListener { v ->
                // Press bounce animation
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
            card.setOnClickListener(null)
        }
    }

    override fun getItemCount() = items.size
}
