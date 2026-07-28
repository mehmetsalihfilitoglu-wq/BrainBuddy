package com.edumio.app.dailychallenge

import com.edumio.app.db.QuestionCandidateRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract for injecting due scheduled reviews into the Daily Challenge.
 *
 * The challenge stays EXACTLY five questions: a due review REPLACES a newly selected question from the
 * SAME section, so the blueprint's section distribution — and therefore the deficit accounting — is
 * untouched. The displaced question is merely not shown; because retirement happens at answer time
 * (see DailyChallengeEngine.submitAnswer), it is never marked exposed and stays fully eligible later.
 *
 * injectDueReviews is pure, so the whole contract is provable without a device.
 */
class ScheduledReviewInjectionTest {

    private fun q(id: String, section: String) = QuestionCandidateRow(
        id = id, subject = section, difficulty = 2, grade = 12, stemHash = "h_$id",
        stemNormalized = id, type = "MCQ", skill = "", topic = "t_$id",
        qualityTier = "MEDIUM", reasoningLevel = 1,
    )

    private fun due(id: String, section: String, at: Long) =
        DailyChallengeSelection.DueReview(id, section, at)

    /** A five-question challenge: 3 biology, 2 chemistry. */
    private fun selection() = listOf(
        q("n1", "biology"), q("n2", "chemistry"), q("n3", "biology"),
        q("n4", "chemistry"), q("n5", "biology"),
    )

    @Test
    fun noDueReviewsChangesNothing() {
        val r = DailyChallengeSelection.injectDueReviews(selection(), emptyList())
        assertEquals(listOf("n1", "n2", "n3", "n4", "n5"), r.orderedIds)
        assertTrue(r.injectedIds.isEmpty())
        assertTrue(r.displacedIds.isEmpty())
    }

    @Test
    fun oneDueReviewInjectsExactlyOne() {
        val r = DailyChallengeSelection.injectDueReviews(selection(), listOf(due("r1", "biology", 10)))
        assertEquals("length is invariant", 5, r.orderedIds.size)
        assertEquals(listOf("r1"), r.injectedIds)
        assertEquals("exactly one new question displaced", 1, r.displacedIds.size)
        assertTrue("displaced one must be biology", r.displacedIds.single() in listOf("n1", "n3", "n5"))
    }

    @Test
    fun neverMoreThanTwoAreInjected() {
        val many = listOf(
            due("r1", "biology", 10), due("r2", "biology", 20),
            due("r3", "biology", 30), due("r4", "chemistry", 40),
        )
        val r = DailyChallengeSelection.injectDueReviews(selection(), many)
        assertEquals(5, r.orderedIds.size)
        assertEquals("cap of two is absolute", 2, r.injectedIds.size)
        assertEquals("the rest are deferred, not dropped", 2, r.deferredIds.size)
    }

    @Test
    fun oldestDueWinsAContestedSlot() {
        // Only ONE chemistry slot pair exists; the oldest-due chemistry review must take it first.
        val r = DailyChallengeSelection.injectDueReviews(
            listOf(q("n1", "biology"), q("n2", "chemistry"), q("n3", "biology")),
            listOf(due("newer", "chemistry", 900), due("older", "chemistry", 100)),
            maxInjected = 1,
        )
        assertEquals(listOf("older"), r.injectedIds)
        assertTrue("the newer one waits its turn", r.deferredIds.contains("newer"))
    }

    @Test
    fun aReviewOnlyEverReplacesItsOwnSection() {
        val r = DailyChallengeSelection.injectDueReviews(selection(), listOf(due("rChem", "chemistry", 10)))
        assertTrue(r.injectedIds.contains("rChem"))
        assertTrue(
            "a chemistry review must displace a chemistry question, never biology",
            r.displacedIds.single() in listOf("n2", "n4"),
        )
    }

    @Test
    fun noMatchingSectionMeansDeferredNotForced() {
        val r = DailyChallengeSelection.injectDueReviews(selection(), listOf(due("rPhys", "physics", 10)))
        assertEquals("nothing injected", 0, r.injectedIds.size)
        assertEquals("nothing displaced", 0, r.displacedIds.size)
        assertEquals(listOf("rPhys"), r.deferredIds)
        assertEquals("the five stand unchanged", listOf("n1", "n2", "n3", "n4", "n5"), r.orderedIds)
    }

    @Test
    fun sectionDistributionIsPreserved() {
        val sel = selection()
        val before = sel.groupingBy { it.subject }.eachCount()
        val r = DailyChallengeSelection.injectDueReviews(
            sel, listOf(due("rBio", "biology", 10), due("rChem", "chemistry", 20)),
        )
        // Reconstruct each final slot's section: injected reviews carry their own section, which by
        // construction equals the section of the slot they replaced.
        val after = r.orderedIds.mapIndexed { i, _ -> sel[i].subject }.groupingBy { it }.eachCount()
        assertEquals("the blueprint's section quotas must be untouched", before, after)
    }

    @Test
    fun anAlreadySelectedQuestionIsNeverDuplicated() {
        val r = DailyChallengeSelection.injectDueReviews(selection(), listOf(due("n3", "biology", 10)))
        assertEquals("n3 is already in the five — skip it", 0, r.injectedIds.size)
        assertTrue(r.deferredIds.contains("n3"))
        assertEquals("no duplicate ids", r.orderedIds.size, r.orderedIds.toSet().size)
    }

    @Test
    fun finalIdsAreAlwaysUnique() {
        val r = DailyChallengeSelection.injectDueReviews(
            selection(), listOf(due("r1", "biology", 10), due("r2", "chemistry", 20)),
        )
        assertEquals(5, r.orderedIds.size)
        assertEquals("no id may appear twice", 5, r.orderedIds.toSet().size)
    }

    @Test
    fun twoInjectedReviewsAreNotPlacedConsecutively() {
        // Three biology slots at indices 0,2,4 — separation is achievable.
        val r = DailyChallengeSelection.injectDueReviews(
            selection(), listOf(due("r1", "biology", 10), due("r2", "biology", 20)),
        )
        assertEquals(2, r.injectedIds.size)
        val positions = r.orderedIds.indices.filter { r.orderedIds[it] in r.injectedIds }
        assertEquals(2, positions.size)
        assertTrue(
            "injected reviews must not be adjacent when the layout allows separation (was $positions)",
            kotlin.math.abs(positions[0] - positions[1]) > 1,
        )
    }

    @Test
    fun displacedQuestionsAreReportedSoTheyCanStayEligible() {
        val r = DailyChallengeSelection.injectDueReviews(selection(), listOf(due("r1", "biology", 10)))
        val displaced = r.displacedIds.single()
        assertFalse("a displaced question must not be served today", r.orderedIds.contains(displaced))
        // It is only omitted from today's five. Nothing here exposes or retires it, and the engine
        // records exposure exclusively at answer time — so it remains selectable tomorrow.
        assertTrue("still a real question id", displaced.startsWith("n"))
    }

    // ── candidate selection must survey ALL due reviews, not just the oldest few ─────────────────

    @Test
    fun twoIneligibleOlderReviewsDoNotHideAnEligibleThirdOne() {
        // The exact reported defect: capping the candidate list at two (e.g. in SQL) meant two older
        // Geometry reviews — with no Geometry slot today — consumed both candidate places and NOTHING
        // was injected, even though a Mathematics review was eligible.
        val sel = listOf(
            q("n1", "mathematics"), q("n2", "biology"), q("n3", "mathematics"),
            q("n4", "biology"), q("n5", "biology"),
        )
        val due = listOf(
            due("gOld", "geometry", 100),      // oldest, no slot today
            due("gOlder2", "geometry", 200),   // also no slot
            due("mathOk", "mathematics", 300), // eligible
        )
        val r = DailyChallengeSelection.injectDueReviews(sel, due)
        assertTrue("the eligible third review must still be injected", r.injectedIds.contains("mathOk"))
        assertEquals(5, r.orderedIds.size)
        assertTrue(r.deferredIds.containsAll(listOf("gOld", "gOlder2")))
    }

    @Test
    fun anIneligibleOlderReviewNeverBlocksAnEligibleNewerOne() {
        val sel = selection() // 3 biology, 2 chemistry
        val due = listOf(
            due("physOldest", "physics", 1),      // never eligible
            due("bioNewer", "biology", 5_000),    // eligible
        )
        val r = DailyChallengeSelection.injectDueReviews(sel, due)
        assertEquals(listOf("bioNewer"), r.injectedIds)
        assertEquals(listOf("physOldest"), r.deferredIds)
    }

    @Test
    fun theTwoOldestELIGIBLEReviewsAreInjectedAcrossSections() {
        val sel = selection()
        val due = listOf(
            due("noSlotA", "physics", 10),     // ineligible
            due("bioOldest", "biology", 20),   // eligible, oldest eligible
            due("chemNext", "chemistry", 30),  // eligible, second oldest eligible
            due("bioLater", "biology", 40),    // eligible but beyond the cap
        )
        val r = DailyChallengeSelection.injectDueReviews(sel, due)
        assertEquals("oldest-eligible-first, capped at two", listOf("bioOldest", "chemNext"), r.injectedIds)
        assertTrue(r.deferredIds.containsAll(listOf("noSlotA", "bioLater")))
    }

    @Test
    fun neverReplacesMoreSlotsThanTheSectionActuallyHas() {
        // Only ONE chemistry slot exists; three chemistry reviews are due.
        val sel = listOf(q("n1", "biology"), q("n2", "chemistry"), q("n3", "biology"))
        val due = listOf(
            due("c1", "chemistry", 10), due("c2", "chemistry", 20), due("c3", "chemistry", 30),
        )
        val r = DailyChallengeSelection.injectDueReviews(sel, due)
        assertEquals("only the single chemistry slot can be replaced", listOf("c1"), r.injectedIds)
        assertEquals(1, r.displacedIds.size)
        assertEquals("n2", r.displacedIds.single())
        assertTrue(r.deferredIds.containsAll(listOf("c2", "c3")))
        assertEquals(3, r.orderedIds.size)
    }

    @Test
    fun spreadPlacementNeverAltersSectionCountsOrEligibility() {
        // pickSpreadSlot may move WHICH same-section slot is taken, but must never take a slot from a
        // different section, so section counts are invariant however placement resolves.
        val sel = selection()
        val r = DailyChallengeSelection.injectDueReviews(
            sel, listOf(due("rBio1", "biology", 10), due("rBio2", "biology", 20)),
        )
        assertEquals(2, r.injectedIds.size)
        // Every displaced question must be biology — the injected reviews' own section.
        assertTrue(
            "spread placement must not cross sections (displaced=${r.displacedIds})",
            r.displacedIds.all { id -> sel.first { it.id == id }.subject == "biology" },
        )
        val before = sel.groupingBy { it.subject }.eachCount()
        val after = r.orderedIds.mapIndexed { i, _ -> sel[i].subject }.groupingBy { it }.eachCount()
        assertEquals(before, after)
    }

    @Test
    fun injectionIsDeterministic() {
        val d = listOf(due("r1", "biology", 10), due("r2", "chemistry", 20))
        val a = DailyChallengeSelection.injectDueReviews(selection(), d)
        val b = DailyChallengeSelection.injectDueReviews(selection(), d)
        assertEquals(a.orderedIds, b.orderedIds)
        assertEquals(a.injectedIds, b.injectedIds)
    }
}
