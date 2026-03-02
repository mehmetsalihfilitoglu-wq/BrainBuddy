package com.brainbuddy.app.avatar

import android.content.Context
import com.brainbuddy.app.core.ProfileScopedPrefs
import org.json.JSONObject

class AvatarStore(private val context: Context) {
    private val prefs = ProfileScopedPrefs.avatar(context)
    private val gamification = com.brainbuddy.app.core.GamificationStore(context)

    fun isShopDisabledByParent(): Boolean = prefs.getBoolean(KEY_SHOP_DISABLED, false)
    fun setShopDisabledByParent(v: Boolean) = prefs.edit().putBoolean(KEY_SHOP_DISABLED, v).apply()

    fun getCatalog(): List<AvatarItem> = AvatarCatalog.items()

    fun isUnlocked(itemId: String): Boolean {
        val unlocked = prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()
        if (itemId in unlocked) return true
        val item = getCatalog().find { it.id == itemId } ?: return false
        if (item.priceXp == 0 && item.requiredLevel <= 1) return true
        if (item.id == "mascot_30d" && gamification.milestoneBadges().contains(com.brainbuddy.app.core.GamificationStore.MILESTONE_30)) return true
        return false
    }

    init {
        ensureDefaultEquipped()
    }

    fun canPurchase(item: AvatarItem): Triple<Boolean, String?, String?> {
        if (isUnlocked(item.id)) return Triple(true, null, null)
        if (isShopDisabledByParent()) return Triple(false, "Mağaza kapalı", null)
        val userLevel = gamification.level()
        val userXp = gamification.xp()
        if (userLevel < item.requiredLevel) return Triple(false, "Bu öğe Seviye ${item.requiredLevel} gerektirir.", null)
        if (userXp < item.priceXp) return Triple(false, "Yetersiz XP", null)
        return Triple(true, null, null)
    }

    fun unlockWithXP(itemId: String): Boolean {
        val (can, _, _) = canPurchase(getCatalog().find { it.id == itemId } ?: return false)
        if (!can) return false
        if (isUnlocked(itemId)) return true
        val item = getCatalog().find { it.id == itemId } ?: return false
        if (item.priceXp > 0) gamification.addXp(-item.priceXp)
        val set = (prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()).toMutableSet()
        set.add(itemId)
        prefs.edit().putStringSet(KEY_UNLOCKED, set).apply()
        return true
    }

    fun getEquippedItems(): Map<AvatarCategory, String> {
        val arr = prefs.getString(KEY_EQUIPPED, "{}") ?: "{}"
        return try {
            val o = JSONObject(arr)
            AvatarCategory.entries.associateWith { cat ->
                o.optString(cat.name, "").takeIf { it.isNotEmpty() } ?: ""
            }.filterValues { it.isNotEmpty() && isUnlocked(it) }
        } catch (_: Exception) { emptyMap() }
    }

    fun equipItem(category: AvatarCategory, itemId: String) {
        val item = getCatalog().find { it.id == itemId } ?: return
        if (gamification.level() < item.requiredLevel) return
        if (!isUnlocked(itemId)) return
        val map = getEquippedItems().toMutableMap()
        map[category] = itemId
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k.name, v) }
        prefs.edit().putString(KEY_EQUIPPED, o.toString()).apply()
    }

    fun ensureDefaultEquipped() {
        val current = getEquippedItems()
        val catalog = getCatalog()
        var changed = false
        val map = current.toMutableMap()
        for (cat in AvatarCategory.entries) {
            val currentId = map[cat]
            if (currentId.isNullOrEmpty() || !isUnlocked(currentId)) {
                val defaultItem = catalog.find { it.category == cat && (it.priceXp == 0 || it.requiredLevel <= 1) }
                    ?: catalog.find { it.category == cat }
                if (defaultItem != null && isUnlocked(defaultItem.id)) {
                    map[cat] = defaultItem.id
                    changed = true
                }
            }
        }
        if (changed) {
            val o = JSONObject()
            map.filterValues { it.isNotEmpty() }.forEach { (k, v) -> o.put(k.name, v) }
            prefs.edit().putString(KEY_EQUIPPED, o.toString()).apply()
        }
    }

    companion object {
        private const val KEY_UNLOCKED = "unlocked_ids"
        private const val KEY_EQUIPPED = "equipped_json"
        private const val KEY_SHOP_DISABLED = "shop_disabled_by_parent"
    }
}
