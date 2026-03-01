package com.brainbuddy.app.avatar

/**
 * Single avatar item in the shop catalog.
 */
data class AvatarItem(
    val id: String,
    val category: AvatarCategory,
    val displayName: String,
    val priceXp: Int,
    val requiredLevel: Int,
    val rarity: AvatarRarity,
    val previewDrawableRes: Int = 0
)

enum class AvatarCategory(val tr: String) {
    HAIR("Saç"),
    BACKGROUND("Arka Plan"),
    BADGE_FRAME("Çerçeve"),
    MASCOT("Maskot"),
    ACCESSORY("Aksesuar")
}

enum class AvatarRarity(val tr: String) {
    COMMON("Yaygın"),
    RARE("Nadir"),
    EPIC("Epik"),
    LEGENDARY("Efsane")
}
