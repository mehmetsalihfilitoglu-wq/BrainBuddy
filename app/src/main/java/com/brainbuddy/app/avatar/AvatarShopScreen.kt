package com.brainbuddy.app.avatar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.brainbuddy.app.R
import com.brainbuddy.app.core.GamificationStore
import com.brainbuddy.app.core.ProtectionPrefs
import com.brainbuddy.app.databinding.ActivityAvatarShopBinding
import com.brainbuddy.app.databinding.ItemAvatarShopCardBinding

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

        b.btnShopBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    override fun onResume() {
        super.onResume()
        b.tvXpBalance.text = "${gam.xp()} XP"
        b.tvLevelBadge.text = "Lv ${gam.level()}"
        refreshList()
    }

    private fun refreshList() {
        var items = store.getCatalog()
        selectedCategory?.let { cat -> items = items.filter { it.category == cat } }
        b.recyclerItems.adapter = AvatarItemAdapter(items, store, gam) { item -> onItemClick(item) }
    }

    private fun onItemClick(item: AvatarItem) {
        val unlocked = store.isUnlocked(item.id)
        val (canBuy, reason, _) = store.canPurchase(item)

        if (unlocked) {
            store.equipItem(item.category, item.id)
            Toast.makeText(this, "Kuşanıldı: ${item.displayName}", Toast.LENGTH_SHORT).show()
            refreshList()
            return
        }

        if (!canBuy && reason != null) {
            Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
            return
        }

        if (store.isShopDisabledByParent()) {
            Toast.makeText(this, "Mağaza veli tarafından kapatıldı.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle(item.displayName)
            .setMessage("${item.priceXp} XP ile satın almak istiyor musun?")
            .setPositiveButton("Satın Al") { _, _ ->
                if (store.unlockWithXP(item.id)) {
                    store.equipItem(item.category, item.id)
                    Toast.makeText(this, "Açıldı ve kuşanıldı!", Toast.LENGTH_SHORT).show()
                    refreshList()
                } else {
                    Toast.makeText(this, "Yetersiz XP veya seviye", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("İptal", null)
            .show()
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
        holder.b.tvRarity.text = item.rarity.tr

        val unlocked = store.isUnlocked(item.id)
        val (canBuy, _, _) = store.canPurchase(item)
        val locked = !unlocked && (!canBuy || gam.level() < item.requiredLevel)

        holder.b.lockOverlay.visibility = if (locked) View.VISIBLE else View.GONE
        holder.b.iconLock.visibility = if (locked) View.VISIBLE else View.GONE

        holder.b.root.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount() = items.size
}
