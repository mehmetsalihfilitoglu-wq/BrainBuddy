package com.edumio.app.core

import android.content.Context
import com.edumio.app.avatar.AvatarCategory
import com.edumio.app.avatar.AvatarStore

/**
 * Student profile display preferences (displayName, selected avatar).
 * Owned avatars and selection delegated to AvatarStore.
 */
class StudentProfileStore(context: Context) {
    private val prefs = ProfileScopedPrefs.studentProfile(context)
    private val avatarStore = AvatarStore(context)

    fun getDisplayName(): String = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
    fun setDisplayName(name: String) = prefs.edit().putString(KEY_DISPLAY_NAME, name.trim()).apply()

    /** Selected mascot avatar ID from AvatarStore equipped items. */
    fun getSelectedAvatarId(): String =
        avatarStore.getEquippedItems()[AvatarCategory.MASCOT] ?: "mascot_default"

    fun setSelectedAvatarId(id: String) {
        avatarStore.equipItem(AvatarCategory.MASCOT, id)
    }

    /** All owned avatar item IDs from AvatarStore. */
    fun getOwnedAvatarIds(): Set<String> {
        val catalog = avatarStore.getCatalog()
        return catalog.filter { avatarStore.isUnlocked(it.id) }.map { it.id }.toSet()
    }
    companion object {
        private const val KEY_DISPLAY_NAME = "student_display_name"
    }
}
