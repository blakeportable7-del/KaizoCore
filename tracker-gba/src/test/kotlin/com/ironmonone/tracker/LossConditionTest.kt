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

    @Test
    fun `a run's settings file picks its condition, as the reference's profiles do`() {
        // QuickloadScreen.SettingsKeywordToGameOverMap over every bundled settings file name, but
        // Kaizo Doubles: the reference's keyword map gives it the lead, the rules give it either of
        // the two (Blake, 2026-09-29: the most recent rules).
        val dir = java.io.File("../app/src/main/assets/presets")
        val names = dir.listFiles()?.map { it.name }.orEmpty()
        kotlin.test.assertTrue(names.size > 30, "presets not found at ${dir.absolutePath}")
        for (n in names) {
            val want = when {
                n.contains("Doubles") -> LossCondition.EITHER_OF_FIRST_TWO
                // RBY Survival's HM friend may lead a gym and faint (IronMON rules check R9, 2026-09-30).
                n == "RBY Survival.rnqs" -> LossCondition.HIGHEST_LEVEL
                n.contains("Standard") || n.contains("Ultimate") -> LossCondition.ENTIRE_PARTY
                else -> LossCondition.LEAD   // Kaizo, Super Kaizo, Survival, PART 2, anything else
            }
            kotlin.test.assertEquals(want, LossCondition.forSettingsName(n), n)
        }
        kotlin.test.assertEquals(LossCondition.ENTIRE_PARTY, LossCondition.forSettingsName("my ultimate run.rnqs"))
    }

    @Test
    fun `eggs never count`() {
        val egg = LossMon(5, 0, isEgg = true)
        // The lead is the first Pokemon that is not an egg (Tracker.getPokemon skips eggs, Tracker.lua:105-140): an egg
        // in slot 1 is never a fainted lead, and the Pokemon after it is the lead (rc32 audit P2 #136).
        kotlin.test.assertFalse(LossCondition.LEAD.lostMons(listOf(egg, LossMon(20, 30))))
        kotlin.test.assertTrue(LossCondition.LEAD.lostMons(listOf(LossMon(5, 40, isEgg = true), LossMon(20, 0))))
        // Entire party: every REAL Pokemon down, the egg does not keep the run alive.
        kotlin.test.assertTrue(LossCondition.ENTIRE_PARTY.lostMons(listOf(LossMon(20, 0), LossMon(5, 40, isEgg = true))))
        // Highest level: a higher-level egg is not the highest Pokemon.
        kotlin.test.assertTrue(LossCondition.HIGHEST_LEVEL.lostMons(listOf(LossMon(30, 0), LossMon(40, 10, isEgg = true))))
        // Only eggs: nothing to lose.
        kotlin.test.assertFalse(LossCondition.ENTIRE_PARTY.lostMons(listOf(egg)))
    }

    @Test
    fun `Kaizo Doubles ends when either of the first two faints`() {
        val d = LossCondition.EITHER_OF_FIRST_TWO
        assertTrue(d.lost(leadDown), "the lead")
        assertTrue(d.lost(listOf(20 to 30, 18 to 0, 25 to 40)), "the second")
        assertFalse(d.lost(listOf(20 to 30, 18 to 10, 25 to 0)), "a third Pokemon (an HM friend) is not one of the two")
        assertFalse(LossCondition.LEAD.lost(listOf(20 to 30, 18 to 0)), "the lead rule would miss the second")
        // Eggs are skipped, as for every condition: the two are the first two real Pokemon.
        assertTrue(d.lostMons(listOf(LossMon(5, 20, isEgg = true), LossMon(20, 30), LossMon(18, 0))))
    }

    @Test
    fun `a Doubles settings file starts on the doubles rule, before its Kaizo keyword`() {
        assertEquals(LossCondition.EITHER_OF_FIRST_TWO, LossCondition.forSettingsName("RSE Kaizo Doubles.rnqs"))
        assertEquals(LossCondition.EITHER_OF_FIRST_TWO, LossCondition.forSettingsName("B2W2 Kaizo Doubles.rnqs"))
        assertEquals(LossCondition.LEAD, LossCondition.forSettingsName("FRLG Kaizo.rnqs"))
        assertEquals(LossCondition.ENTIRE_PARTY, LossCondition.forSettingsName("HGSS Standard.rnqs"))
    }
}
