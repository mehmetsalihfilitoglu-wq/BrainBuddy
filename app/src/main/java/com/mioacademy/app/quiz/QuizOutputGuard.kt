package com.mioacademy.app.quiz

import android.util.Log
import com.mioacademy.app.db.QuestionStemHash
import java.util.Locale

/**
 * Final output gate: applied AFTER the picker selects questions,
 * BEFORE they reach any UI.
 *
 * Single enforcement point for two invariants:
 *   1. Choice quality  — all four choices in every question are non-blank
 *                        and mutually distinct (case-insensitive, trimmed).
 *   2. Repeat detection — recently-seen question IDs are logged so the
 *                         picker's anti-repeat gaps surface in the logs.
 *
 * Design contract
 * ───────────────
 * • Always reads  presentationChoices when set, falls back to raw choices.
 *   This alone closes the Bug-1A bypass (QuizActivity was reading q.choices
 *   and ignoring the runtime-fixed presentationChoices entirely).
 * • Always writes a sanitised presentationChoices so every downstream
 *   caller can unconditionally use `q.presentationChoices ?: q.choices`.
 * • Never mutates question IDs or correctIndex.
 * • Never throws; every error path is logged and returned as-is.
 */
object QuizOutputGuard {

    private const val TAG = "QuizOutputGuard"

    // ── Public types ─────────────────────────────────────────────────────────

    data class Report(
        val questions: List<Question>,
        /** Questions that had ≥1 choice sanitised. */
        val fixedCount: Int,
        /** Questions whose ID appeared in recentIds (repeat detected). */
        val repeatCount: Int,
        /** Questions rejected because options could not be made valid. */
        val rejectedInvalidOptions: Int = 0,
    )

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Validate and sanitise a full quiz batch.
     *
     * @param questions  Final list from repository / picker.
     * @param recentIds  Question IDs seen in the last N tests for this profile.
     *                   Used for repeat detection only — the caller is responsible
     *                   for prevention; this layer only detects and logs.
     */
    fun validate(questions: List<Question>, recentIds: Set<String> = emptySet()): Report {
        var fixedCount = 0
        var repeatCount = 0
        var rejectedInvalidOptions = 0

        val out = mutableListOf<Question>()
        for (q in questions) {
            if (q.id in recentIds) {
                repeatCount++
                Log.w(TAG, "REPEAT_DETECTED id=${q.id} sub=${q.subject}")
            }
            val (sanitised, wasFixed) = sanitiseChoices(q)
            if (wasFixed) fixedCount++

            // Hard gate: reject any question where choices are still invalid after sanitise.
            val finalChoices = sanitised.presentationChoices ?: sanitised.choices
            val invalidReason = choicesInvalidReason(finalChoices, sanitised.correctIndex)
            if (invalidReason != null) {
                rejectedInvalidOptions++
                Log.e(
                    TAG,
                    "[INVALID_OPTIONS_DETECTED] REJECTED id=${q.id} sub=${q.subject} " +
                        "reason=$invalidReason choices=${finalChoices.joinToString("|") { it.take(20) }}"
                )
                continue
            }

            out.add(sanitised)
        }

        if (repeatCount > 0) Log.w(TAG, "guard_batch: $repeatCount repeat(s) in ${questions.size} questions")
        if (fixedCount  > 0) Log.w(TAG, "guard_batch: $fixedCount choice fix(es) in ${questions.size} questions")
        if (rejectedInvalidOptions > 0) Log.e(TAG, "guard_batch: $rejectedInvalidOptions question(s) REJECTED due to invalid options")

        return Report(out, fixedCount, repeatCount, rejectedInvalidOptions)
    }

    /**
     * Returns a reason string if [choices] are invalid, null if all choices are valid.
     * Checks: blank/dash, placeholder text, too few choices, duplicates,
     *         telegraphed answer (when correctIndex >= 0), weak numeric distractors.
     */
    fun choicesInvalidReason(choices: List<String>, correctIndex: Int = -1): String? {
        if (choices.size < 4) return "TOO_FEW_CHOICES(${choices.size})"

        val placeholderPattern = Regex(
            "^(Se[çc]enek|Option|Cevap|[Şş][ıi]k)\\s*[A-Ea-e]$",
            RegexOption.IGNORE_CASE
        )

        for ((i, c) in choices.take(4).withIndex()) {
            val t = c.trim()
            if (t.isBlank() || t == "-") return "BLANK_CHOICE(idx=$i)"
            if (placeholderPattern.matches(t)) {
                Log.w(TAG, "[PLACEHOLDER_OPTION_REJECTED] idx=$i value='$t'")
                return "PLACEHOLDER_CHOICE(idx=$i,val=$t)"
            }
        }

        val distinctTrimmed = choices.take(4).map { it.trim() }.distinct()
        if (distinctTrimmed.size < 4) return "DUPLICATE_CHOICES"

        if (correctIndex in 0..3) {
            if (isHardTelegraphed(choices, correctIndex)) {
                Log.w(TAG, "[TELEGRAPHED_REJECTED] correctIdx=$correctIndex " +
                    "correctLen=${choices[correctIndex].trim().length} " +
                    "choices=${choices.joinToString("|") { it.take(25) }}")
                return "TELEGRAPHED_ANSWER"
            }
            if (isWeakNumericDistractors(choices, correctIndex)) {
                Log.w(TAG, "[WEAK_DISTRACTOR_REJECTED] correctIdx=$correctIndex " +
                    "choices=${choices.joinToString("|") { it.take(25) }}")
                return "WEAK_NUMERIC_DISTRACTOR"
            }
        }

        return null
    }

    /**
     * Returns true when the correct answer is more than twice the length of the
     * LONGEST distractor — making it visually obvious without reading content.
     */
    fun isHardTelegraphed(options: List<String>, correctIndex: Int): Boolean {
        if (options.size < 4 || correctIndex !in options.indices) return false
        val correctLen = options[correctIndex].trim().length
        val maxDistractorLen = options.indices
            .filter { it != correctIndex }
            .maxOfOrNull { options[it].trim().length } ?: return false
        if (maxDistractorLen < 6) return false
        return correctLen > maxDistractorLen * 2
    }

    /**
     * Returns true when ALL four options are pure numbers and
     * max/min > 2.5 — indicating distractors are implausibly far from the correct value.
     */
    fun isWeakNumericDistractors(options: List<String>, correctIndex: Int = -1): Boolean {
        if (options.size < 4) return false
        val nums = options.take(4).mapNotNull { s ->
            s.trim().replace(',', '.').toDoubleOrNull()
        }
        if (nums.size < 4) return false
        val min = nums.min()
        val max = nums.max()
        if (min <= 0) return false
        return max / min > 2.5
    }

    /**
     * Sanitise a single question's choices.
     *
     * Safe to call from any Activity / ViewHolder as a defence-in-depth measure.
     * Always returns a question with [Question.presentationChoices] set.
     */
    fun sanitizeQuestion(q: Question): Question = sanitiseChoices(q).first

    // ── Enforcement (hard guarantee) ───────────────────────────────────

    /**
     * Result of [enforce] with structured context for observability.
     */
    data class EnforceReport(
        val questions: List<Question>,
        val requested: Int,
        val delivered: Int,
        val blockedById: Int,
        val blockedByFingerprint: Int,
        val withinQuizDupRemoved: Int,
        val fallbackStage: String,
    )

    /**
     * Defense-in-depth enforcement layer. Runs on fully materialized [Question] objects
     * AFTER picker selection, BEFORE returning to caller.
     *
     * Fallback chain:
     *   Stage 1 — hard exclude by ID
     *   Stage 2 — hard exclude by content fingerprint
     *   Stage 3 — within-quiz dedup (by ID + fingerprint)
     *   Stage 4 — if result < requested: return shorter quiz (REPEAT_LAST_RESORT)
     *
     * Never silently falls back to old behavior. Every exclusion is logged.
     *
     * @param questions     Final list from picker (pre-enforcement).
     * @param snapshot      Cross-quiz exclusion snapshot from [RepeatPolicy].
     * @param pickerTag     Identifies calling picker for logs.
     * @param requested     Original requested quiz size.
     */
    fun enforce(
        questions: List<Question>,
        snapshot: RepeatPolicy.ExclusionSnapshot,
        pickerTag: String,
        requested: Int = questions.size
    ): EnforceReport {
        if (questions.isEmpty()) {
            return EnforceReport(emptyList(), requested, 0, 0, 0, 0, "EMPTY_INPUT")
        }

        var blockedById = 0
        var blockedByFingerprint = 0
        var withinQuizDup = 0
        val seenIds = mutableSetOf<String>()
        val seenFingerprints = mutableSetOf<String>()
        val accepted = mutableListOf<Question>()

        for (q in questions) {
            val fp = QuestionStemHash.contentFingerprint(q.stem, q.choices, q.correctIndex)

            // Stage 1: hard exclude by ID
            if (q.id in snapshot.excludedIds) {
                blockedById++
                Log.w(TAG, "REPEAT_BLOCKED picker=$pickerTag id=${q.id} reason=hardExcludeId sub=${q.subject}")
                continue
            }

            // Stage 2: hard exclude by content fingerprint
            if (fp.isNotBlank() && fp in snapshot.excludedFingerprints) {
                blockedByFingerprint++
                Log.w(TAG, "REPEAT_BLOCKED picker=$pickerTag id=${q.id} reason=hardExcludeFingerprint sub=${q.subject}")
                continue
            }

            // Stage 3: within-quiz dedup
            if (q.id in seenIds) {
                withinQuizDup++
                Log.w(TAG, "REPEAT_BLOCKED picker=$pickerTag id=${q.id} reason=withinQuizDupId")
                continue
            }
            if (fp.isNotBlank() && fp in seenFingerprints) {
                withinQuizDup++
                Log.w(TAG, "REPEAT_BLOCKED picker=$pickerTag id=${q.id} reason=withinQuizDupFingerprint")
                continue
            }

            seenIds.add(q.id)
            if (fp.isNotBlank()) seenFingerprints.add(fp)
            accepted.add(q)
        }

        // Stage 4: determine fallback stage
        val delivered = accepted.size
        val fallbackStage = when {
            delivered >= requested -> "FULL"
            delivered > 0 -> {
                Log.w(TAG, "REPEAT_LAST_RESORT picker=$pickerTag mode=shortage " +
                        "requested=$requested delivered=$delivered " +
                        "blockedById=$blockedById blockedByFingerprint=$blockedByFingerprint " +
                        "withinQuizDup=$withinQuizDup")
                "SHORTAGE"
            }
            else -> {
                Log.e(TAG, "REPEAT_LAST_RESORT picker=$pickerTag mode=empty " +
                        "requested=$requested all_blocked " +
                        "blockedById=$blockedById blockedByFingerprint=$blockedByFingerprint")
                "EMPTY"
            }
        }

        return EnforceReport(
            questions = accepted,
            requested = requested,
            delivered = delivered,
            blockedById = blockedById,
            blockedByFingerprint = blockedByFingerprint,
            withinQuizDupRemoved = withinQuizDup,
            fallbackStage = fallbackStage,
        )
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private fun sanitiseChoices(q: Question): Pair<Question, Boolean> {
        return try {
            doSanitise(q)
        } catch (e: Exception) {
            Log.e(TAG, "sanitise error id=${q.id}", e)
            q.copy(presentationChoices = q.presentationChoices ?: q.choices) to false
        }
    }

    private fun doSanitise(q: Question): Pair<Question, Boolean> {
        val correctIdx = q.correctIndex.coerceIn(0, 3)

        // Use presentationChoices when available — this is the primary fix for Bug-1A.
        // fixDistractors() writes its output to presentationChoices; QuizActivity was
        // reading q.choices and discarding it. This guard reads the right field and
        // ensures every downstream path receives a non-null presentationChoices.
        val raw = (q.presentationChoices ?: q.choices)
            .map { it.trim() }
            .toMutableList()

        // Pad to exactly 4 slots.
        while (raw.size < 4) raw.add("")
        val work = raw.take(4).toMutableList()

        var mutated = false

        // ── Pass 1: Replace blank / dash / single-char distractors ───────────
        // Only touches non-correct slots. Uses correct answer's numeric value
        // to generate plausible distractors instead of placeholder text.
        val correctText = work[correctIdx]
        for (i in work.indices) {
            if (i == correctIdx) continue
            if (work[i].isBlank() || work[i] == "-" || work[i].length < 2) {
                // Try to generate a numeric distractor based on correct answer
                val fixed = AdaptiveQuizRuntime.fixDistractors(work, correctText, correctIdx)
                if (fixed != null) {
                    for (j in work.indices) work[j] = fixed[j]
                    mutated = true
                    break // fixDistractors handles all slots at once
                }
                // If fixDistractors can't help, use a generic label (no suffix pattern)
                work[i] = "Seçenek ${('A'.code + i).toChar()}"
                mutated = true
            }
        }

        // ── Pass 2: Dedup — ensure all four choices are distinct ─────────────
        val seen = mutableSetOf(norm(work[correctIdx]))
        for (i in work.indices) {
            if (i == correctIdx) continue
            var n = norm(work[i])
            var attempt = 0
            while (n in seen && attempt < 4) {
                work[i] = work[i].trimEnd() + " v${attempt + 2}"
                n = norm(work[i])
                attempt++
                mutated = true
            }
            seen.add(n)
        }

        // Always write presentationChoices so callers can rely on it being non-null.
        return q.copy(presentationChoices = work) to mutated
    }

    /** Case-insensitive, trim-normalised comparison key. */
    private fun norm(s: String): String =
        s.lowercase(Locale("tr")).trim().replace(Regex("\\s{2,}"), " ")
}
