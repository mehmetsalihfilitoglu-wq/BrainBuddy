package com.brainbuddy.app.core

import android.content.Context
import com.brainbuddy.app.R
import com.brainbuddy.app.avatar.AvatarCatalog
import com.brainbuddy.app.avatar.AvatarCategory
import com.brainbuddy.app.avatar.AvatarStore

/**
 * Aggregates all user-facing profile data from their respective stores into a single
 * read model. All UI that needs displayName, avatar, XP, level or streak should pull
 * from here so every screen sees the same values.
 *
 * Sources:
 *   displayName   → StudentProfileStore (bb_student_profile_<id> → "student_display_name")
 *   avatarId      → AvatarStore equipped MASCOT (bb_avatar_<id> → "equipped_json")
 *   xp / level / streak → GamificationStore (bb_gamification_<id>)
 */
data class UserProfile(
    val displayName: String,
    val selectedAvatarId: String,
    val selectedAvatarDrawableRes: Int,
    val xp: Int,
    val level: Int,
    val streakDays: Int
)

object UserProfileProvider {

    fun get(context: Context): UserProfile {
        val studentStore = StudentProfileStore(context)
        val avatarStore  = AvatarStore(context)
        val gam          = GamificationStore(context)
        val profileStore = ProfileStore(context)
        val profileId    = profileStore.getCurrentProfileId()

        // Display name: student's own setting takes priority; fall back to profile name
        val rawName = studentStore.getDisplayName()
        val displayName = when {
            rawName.isNotEmpty() -> rawName
            else -> profileStore.getProfile(profileId)?.name?.takeIf { it.isNotEmpty() } ?: "Öğrenci"
        }

        // Avatar: equipped mascot from AvatarStore
        val mascotId = avatarStore.getEquippedItems()[AvatarCategory.MASCOT] ?: "mascot_default"
        val mascotDrawableRes = AvatarCatalog.items()
            .find { it.id == mascotId }
            ?.previewDrawableRes
            ?.takeIf { it != 0 }
            ?: R.drawable.avatar_mascot_brainy

        return UserProfile(
            displayName          = displayName,
            selectedAvatarId     = mascotId,
            selectedAvatarDrawableRes = mascotDrawableRes,
            xp                   = gam.xp(),
            level                = gam.level(),
            streakDays           = gam.streakDays()
        )
    }
}
