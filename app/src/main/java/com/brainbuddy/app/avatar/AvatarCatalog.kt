package com.brainbuddy.app.avatar

/**
 * Seeded catalog: many items across levels and categories.
 * Level 1-3: COMMON | Level 4-7: COMMON/RARE | Level 8-12: EPIC | Level 13+: LEGENDARY
 */
object AvatarCatalog {

    fun items(): List<AvatarItem> = listOf(
        // HAIR - Level 1-3
        AvatarItem("hair_default", AvatarCategory.HAIR, "Klasik", 0, 1, AvatarRarity.COMMON),
        AvatarItem("hair_1", AvatarCategory.HAIR, "Kısa Kesim", 30, 1, AvatarRarity.COMMON),
        AvatarItem("hair_2", AvatarCategory.HAIR, "Dalgalı", 50, 2, AvatarRarity.COMMON),
        AvatarItem("hair_3", AvatarCategory.HAIR, "Örgülü", 80, 3, AvatarRarity.COMMON),
        AvatarItem("hair_4", AvatarCategory.HAIR, "Ponitail", 120, 4, AvatarRarity.RARE),
        AvatarItem("hair_5", AvatarCategory.HAIR, "Kıvırcık", 150, 5, AvatarRarity.RARE),
        AvatarItem("hair_6", AvatarCategory.HAIR, "Mohawk", 200, 6, AvatarRarity.RARE),
        AvatarItem("hair_7", AvatarCategory.HAIR, "Gökkuşağı", 280, 8, AvatarRarity.EPIC),
        AvatarItem("hair_8", AvatarCategory.HAIR, "Yıldız Işığı", 350, 10, AvatarRarity.EPIC),
        AvatarItem("hair_9", AvatarCategory.HAIR, "Efsane Taç", 500, 13, AvatarRarity.LEGENDARY),

        // BACKGROUND - Level 1-3
        AvatarItem("bg_default", AvatarCategory.BACKGROUND, "Beyaz", 0, 1, AvatarRarity.COMMON),
        AvatarItem("bg_1", AvatarCategory.BACKGROUND, "Mavi Gökyüzü", 40, 1, AvatarRarity.COMMON),
        AvatarItem("bg_2", AvatarCategory.BACKGROUND, "Yeşil Çimen", 60, 2, AvatarRarity.COMMON),
        AvatarItem("bg_3", AvatarCategory.BACKGROUND, "Turuncu Gün Batımı", 90, 3, AvatarRarity.COMMON),
        AvatarItem("bg_4", AvatarCategory.BACKGROUND, "Mor Gece", 130, 5, AvatarRarity.RARE),
        AvatarItem("bg_5", AvatarCategory.BACKGROUND, "Deniz Mavisi", 170, 6, AvatarRarity.RARE),
        AvatarItem("bg_6", AvatarCategory.BACKGROUND, "Kar Tanesi", 220, 7, AvatarRarity.RARE),
        AvatarItem("bg_7", AvatarCategory.BACKGROUND, "Galaksi", 300, 9, AvatarRarity.EPIC),
        AvatarItem("bg_8", AvatarCategory.BACKGROUND, "Kuzey Işıkları", 400, 11, AvatarRarity.EPIC),
        AvatarItem("bg_9", AvatarCategory.BACKGROUND, "Altın Parıltı", 550, 14, AvatarRarity.LEGENDARY),

        // FRAME - Level 1-3
        AvatarItem("frame_default", AvatarCategory.BADGE_FRAME, "Varsayılan", 0, 1, AvatarRarity.COMMON),
        AvatarItem("frame_1", AvatarCategory.BADGE_FRAME, "Mavi Çerçeve", 50, 2, AvatarRarity.COMMON),
        AvatarItem("frame_2", AvatarCategory.BADGE_FRAME, "Yeşil Yaprak", 70, 3, AvatarRarity.COMMON),
        AvatarItem("frame_3", AvatarCategory.BADGE_FRAME, "Kırmızı Yıldız", 100, 4, AvatarRarity.RARE),
        AvatarItem("frame_4", AvatarCategory.BADGE_FRAME, "Gümüş Kenar", 160, 6, AvatarRarity.RARE),
        AvatarItem("frame_gold", AvatarCategory.BADGE_FRAME, "Altın Çerçeve", 250, 8, AvatarRarity.EPIC),
        AvatarItem("frame_5", AvatarCategory.BADGE_FRAME, "Elmas Kenar", 380, 10, AvatarRarity.EPIC),
        AvatarItem("frame_6", AvatarCategory.BADGE_FRAME, "Efsane Halesi", 600, 15, AvatarRarity.LEGENDARY),

        // MASCOT
        AvatarItem("mascot_default", AvatarCategory.MASCOT, "Brainy", 0, 1, AvatarRarity.COMMON),
        AvatarItem("mascot_1", AvatarCategory.MASCOT, "Gülümseyen", 80, 3, AvatarRarity.COMMON),
        AvatarItem("mascot_2", AvatarCategory.MASCOT, "Süper Kahraman", 150, 5, AvatarRarity.RARE),
        AvatarItem("mascot_3", AvatarCategory.MASCOT, "Uzaylı", 250, 8, AvatarRarity.EPIC),
        AvatarItem("mascot_30d", AvatarCategory.MASCOT, "30 Gün Seri", 0, 1, AvatarRarity.LEGENDARY),

        // ACCESSORY
        AvatarItem("acc_1", AvatarCategory.ACCESSORY, "Gözlük", 45, 1, AvatarRarity.COMMON),
        AvatarItem("acc_2", AvatarCategory.ACCESSORY, "Şapka", 75, 2, AvatarRarity.COMMON),
        AvatarItem("acc_3", AvatarCategory.ACCESSORY, "Kolye", 110, 4, AvatarRarity.RARE),
        AvatarItem("acc_4", AvatarCategory.ACCESSORY, "Madalya", 190, 7, AvatarRarity.EPIC),
        AvatarItem("acc_5", AvatarCategory.ACCESSORY, "Taç", 450, 12, AvatarRarity.LEGENDARY)
    )
}
