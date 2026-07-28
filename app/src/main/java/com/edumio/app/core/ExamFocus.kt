package com.edumio.app.core

import com.edumio.app.remote.RemoteConfigKeys
import com.edumio.app.remote.RemoteConfigProvider

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
        val raw = RemoteConfigProvider.get().getString(RemoteConfigKeys.ACTIVE_EXAM_TYPES, "IMAT,TIL_I,CENT_S")
        val parsed = raw.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { runCatching { ExamType.valueOf(it) }.getOrNull() }
            .toSet()
        return parsed.ifEmpty { CareerPath.values().map { it.examType }.toSet() }
    }

    /** Canonical display order for the v1 exams. */
    private val EXAM_ORDER = listOf(ExamType.IMAT, ExamType.TIL_I, ExamType.CENT_S)

    /**
     * The selectable study areas — EXACTLY ONE per active exam (IMAT / TIL-I / CEnT-S), in canonical
     * order. v1 selects by exam, not by degree/career, so many careers collapsing to the same examType
     * yield a single card (a representative CareerPath carries the isolated question bank for that exam).
     */
    fun selectableCareers(): List<CareerPath> {
        val active = activeExamTypes()
        val oneCardPerExam = CareerPath.values()
            .filter { it.examType in active }
            .distinctBy { it.examType }
            .sortedBy { c -> EXAM_ORDER.indexOf(c.examType).let { if (it < 0) Int.MAX_VALUE else it } }
        return oneCardPerExam.ifEmpty { CareerPath.values().distinctBy { it.examType } }
    }
}
