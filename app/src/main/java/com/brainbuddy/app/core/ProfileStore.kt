package com.brainbuddy.app.core

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Multi-child profiles: name, separate stats/levels/mastery per profile.
 * Default profile "default" for backward compatibility.
 */
class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Profile(
        val id: String,
        val name: String,
        val sharedBlockedApps: Boolean = true
    )

    fun getCurrentProfileId(): String = prefs.getString(KEY_CURRENT, DEFAULT_ID) ?: DEFAULT_ID
    fun setCurrentProfileId(id: String) = prefs.edit().putString(KEY_CURRENT, id).apply()

    fun getProfiles(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { return listOf(Profile(DEFAULT_ID, "Öğrenci", true)) }
        if (arr.length() == 0) {
            // Ensure default exists
            setProfiles(listOf(Profile(DEFAULT_ID, "Öğrenci", true)))
            return listOf(Profile(DEFAULT_ID, "Öğrenci", true))
        }
        return (0 until arr.length()).mapNotNull {
            val o = arr.optJSONObject(it) ?: return@mapNotNull null
            Profile(
                id = o.optString("id", DEFAULT_ID),
                name = o.optString("name", "Öğrenci"),
                sharedBlockedApps = o.optBoolean("sharedBlockedApps", true)
            )
        }
    }

    fun setProfiles(profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("sharedBlockedApps", p.sharedBlockedApps)
            })
        }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    fun addProfile(profile: Profile) {
        val list = getProfiles().toMutableList()
        if (list.none { it.id == profile.id }) list.add(profile)
        setProfiles(list)
    }

    fun removeProfile(id: String) {
        if (id == DEFAULT_ID) return
        val list = getProfiles().filter { it.id != id }.toMutableList()
        if (list.isEmpty()) list.add(Profile(DEFAULT_ID, "Öğrenci", true))
        setProfiles(list)
        if (getCurrentProfileId() == id) setCurrentProfileId(DEFAULT_ID)
    }

    fun getProfile(id: String): Profile? = getProfiles().find { it.id == id }

    companion object {
        const val DEFAULT_ID = "default"
        private const val PREFS = "bb_profiles"
        private const val KEY_CURRENT = "current_profile_id"
        private const val KEY_PROFILES = "profiles_json"
    }
}
