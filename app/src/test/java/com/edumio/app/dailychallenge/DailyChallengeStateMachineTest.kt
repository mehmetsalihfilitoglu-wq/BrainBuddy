package com.edumio.app.dailychallenge

import com.edumio.app.dailychallenge.DailyChallengeHomePresenter.FlowState
import com.edumio.app.dailychallenge.DailyChallengeHomePresenter.ListState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Release-blocker guards for the two state machines that were reporting SUCCESS for FAILURE.
 *
 * 1) A question-bank load failure was shown as "Bugünlük yeni soru kalmadı" — a genuine failure read
 *    to the student like a normal, finished day.
 * 2) A DAO/database exception in the wrong-question hub was swallowed into an empty list and rendered
 *    as "Aktif yanlış sorun yok. Böyle devam!" — the app congratulated the student on data it could
 *    not read.
 *
 * AVAILABLE / COMPLETED / LOADING / ERROR (and EMPTY vs ERROR) must never be confused again.
 */
class DailyChallengeStateMachineTest {

    // ── Blocker #1: the answering-screen flow state ────────────────────────────────────────────────

    @Test
    fun questionBankFailure_isERROR_neverCompletedOrAvailable() {
        val s = DailyChallengeHomePresenter.flowState(
            loadFailed = true, hasChallenge = false, answered = 0, total = 0, completed = false,
        )
        assertEquals(FlowState.ERROR, s)
        assertNotEquals("a load failure must never read as a finished day", FlowState.COMPLETED, s)
        assertNotEquals(FlowState.AVAILABLE, s)
    }

    @Test
    fun missingChallenge_isERROR_evenWithoutAnException() {
        // today() returning null (nothing could be built) is still a failure to serve, not "done".
        val s = DailyChallengeHomePresenter.flowState(
            loadFailed = false, hasChallenge = false, answered = 0, total = 5, completed = false,
        )
        assertEquals(FlowState.ERROR, s)
        assertNotEquals(FlowState.COMPLETED, s)
    }

    @Test
    fun questionBankSuccess_isAVAILABLE() {
        val s = DailyChallengeHomePresenter.flowState(
            loadFailed = false, hasChallenge = true, answered = 0, total = 5, completed = false,
        )
        assertEquals(FlowState.AVAILABLE, s)
    }

    @Test
    fun partiallyAnswered_isStillAVAILABLE_notCompleted() {
        for (answered in 1..4) {
            val s = DailyChallengeHomePresenter.flowState(
                loadFailed = false, hasChallenge = true, answered = answered, total = 5, completed = false,
            )
            assertEquals("$answered/5 must not be COMPLETED", FlowState.AVAILABLE, s)
        }
    }

    @Test
    fun completed_onlyAfterAllFiveAnswered() {
        val byCount = DailyChallengeHomePresenter.flowState(
            loadFailed = false, hasChallenge = true, answered = 5, total = 5, completed = false,
        )
        val byFlag = DailyChallengeHomePresenter.flowState(
            loadFailed = false, hasChallenge = true, answered = 5, total = 5, completed = true,
        )
        assertEquals(FlowState.COMPLETED, byCount)
        assertEquals(FlowState.COMPLETED, byFlag)
    }

    @Test
    fun failureWins_evenIfAChallengeObjectIsPresent() {
        // If the load threw, we must not trust any partial result we happen to hold.
        val s = DailyChallengeHomePresenter.flowState(
            loadFailed = true, hasChallenge = true, answered = 5, total = 5, completed = true,
        )
        assertEquals(FlowState.ERROR, s)
    }

    @Test
    fun loadingIsADistinctState() {
        // LOADING is modelled explicitly so "preparing" can never be drawn as an error or an empty day.
        assertEquals(4, FlowState.values().size)
        assertNotEquals(FlowState.LOADING, FlowState.ERROR)
        assertNotEquals(FlowState.LOADING, FlowState.AVAILABLE)
        assertNotEquals(FlowState.LOADING, FlowState.COMPLETED)
    }

    // ── Blocker #2: the wrong-question list state ──────────────────────────────────────────────────

    @Test
    fun daoException_isERROR_neverEmpty() {
        val s = DailyChallengeHomePresenter.listState(loadFailed = true, itemCount = 0)
        assertEquals(ListState.ERROR, s)
        assertNotEquals("a database failure must never read as an empty pool", ListState.EMPTY, s)
    }

    @Test
    fun daoException_isERROR_evenIfSomeRowsWereAlreadyHeld() {
        assertEquals(ListState.ERROR, DailyChallengeHomePresenter.listState(loadFailed = true, itemCount = 7))
    }

    @Test
    fun emptyList_isEMPTY() {
        assertEquals(ListState.EMPTY, DailyChallengeHomePresenter.listState(loadFailed = false, itemCount = 0))
    }

    @Test
    fun nonEmptyList_isCONTENT() {
        assertEquals(ListState.CONTENT, DailyChallengeHomePresenter.listState(loadFailed = false, itemCount = 1))
        assertEquals(ListState.CONTENT, DailyChallengeHomePresenter.listState(loadFailed = false, itemCount = 42))
    }

    @Test
    fun listLoadingIsADistinctState() {
        assertEquals(4, ListState.values().size)
        assertNotEquals(ListState.LOADING, ListState.EMPTY)
        assertNotEquals(ListState.LOADING, ListState.ERROR)
    }
}
