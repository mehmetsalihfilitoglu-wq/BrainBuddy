package com.edumio.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The learning-statistics card must never render placeholder dashes.
 *
 * Before: when no data existed the card still drew its three values as "—" (Accuracy / Tests /
 * Questions), which users read as a broken screen. Now the card has two explicit states and the
 * decision is made from the DATA, never hardcoded — so the empty state disappears by itself the
 * moment real statistics exist.
 */
class StudyHubStatsStateTest {

    // ── the state decision itself ───────────────────────────────────────────────────────────────

    @Test
    fun noAnsweredQuestionsMeansEmpty() {
        assertEquals(
            StudyHubActivity.StatsState.EMPTY,
            StudyHubActivity.statsState(answeredQuestions = 0),
        )
    }

    @Test
    fun theCardSwitchesToLoadedAsSoonAsThereIsOneRealAnswer() {
        // Deliberately not a hardcoded "10 tests" gate: the card lights up when there is something
        // true to show, and never before.
        for (n in 1..50) {
            assertEquals(
                "answeredQuestions=$n must render real statistics",
                StudyHubActivity.StatsState.LOADED,
                StudyHubActivity.statsState(n),
            )
        }
    }

    @Test
    fun negativeOrCorruptCountsFallBackToEmptyRatherThanShowingNonsense() {
        assertEquals(StudyHubActivity.StatsState.EMPTY, StudyHubActivity.statsState(-1))
        assertEquals(StudyHubActivity.StatsState.EMPTY, StudyHubActivity.statsState(Int.MIN_VALUE))
    }

    // ── the shipped UI ──────────────────────────────────────────────────────────────────────────

    private fun res(): File = listOf(File("src/main/res"), File("app/src/main/res"), File("../app/src/main/res"))
        .firstOrNull { it.isDirectory } ?: error("res dir not found (cwd=${File(".").absolutePath})")

    private fun layout(): String = File(res(), "layout/activity_study_hub.xml").readText(Charsets.UTF_8)

    @Test
    fun bothStatesExistInTheLayout() {
        val xml = layout()
        assertTrue("the LOADED value row must be addressable", xml.contains("@+id/statsRow"))
        assertTrue("the EMPTY state must exist", xml.contains("@+id/statsEmpty"))
        assertTrue("the empty state needs its call to action", xml.contains("@+id/btnStatsEmptyCta"))
    }

    @Test
    fun theStatisticsCardShipsNoPlaceholderDashes() {
        // The three stat values must not be seeded with "—"/"-" in XML either: a dash flashing before
        // the state is applied is the same bug the user reported.
        val xml = layout()
        val statValueIds = listOf("tvStatsAccuracy", "tvStatsTests", "tvStatsQuestions")
        for (id in statValueIds) {
            val block = xml.substringAfter("@+id/$id").substringBefore("/>")
            assertFalse(
                "$id must not declare a placeholder dash as its text (was: ${block.trim().take(160)})",
                Regex("""android:text\s*=\s*"[-–—]"""").containsMatchIn(block),
            )
        }
    }

    @Test
    fun noSourceCodePathAssignsADashToAStatValue() {
        val src = File(res().parentFile, "java/com/edumio/app/ui/StudyHubActivity.kt").readText(Charsets.UTF_8)
        assertFalse(
            "StudyHubActivity must not write a dash into a stat view",
            Regex("""\.text\s*=\s*"[-–—]"""").containsMatchIn(src),
        )
        // And it must not invent a zero either — a fake "0%" is as misleading as a dash.
        assertFalse(
            "StudyHubActivity must not fabricate a zero statistic",
            Regex("""tvStatsAccuracy\.text\s*=\s*"%?0%?"""").containsMatchIn(src),
        )
    }

    @Test
    fun theEmptyStateCopyExistsAndPromisesOnlyWhatTheRealFlowDelivers() {
        val strings = File(res(), "values/strings.xml").readText(Charsets.UTF_8)
        for (key in listOf(
            "study_hub_stats_empty_title", "study_hub_stats_empty_body", "study_hub_stats_empty_cta",
        )) {
            assertTrue("missing empty-state string '$key'", strings.contains("name=\"$key\""))
        }
        val body = strings.substringAfter("name=\"study_hub_stats_empty_body\">").substringBefore("</string>")

        // The promise must be tied to the ONE flow that actually produces statistics — completing a
        // daily challenge. The Daily Challenge now writes them (see DailyChallengeStatsRecorder), so
        // this is a promise the app can keep.
        assertTrue(
            "the empty state must point at the daily challenge, the only flow that writes stats: '$body'",
            body.contains("günlük görev", ignoreCase = true),
        )
        // Guard against re-introducing a numeric threshold ("after 10 tests…"): the card lights up on
        // the FIRST completed challenge, and promising a count would be false.
        assertFalse(
            "the empty state must not promise a specific test threshold: '$body'",
            Regex("""\d+\s*(test|görev)""", RegexOption.IGNORE_CASE).containsMatchIn(body),
        )
    }
}
