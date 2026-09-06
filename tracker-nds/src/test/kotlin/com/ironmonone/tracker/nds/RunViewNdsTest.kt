package com.ironmonone.tracker.nds

import com.ironmonone.tracker.RunOutcome
import com.ironmonone.tracker.RunView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The DS state answers the same four questions as the GBA one.
 *
 * Every DS run-over reason except WON is a loss: Shedinja, imposter and the
 * BST rule all end the run, and the screen only needs to know that it ended.
 */
class RunViewNdsTest {

    private fun state(runOver: NdsRunOver? = null, inBattle: Boolean = false, wild: Boolean = false): RunView =
        NdsTrackerState(
            partyCount = 0, party = emptyList(), located = true,
            inBattle = inBattle, isWildBattle = wild, runOver = runOver,
        )

    @Test
    fun `flags pass through and no opponent is -1`() {
        val s = state(inBattle = true, wild = true)
        assertEquals(true, s.inBattle)
        assertEquals(true, s.isWildBattle)
        assertEquals(-1, s.enemySpeciesId)
    }

    @Test
    fun `every run-over reason but WON is a loss`() {
        assertEquals(RunOutcome.WON, state(NdsRunOver.WON).outcome)
        for (r in NdsRunOver.entries.filter { it != NdsRunOver.WON }) {
            assertEquals(RunOutcome.LOST, state(r).outcome, "$r should read as a loss")
        }
        assertNull(state(null).outcome)
    }
}
