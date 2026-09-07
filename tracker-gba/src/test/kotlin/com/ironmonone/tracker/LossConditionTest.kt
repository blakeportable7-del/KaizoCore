package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** GameOverScreen.LossConditions, one case per rule, plus the undecoded-slot guard. */
class LossConditionTest {
    private val leadDown = listOf(20 to 0, 18 to 30, 25 to 40)      // lead fainted, highest level (25) alive
    private val highestDown = listOf(20 to 15, 18 to 30, 25 to 0)   // lead alive, highest level fainted
    private val allDown = listOf(20 to 0, 18 to 0, 25 to 0)

    @Test
    fun `lead faints`() {
        assertTrue(LossCondition.LEAD.lost(leadDown))
        assertFalse(LossCondition.LEAD.lost(highestDown))
        assertTrue(LossCondition.LEAD.lost(allDown))
    }

    @Test
    fun `highest level faints, ties count`() {
        assertFalse(LossCondition.HIGHEST_LEVEL.lost(leadDown))
        assertTrue(LossCondition.HIGHEST_LEVEL.lost(highestDown))
        assertTrue(LossCondition.HIGHEST_LEVEL.lost(listOf(25 to 30, 25 to 0)), "a tie for highest that faints ends it")
    }

    @Test
    fun `entire party faints`() {
        assertFalse(LossCondition.ENTIRE_PARTY.lost(leadDown))
        assertFalse(LossCondition.ENTIRE_PARTY.lost(highestDown))
        assertTrue(LossCondition.ENTIRE_PARTY.lost(allDown))
    }

    @Test
    fun `an undecoded slot is not a faint, and an empty party is not a loss`() {
        for (c in LossCondition.entries) {
            assertFalse(c.lost(listOf(0 to 0)))
            assertFalse(c.lost(emptyList()))
        }
        assertEquals(LossCondition.LEAD, LossCondition.byKey("nonsense"))
        assertEquals(LossCondition.ENTIRE_PARTY, LossCondition.byKey("EntirePartyFaints"))
    }
}
