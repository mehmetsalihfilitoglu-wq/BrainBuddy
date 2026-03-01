package com.brainbuddy.app.avatar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AvatarItem(
    val id: String,
    val type: AvatarCategory,
    val priceXP: Int,
    val unlocked: Boolean
)

enum class AvatarCategory { HAIR, BACKGROUND, BADGE_FRAME, MASCOT }

class AvatarStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gamification = com.brainbuddy.app.core.GamificationStore(context)

    fun isShopDisabledByParent(): Boolean = prefs.getBoolean(KEY_SHOP_DISABLED, false)
    fun setShopDisabledByParent(v: Boolean) = prefs.edit().putBoolean(KEY_SHOP_DISABLED, v).apply()

    fun getAvatarItems(): List<AvatarItem> {
        val default = defaultItems()
        val unlocked = prefs.getStringSet(KEY_UNLOCKED, emptySet()) ?: emptySet()
        val has30DayBadge = com.brainbuddy.app.core.GamificationStore(context).milestoneBadges().contains(com.brainbuddy.app.core.GamificationStore.MILESTONE_30)
        return default.map { item ->
            val isUnlocked = item.id in unlocked || item.priceXP == 0 ||
                (item.id == "mascot_30d" && has30DayBadge)
            item.copy(unlocked = isUnlocked)
        }
    }

    fun unlockWithXP(itemId: String): Boolean {
        if (isShopDisabledByParent()) return false
        val items = getAvatarItems()
        val item = items.find { it.id == itemId } ?: return false
        if (item.unlocked) return true
        if (gamification.xp() < item.priceXP) return false
        gamification.addXp(-item.priceXP)
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
                o.optString(cat.name, "").takeIf { it.isNotEmpty() } ?: defaultItems().firstOrNull { it.type == cat && it.unlocked }?.id ?: ""
            }.filterValues { it.isNotEmpty() }
        } catch (_: Exception) { emptyMap() }
    }

    fun equipItem(category: AvatarCategory, itemId: String) {
        val items = getAvatarItems()
        val item = items.find { it.id == itemId && it.unlocked } ?: return
        val map = getEquippedItems().toMutableMap()
        map[category] = itemId
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k.name, v) }
        prefs.edit().putString(KEY_EQUIPPED, o.toString()).apply()
    }

    private fun defaultItems(): List<AvatarItem> = listOf(
        AvatarItem("hair_default", AvatarCategory.HAIR, 0, true),
        AvatarItem("hair_1", AvatarCategory.HAIR, 50, false),
        AvatarItem("hair_2", AvatarCategory.HAIR, 100, false),
        AvatarItem("bg_default", AvatarCategory.BACKGROUND, 0, true),
        AvatarItem("bg_1", AvatarCategory.BACKGROUND, 75, false),
        AvatarItem("bg_2", AvatarCategory.BACKGROUND, 150, false),
        AvatarItem("frame_default", AvatarCategory.BADGE_FRAME, 0, true),
        AvatarItem("frame_gold", AvatarCategory.BADGE_FRAME, 200, false),
        AvatarItem("mascot_default", AvatarCategory.MASCOT, 0, true),
        AvatarItem("mascot_30d", AvatarCategory.MASCOT, 0, false)
    )

    companion object {
        private const val PREFS = "bb_avatar"
        private const val KEY_UNLOCKED = "unlocked_ids"
        private const val KEY_EQUIPPED = "equipped_json"
        private const val KEY_SHOP_DISABLED = "shop_disabled_by_parent"
    }
}
