package com.edumio.app.core

import android.content.Context

/**
 * Study areas (çalışma alanları) — the student's selected exam/career profiles.
 *
 * A study area maps 1:1 to a [ProfileStore.Profile]. Because every stats store
 * (XP, streak, level, analytics, wrong pool, past tests, coach inputs) is
 * already scoped by the active profile id via [ProfileScopedPrefs] and
 * QuestionRepository's profile-scoped Room rows, switching the active area
 * switches ALL of those namespaces at once — so each area's statistics stay
 * fully isolated and nothing is ever mixed.
 *
 * The active area's [CareerPath] is mirrored into [UserGoalPrefs] so the whole
 * existing UI (Home, StudyHub, GrowthHub, Coach, daily mission subjects) keeps
 * reading a single "active career" and automatically reflects the active area.
 */
object StudyAreaManager {

    /** One study area = one profile carrying a career. */
    data class Area(val id: String, val career: CareerPath, val isActive: Boolean) {
        /** e.g. "Tıp · IMAT" */
        val displayName: String get() = "${career.displayNameTr} · ${career.examType.code}"
        val emoji: String get() = career.emoji
    }

    fun getAreas(context: Context): List<Area> {
        val store = ProfileStore(context)
        val activeId = ActiveProfileManager.getActiveProfileId(context)
        return store.getProfiles().map { p ->
            Area(id = p.id, career = safeCareer(p.careerPath), isActive = p.id == activeId)
        }
    }

    fun getActiveArea(context: Context): Area {
        val areas = getAreas(context)
        return areas.firstOrNull { it.isActive } ?: areas.first()
    }

    /**
     * Switches the active study area. Swaps every profile-scoped store to the
     * target area's namespace and mirrors its career into [UserGoalPrefs].
     */
    fun setActiveArea(context: Context, areaId: String) {
        ActiveProfileManager.setActiveProfileId(context, areaId)
        val active = getActiveArea(context)
        UserGoalPrefs(context).setCareerPath(active.career)
    }

    /**
     * Adds a new study area for [career] (as a new profile). The student name is
     * reused so League/Reports keep showing the same student. Does not change the
     * active area unless it is the very first one. Returns the new area id.
     */
    fun addArea(context: Context, career: CareerPath): String {
        val store = ProfileStore(context)
        val existing = store.getProfiles()
        // Avoid duplicate areas for the same career.
        existing.firstOrNull { safeCareer(it.careerPath) == career }?.let { return it.id }

        val studentName = existing.firstOrNull()?.name
            ?: UserGoalPrefs(context).getStudentName().ifBlank { "Öğrenci" }
        val newId = "area_${System.currentTimeMillis()}"
        store.addProfile(ProfileStore.Profile(id = newId, name = studentName, careerPath = career.name))

        if (existing.isEmpty()) setActiveArea(context, newId)
        return newId
    }

    /**
     * Removes a study area. The last remaining area cannot be removed. If the
     * active area is removed, the next available area becomes active.
     * The removed area's stats stay in its (now-orphaned) namespace and are not
     * merged into any other area.
     */
    fun removeArea(context: Context, areaId: String): Boolean {
        val store = ProfileStore(context)
        val profiles = store.getProfiles()
        if (profiles.size <= 1) return false
        if (profiles.none { it.id == areaId }) return false

        val wasActive = ActiveProfileManager.getActiveProfileId(context) == areaId
        store.removeProfile(areaId)
        if (wasActive) {
            val newActive = store.getProfiles().first().id
            setActiveArea(context, newActive)
        }
        return true
    }

    /**
     * One-time migration for users onboarded before multi-area existed: if the
     * primary profile has no real career yet but [UserGoalPrefs] holds one,
     * stamp that career onto the profile. Existing stats already live in that
     * profile's scoped namespace, so they simply become the first area's stats.
     * Idempotent and safe to call on every launch.
     */
    fun ensureMigrated(context: Context) {
        val store = ProfileStore(context)
        val goalPrefs = UserGoalPrefs(context)
        if (!goalPrefs.hasGoal()) return // brand-new user; onboarding will set areas up

        val activeId = ActiveProfileManager.getActiveProfileId(context)
        val active = store.getProfile(activeId) ?: return
        if (safeCareer(active.careerPath) == CareerPath.OTHER) {
            val legacyCareer = goalPrefs.getCareerPath()
            if (legacyCareer != CareerPath.OTHER) {
                store.setProfileCareer(activeId, legacyCareer.name)
            }
        }
    }

    private fun safeCareer(name: String?): CareerPath = try {
        CareerPath.valueOf(name ?: "")
    } catch (_: Exception) {
        CareerPath.OTHER
    }
}
