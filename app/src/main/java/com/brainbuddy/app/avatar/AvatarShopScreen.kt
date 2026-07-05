package com.brainbuddy.app.avatar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityAvatarShopBinding
import com.brainbuddy.app.databinding.BottomSheetAvatarItemBinding
import com.brainbuddy.app.databinding.ItemAvatarShopCardBinding
import com.google.android.material.bottomsheet.BottomSheetDialog

class AvatarShopScreen : AppCompatActivity() {

    private lateinit var b: ActivityAvatarShopBinding
    private lateinit var store: AvatarStore
    private lateinit var gam: GamificationStore
    private var selectedCategory: AvatarCategory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProtectionPrefs(this).userLocked()) {
            startActivity(Intent(this, com.brainbuddy.app.LockScreenActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        b = ActivityAvatarShopBinding.inflate(layoutInflater)
        setContentView(b.root)

        store = AvatarStore(this)
        gam = GamificationStore(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        b.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        b.tvXpBalance.text = "${gam.xp()} XP"
        b.tvLevelBadge.text = "Lv ${gam.level()}"

        if (store.isShopDisabledByParent()) {
            b.tvShopDisabled.visibility = View.VISIBLE
        } else {
            b.tvShopDisabled.visibility = View.GONE
        }

        selectedCategory = null
        b.tabCategories.removeAllTabs()
        b.tabCategories.addTab(b.tabCategories.newTab().setText("Tümü"))
        AvatarCategory.entries.forEach { cat ->
            b.tabCategories.addTab(b.tabCategories.newTab().setText(cat.tr))
        }
        b.tabCategories.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                selectedCategory = when (tab?.position) {
                    0 -> null
                    else -> tab?.position?.let { p -> AvatarCategory.entries.getOrNull(p - 1) }
                }
                refreshList()
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        b.recyclerItems.layoutManager = GridLayoutManager(this, 2)
        refreshList()
    }

    override fun onResume() {
        super.onResume()
        b.tvXpBalance.text = "${gam.xp()} XP"
        b.tvLevelBadge.text = "Lv ${gam.level()}"
        refreshPreview()
        refreshList()
    }

    private fun refreshPreview() {
        val equipped = store.getEquippedItems()
        val catalog = store.getCatalog()

        val bgId = equipped[AvatarCategory.BACKGROUND] ?: catalog.find { it.category == AvatarCategory.BACKGROUND && it.priceXp == 0 }?.id
        val mascotId = equipped[AvatarCategory.MASCOT] ?: catalog.find { it.category == AvatarCategory.MASCOT && it.priceXp == 0 }?.id
        val hairId = equipped[AvatarCategory.HAIR] ?: catalog.find { it.category == AvatarCategory.HAIR && it.priceXp == 0 }?.id
        val frameId = equipped[AvatarCategory.BADGE_FRAME] ?: catalog.find { it.category == AvatarCategory.BADGE_FRAME && it.priceXp == 0 }?.id
        val accId = equipped[AvatarCategory.ACCESSORY]

        val bg = catalog.find { it.id == bgId }
        val mascot = catalog.find { it.id == mascotId }
        val hair = catalog.find { it.id == hairId }
        val frame = catalog.find { it.id == frameId }
        val acc = catalog.find { it.id == accId }

        bg?.let { if (it.previewDrawableRes != 0) b.previewBg.setBackgroundResource(it.previewDrawableRes) }
        mascot?.let { if (it.previewDrawableRes != 0) { b.previewMascot.setImageResource(it.previewDrawableRes); b.previewMascot.visibility = View.VISIBLE } }
        hair?.let { if (it.previewDrawableRes != 0) { b.previewHair.setImageResource(it.previewDrawableRes); b.previewHair.visibility = View.VISIBLE } else b.previewHair.visibility = View.GONE }
        val frameRes = if (frame != null && frame.previewDrawableRes != 0) frame.previewDrawableRes else R.drawable.avatar_frame_default
        b.previewContainer.setBackgroundResource(frameRes)
        acc?.let { if (it.previewDrawableRes != 0) { b.previewAccessory.setImageResource(it.previewDrawableRes); b.previewAccessory.visibility = View.VISIBLE } else b.previewAccessory.visibility = View.GONE }
            ?: run { b.previewAccessory.visibility = View.GONE }
    }

    private fun refreshList() {
        refreshPreview()
        var items = store.getCatalog()
        selectedCategory?.let { cat -> items = items.filter { it.category == cat } }
        b.recyclerItems.adapter = AvatarItemAdapter(items, store, gam) { item -> onItemClick(item) }
    }

    private fun onItemClick(item: AvatarItem) {
        val bottomSheet = BottomSheetDialog(this)
        val sheetBinding = BottomSheetAvatarItemBinding.inflate(layoutInflater)
        bottomSheet.setContentView(sheetBinding.root)

        sheetBinding.detailItemName.text = item.displayName
        sheetBinding.detailChipRarity.text = item.rarity.tr
        sheetBinding.detailChipLevel.text = "Lv ${item.requiredLevel}"

        if (item.previewDrawableRes != 0) {
            when (item.category) {
                AvatarCategory.BACKGROUND -> { sheetBinding.detailPreviewBg.setBackgroundResource(item.previewDrawableRes) }
                AvatarCategory.MASCOT -> { sheetBinding.detailPreviewMascot.setImageResource(item.previewDrawableRes); sheetBinding.detailPreviewMascot.visibility = View.VISIBLE }
                AvatarCategory.HAIR -> { sheetBinding.detailPreviewHair.setImageResource(item.previewDrawableRes); sheetBinding.detailPreviewHair.visibility = View.VISIBLE }
                AvatarCategory.BADGE_FRAME -> sheetBinding.detailPreviewFrame.setBackgroundResource(item.previewDrawableRes)
                AvatarCategory.ACCESSORY -> { sheetBinding.detailPreviewAccessory.setImageResource(item.previewDrawableRes); sheetBinding.detailPreviewAccessory.visibility = View.VISIBLE }
            }
        }

        val unlocked = store.isUnlocked(item.id)
        val (canBuy, reason, _) = store.canPurchase(item)
        val levelOk = gam.level() >= item.requiredLevel

        sheetBinding.btnDetailEquip.visibility = if (unlocked && levelOk) View.VISIBLE else View.GONE
        sheetBinding.btnDetailBuy.visibility = if (!unlocked && canBuy && !store.isShopDisabledByParent()) View.VISIBLE else View.GONE
        sheetBinding.detailRequirement.visibility = if (!levelOk || (!unlocked && reason != null)) View.VISIBLE else View.GONE
        sheetBinding.detailRequirement.text = when {
            !levelOk -> "Bu öğe Seviye ${item.requiredLevel} gerektirir."
            !unlocked && reason != null -> reason
            else -> ""
        }

        sheetBinding.btnDetailEquip.setOnClickListener {
            store.equipItem(item.category, item.id)
            Toast.makeText(this, "Kuşanıldı: ${item.displayName}", Toast.LENGTH_SHORT).show()
            bottomSheet.dismiss()
            refreshList()
        }

        sheetBinding.btnDetailBuy.setOnClickListener {
            if (store.unlockWithXP(item.id)) {
                store.equipItem(item.category, item.id)
                Toast.makeText(this, "Açıldı ve kuşanıldı!", Toast.LENGTH_SHORT).show()
                bottomSheet.dismiss()
                refreshList()
            } else {
                Toast.makeText(this, "Yetersiz XP veya seviye", Toast.LENGTH_SHORT).show()
            }
        }

        sheetBinding.btnDetailClose.setOnClickListener { bottomSheet.dismiss() }
        bottomSheet.show()
    }
}

class AvatarItemAdapter(
    private val items: List<AvatarItem>,
    private val store: AvatarStore,
    private val gam: GamificationStore,
    private val onItemClick: (AvatarItem) -> Unit
) : RecyclerView.Adapter<AvatarItemAdapter.VH>() {

    class VH(val b: ItemAvatarShopCardBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        return VH(ItemAvatarShopCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.b.tvItemName.text = item.displayName
        holder.b.tvPrice.text = if (item.priceXp == 0) "Ücretsiz" else "${item.priceXp} XP"
        holder.b.chipLevel.text = "Lv ${item.requiredLevel}"
        holder.b.chipRarity.text = item.rarity.tr

        if (item.previewDrawableRes != 0) {
            holder.b.previewThumb.setImageResource(item.previewDrawableRes)
        } else {
            holder.b.previewThumb.setImageDrawable(null)
            holder.b.previewThumb.setBackgroundResource(com.brainbuddy.app.R.drawable.avatar_bg_white)
        }

        val unlocked = store.isUnlocked(item.id)
        val levelOk = gam.level() >= item.requiredLevel
        val locked = !unlocked || !levelOk

        holder.b.lockOverlay.visibility = if (locked) View.VISIBLE else View.GONE
        holder.b.iconLock.visibility = if (locked) View.VISIBLE else View.GONE

        holder.b.root.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
