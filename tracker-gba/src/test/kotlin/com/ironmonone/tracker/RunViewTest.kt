package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The GBA state answers the screen's four questions through RunView. */
class RunViewTest {

    private fun enemy(species: Int) = EnemyInfo(
        species = species, speciesName = "x", level = 5,
        curHp = 10, maxHp = 10, type1 = 0, type2 = 0,
        base = null, movesSeen = emptyList(), moveRows = emptyList(),
    )

    @Test
    fun `opponent id and battle flags pass through`() {
        val s: RunView = TrackerState(
            partyCount = 1, party = emptyList(),
            inBattle = true, isWildBattle = true, enemy = enemy(793),
        )
        assertEquals(true, s.inBattle)
        assertEquals(true, s.isWildBattle)
        assertEquals(793, s.enemySpeciesId)
    }

    @Test
    fun `no opponent is -1, not zero`() {
        val s: RunView = TrackerState(partyCount = 1, party = emptyList(),
            inBattle = false, isWildBattle = false)
        assertEquals(-1, s.enemySpeciesId)
    }

    @Test
    fun `game over maps onto the shared outcome`() {
        fun with(g: GameOver?): RunOutcome? = TrackerState(
            partyCount = 1, party = emptyList(), inBattle = false, isWildBattle = false,
            gameOver = g,
        ).outcome
        assertEquals(RunOutcome.WON, with(GameOver.WON))
        assertEquals(RunOutcome.LOST, with(GameOver.LOST))
        assertNull(with(null))
    }
}
