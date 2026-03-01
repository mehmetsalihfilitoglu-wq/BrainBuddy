package com.brainbuddy.app.avatar

import com.brainbuddy.app.R

/**
 * Seeded catalog: many items across levels and categories.
 * Level 1-3: COMMON | Level 4-7: COMMON/RARE | Level 8-12: EPIC | Level 13+: LEGENDARY
 */
object AvatarCatalog {

    fun items(): List<AvatarItem> = listOf(
        // HAIR - 10 items
        AvatarItem("hair_default", AvatarCategory.HAIR, "Klasik", 0, 1, AvatarRarity.COMMON, R.drawable.avatar_hair_default),
        AvatarItem("hair_1", AvatarCategory.HAIR, "Kısa Kesim", 30, 1, AvatarRarity.COMMON, R.drawable.avatar_hair_short),
        AvatarItem("hair_2", AvatarCategory.HAIR, "Dalgalı", 50, 2, AvatarRarity.COMMON, R.drawable.avatar_hair_wavy),
        AvatarItem("hair_3", AvatarCategory.HAIR, "Örgülü", 80, 3, AvatarRarity.COMMON, R.drawable.avatar_hair_braid),
        AvatarItem("hair_4", AvatarCategory.HAIR, "Ponitail", 120, 4, AvatarRarity.RARE, R.drawable.avatar_hair_ponytail),
        AvatarItem("hair_5", AvatarCategory.HAIR, "Kıvırcık", 150, 5, AvatarRarity.RARE, R.drawable.avatar_hair_curly),
        AvatarItem("hair_6", AvatarCategory.HAIR, "Mohawk", 200, 6, AvatarRarity.RARE, R.drawable.avatar_hair_mohawk),
        AvatarItem("hair_7", AvatarCategory.HAIR, "Gökkuşağı", 280, 8, AvatarRarity.EPIC, R.drawable.avatar_hair_rainbow),
        AvatarItem("hair_8", AvatarCategory.HAIR, "Yıldız Işığı", 350, 10, AvatarRarity.EPIC, R.drawable.avatar_hair_star),
        AvatarItem("hair_9", AvatarCategory.HAIR, "Efsane Taç", 500, 13, AvatarRarity.LEGENDARY, R.drawable.avatar_hair_crown),

        // BACKGROUND - 10 items
        AvatarItem("bg_default", AvatarCategory.BACKGROUND, "Beyaz", 0, 1, AvatarRarity.COMMON, R.drawable.avatar_bg_white),
        AvatarItem("bg_1", AvatarCategory.BACKGROUND, "Mavi Gökyüzü", 40, 1, AvatarRarity.COMMON, R.drawable.avatar_bg_sky),
        AvatarItem("bg_2", AvatarCategory.BACKGROUND, "Yeşil Çimen", 60, 2, AvatarRarity.COMMON, R.drawable.avatar_bg_grass),
        AvatarItem("bg_3", AvatarCategory.BACKGROUND, "Turuncu Gün Batımı", 90, 3, AvatarRarity.COMMON, R.drawable.avatar_bg_sunset),
        AvatarItem("bg_4", AvatarCategory.BACKGROUND, "Mor Gece", 130, 5, AvatarRarity.RARE, R.drawable.avatar_bg_night),
        AvatarItem("bg_5", AvatarCategory.BACKGROUND, "Deniz Mavisi", 170, 6, AvatarRarity.RARE, R.drawable.avatar_bg_ocean),
        AvatarItem("bg_6", AvatarCategory.BACKGROUND, "Kar Tanesi", 220, 7, AvatarRarity.RARE, R.drawable.avatar_bg_snow),
        AvatarItem("bg_7", AvatarCategory.BACKGROUND, "Galaksi", 300, 9, AvatarRarity.EPIC, R.drawable.avatar_bg_galaxy),
        AvatarItem("bg_8", AvatarCategory.BACKGROUND, "Kuzey Işıkları", 400, 11, AvatarRarity.EPIC, R.drawable.avatar_bg_aurora),
        AvatarItem("bg_9", AvatarCategory.BACKGROUND, "Altın Parıltı", 550, 14, AvatarRarity.LEGENDARY, R.drawable.avatar_bg_gold),

        // FRAME - 8 items
        AvatarItem("frame_default", AvatarCategory.BADGE_FRAME, "Varsayılan", 0, 1, AvatarRarity.COMMON, R.drawable.avatar_frame_default),
        AvatarItem("frame_1", AvatarCategory.BADGE_FRAME, "Mavi Çerçeve", 50, 2, AvatarRarity.COMMON, R.drawable.avatar_frame_blue),
        AvatarItem("frame_2", AvatarCategory.BADGE_FRAME, "Yeşil Yaprak", 70, 3, AvatarRarity.COMMON, R.drawable.avatar_frame_leaf),
        AvatarItem("frame_3", AvatarCategory.BADGE_FRAME, "Kırmızı Yıldız", 100, 4, AvatarRarity.RARE, R.drawable.avatar_frame_star),
        AvatarItem("frame_4", AvatarCategory.BADGE_FRAME, "Gümüş Kenar", 160, 6, AvatarRarity.RARE, R.drawable.avatar_frame_silver),
        AvatarItem("frame_gold", AvatarCategory.BADGE_FRAME, "Altın Çerçeve", 250, 8, AvatarRarity.EPIC, R.drawable.avatar_frame_gold),
        AvatarItem("frame_5", AvatarCategory.BADGE_FRAME, "Elmas Kenar", 380, 10, AvatarRarity.EPIC, R.drawable.avatar_frame_diamond),
        AvatarItem("frame_6", AvatarCategory.BADGE_FRAME, "Efsane Halesi", 600, 15, AvatarRarity.LEGENDARY, R.drawable.avatar_frame_legend),

        // MASCOT - 10 items
        AvatarItem("mascot_default", AvatarCategory.MASCOT, "Brainy", 0, 1, AvatarRarity.COMMON, R.drawable.avatar_mascot_brainy),
        AvatarItem("mascot_1", AvatarCategory.MASCOT, "Gülümseyen", 80, 3, AvatarRarity.COMMON, R.drawable.avatar_mascot_smile),
        AvatarItem("mascot_2", AvatarCategory.MASCOT, "Süper Kahraman", 150, 5, AvatarRarity.RARE, R.drawable.avatar_mascot_hero),
        AvatarItem("mascot_3", AvatarCategory.MASCOT, "Uzaylı", 250, 8, AvatarRarity.EPIC, R.drawable.avatar_mascot_alien),
        AvatarItem("mascot_30d", AvatarCategory.MASCOT, "30 Gün Seri", 0, 1, AvatarRarity.LEGENDARY, R.drawable.avatar_mascot_streak),
        AvatarItem("mascot_4", AvatarCategory.MASCOT, "Robot", 100, 4, AvatarRarity.RARE, R.drawable.avatar_mascot_robot),
        AvatarItem("mascot_5", AvatarCategory.MASCOT, "Ninja", 180, 6, AvatarRarity.RARE, R.drawable.avatar_mascot_ninja),
        AvatarItem("mascot_6", AvatarCategory.MASCOT, "Büyücü", 320, 9, AvatarRarity.EPIC, R.drawable.avatar_mascot_wizard),
        AvatarItem("mascot_7", AvatarCategory.MASCOT, "Bilim İnsanı", 220, 7, AvatarRarity.RARE, R.drawable.avatar_mascot_scientist),
        AvatarItem("mascot_8", AvatarCategory.MASCOT, "Ateş Ruhu", 480, 12, AvatarRarity.LEGENDARY, R.drawable.avatar_mascot_fire),

        // ACCESSORY - 10 items
        AvatarItem("acc_1", AvatarCategory.ACCESSORY, "Gözlük", 45, 1, AvatarRarity.COMMON, R.drawable.avatar_acc_glasses),
        AvatarItem("acc_2", AvatarCategory.ACCESSORY, "Şapka", 75, 2, AvatarRarity.COMMON, R.drawable.avatar_acc_hat),
        AvatarItem("acc_3", AvatarCategory.ACCESSORY, "Kolye", 110, 4, AvatarRarity.RARE, R.drawable.avatar_acc_necklace),
        AvatarItem("acc_4", AvatarCategory.ACCESSORY, "Madalya", 190, 7, AvatarRarity.EPIC, R.drawable.avatar_acc_medal),
        AvatarItem("acc_5", AvatarCategory.ACCESSORY, "Taç", 450, 12, AvatarRarity.LEGENDARY, R.drawable.avatar_acc_crown),
        AvatarItem("acc_6", AvatarCategory.ACCESSORY, "Fular", 55, 2, AvatarRarity.COMMON, R.drawable.avatar_acc_scarf),
        AvatarItem("acc_7", AvatarCategory.ACCESSORY, "Papyon", 95, 3, AvatarRarity.COMMON, R.drawable.avatar_acc_bow),
        AvatarItem("acc_8", AvatarCategory.ACCESSORY, "Yıldız Rozet", 140, 5, AvatarRarity.RARE, R.drawable.avatar_acc_star),
        AvatarItem("acc_9", AvatarCategory.ACCESSORY, "Işıltı", 260, 8, AvatarRarity.EPIC, R.drawable.avatar_acc_sparkle),
        AvatarItem("acc_10", AvatarCategory.ACCESSORY, "Kanatlar", 520, 14, AvatarRarity.LEGENDARY, R.drawable.avatar_acc_wings)
    )
}
