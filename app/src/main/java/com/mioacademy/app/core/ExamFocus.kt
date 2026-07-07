package com.mioacademy.app.core

import com.mioacademy.app.remote.RemoteConfigKeys
import com.mioacademy.app.remote.RemoteConfigProvider

/**
 * Which exams the product actively offers right now (Remote-Config controlled).
 *
 * Go-to-market decision (Turkey-first): launch IMAT-only and hide the other exams
 * from onboarding / add-area until real content ships for them. Widen the allowlist
 * remotely (e.g. "IMAT,CENT_S") as each exam's question bank is ready — no app update.
 *
 * Existing study areas a user already created are never removed by this; it only
 * filters what's *offered* for new selection.
 */
object ExamFocus {

    /** The exam types currently offered. Falls back to all if config is empty/invalid. */
    fun activeExamTypes(): Set<ExamType> {
        val raw = RemoteConfigProvider.get().getString(RemoteConfigKeys.ACTIVE_EXAM_TYPES, "IMAT")
        val parsed = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { ExamType.valueOf(it) }.getOrNull() }
            .toSet()
        return parsed.ifEmpty { CareerPath.values().map { it.examType }.toSet() }
    }

    /** Careers offered for selection, restricted to the active exams. */
    fun selectableCareers(): List<CareerPath> {
        val active = activeExamTypes()
        val filtered = CareerPath.values().filter { it.examType in active }
        return filtered.ifEmpty { CareerPath.values().toList() }
    }
}
