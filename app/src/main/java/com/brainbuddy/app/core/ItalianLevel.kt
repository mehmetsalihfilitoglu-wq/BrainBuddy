package com.brainbuddy.app.core

enum class ItalianLevel(
    val displayName: String,
    val ceferLevel: String,
    val emoji: String,
    val description: String
) {
    A0("Hiç bilmiyorum", "A0", "🔴", "İtalyancayı sıfırdan öğreniyorum"),
    A1_A2("Biraz biliyorum", "A1–A2", "🟠", "Temel kelimeler ve ifadeler"),
    B1("Temel düzey", "B1", "🟡", "Basit cümleler kurabiliyorum"),
    B2_PLUS("İyi düzey", "B2+", "🟢", "Günlük hayatta rahatça anlaşabiliyorum")
}
