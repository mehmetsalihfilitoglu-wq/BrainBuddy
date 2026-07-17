package com.edumio.app.core

import android.content.Context

/**
 * Central source of truth for the currently active child profile.
 *
 * Backed by `ProfileStore` so existing code using `ProfileStore.getCurrentProfileId()`
 * keeps working. This wrapper ensures:
 * - There is always at least one profile.
 * - The active profile id is always a valid existing profile id.
 */
object ActiveProfileManager {

    /**
     * Returns the active profile id. If none is set or it points to a deleted profile,
     * falls back to the first existing profile or creates the default profile.
     */
    fun getActiveProfileId(context: Context): String {
        val profileStore = ProfileStore(context)
        val profiles = profileStore.getProfiles()
        if (profiles.isEmpty()) {
            // ProfileStore guarantees a default profile, but double‑check defensively.
            val defaultId = ProfileStore.DEFAULT_ID
            profileStore.setProfiles(listOf(ProfileStore.Profile(defaultId, "Öğrenci", true)))
            profileStore.setCurrentProfileId(defaultId)
            return defaultId
        }

        val current = profileStore.getCurrentProfileId()
        val resolved = if (profiles.any { it.id == current }) {
            current
        } else {
            profiles.first().id
        }

        if (resolved != current) {
            profileStore.setCurrentProfileId(resolved)
        }

        return resolved
    }

    /**
     * Sets the active profile id. If the requested id does not exist (e.g. profile deleted),
     * falls back to the first available profile.
     */
    fun setActiveProfileId(context: Context, requestedId: String) {
        val profileStore = ProfileStore(context)
        val profiles = profileStore.getProfiles()
        if (profiles.isEmpty()) {
            val defaultId = ProfileStore.DEFAULT_ID
            profileStore.setProfiles(listOf(ProfileStore.Profile(defaultId, "Öğrenci", true)))
            profileStore.setCurrentProfileId(defaultId)
            return
        }

        val target = profiles.find { it.id == requestedId } ?: profiles.first()
        profileStore.setCurrentProfileId(target.id)
    }
}

