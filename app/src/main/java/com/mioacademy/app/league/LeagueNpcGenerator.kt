package com.mioacademy.app.league

/**
 * Generates NPC profiles and target scores proportional to student expected weekly score.
 * 2 strong (+10% to +25%), 10 around similar (-10% to +10%), 3 weaker (-25% to -10%).
 */
object LeagueNpcGenerator {

    private val NPC_NAMES = listOf(
        "Elif", "Arda", "Zeynep", "Emre", "Defne", "Can", "Ece", "Berk", "Ada", "Ege",
        "Selin", "Kaan", "Deniz", "Alp", "Sude", "Mert", "Ela", "Barış", "Duru", "Cem"
    )

    // Real AvatarCatalog mascot IDs — same key ("MASCOT") as the student entry.
    // All binding code resolves these through AvatarCatalog; no fallback special-casing needed.
    private val AVATAR_IDS = listOf(
        "mascot_default", "mascot_1", "mascot_2", "mascot_3", "mascot_4",
        "mascot_5", "mascot_6", "mascot_7", "mascot_8", "mascot_30d"
    )

    fun generateNpcProfiles(count: Int = 14, seed: Long): List<NpcProfile> {
        val rng = kotlin.random.Random(seed)
        val shuffledNames = NPC_NAMES.shuffled(rng)
        return (0 until count).map { i ->
            val name = shuffledNames[(i % shuffledNames.size).coerceIn(0, shuffledNames.size - 1)]
            val rawIdx = ((seed xor (i * 7L)) and 0x7FFFFFFFL) % AVATAR_IDS.size
            val avatar = AVATAR_IDS[rawIdx.toInt().coerceIn(0, AVATAR_IDS.size - 1)]
            NpcProfile(
                id = "npc_${seed}_$i",
                displayName = name,
                avatarCosmetics = mapOf("MASCOT" to avatar),
                isNpc = true
            )
        }
    }

    /**
     * Generate target scores for NPCs around studentExpectedWeeklyScore.
     * Distribution: 2 strong (+10% to +25%), 10 similar (-10% to +10%), 3 weaker (-25% to -10%).
     */
    fun generateNpcTargetScores(
        studentExpectedWeeklyScore: Int,
        npcProfiles: List<NpcProfile>,
        seed: Long
    ): Map<String, Int> {
        val base = studentExpectedWeeklyScore.coerceAtLeast(20)
        val rng = kotlin.random.Random(seed)
        val targets = mutableMapOf<String, Int>()

        val shuffled = npcProfiles.shuffled(rng)
        // 2 strong
        (0 until 2).forEach { i ->
            if (i < shuffled.size) {
                val mult = 1.1 + rng.nextDouble() * 0.15 // +10% to +25%
                targets[shuffled[i].id] = (base * mult).toInt().coerceAtLeast(0)
            }
        }
        // 10 similar
        (2 until 12).forEach { i ->
            if (i < shuffled.size) {
                val mult = 0.9 + rng.nextDouble() * 0.2 // -10% to +10%
                targets[shuffled[i].id] = (base * mult).toInt().coerceAtLeast(0)
            }
        }
        // 3 weaker
        (12 until 15).forEach { i ->
            if (i < shuffled.size) {
                val mult = 0.75 + rng.nextDouble() * 0.15 // -25% to -10%
                targets[shuffled[i].id] = (base * mult).toInt().coerceAtLeast(0)
            }
        }
        return targets
    }

    /**
     * Apply daily variation: add small random increments so leaderboard feels alive.
     * Each NPC gets +0 to +8 points per day (based on seed + day).
     */
    fun applyDailyVariation(
        baseScores: Map<String, Int>,
        dayIndex: Long
    ): Map<String, Int> {
        return baseScores.mapValues { (id, score) ->
            val rng = kotlin.random.Random(dayIndex * 31L + id.hashCode())
            val delta = rng.nextInt(0, 9)
            (score + delta).coerceAtLeast(0)
        }
    }
}
