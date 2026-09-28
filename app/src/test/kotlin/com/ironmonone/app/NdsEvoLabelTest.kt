package com.ironmonone.app

import com.ironmonone.tracker.EvoText
import com.ironmonone.tracker.nds.Gen4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** MainScreen.setUpEvo on the DS card: the method in brackets, and the friendship bar on your own Pokemon. */
class NdsEvoLabelTest {
    private fun mon(species: Int, friendship: Int, female: Boolean = false) =
        Gen4.decodeParty(Gen4.encodeParty(0x1234L, species, 20, 40, 40, listOf(1, 0, 0, 0), friendship = friendship, female = female))!!

    @Test
    fun `a friendship evolver fills toward 220 from its base`() {
        val half = ndsEvoLabel(mon(42, 145), own = true)            // Golbat, base 70: (145-70)/150
        assertEquals("FRIEND", half.text)
        assertEquals(0.5f, half.fill!!, 0.001f)
        val ready = ndsEvoLabel(mon(42, 220), own = true)
        assertEquals("READY", ready.text); assertEquals(EvoText.Tone.READY, ready.tone)
    }

    @Test
    fun `no bar on the enemy, levels and none as the reference writes them`() {
        val enemy = ndsEvoLabel(mon(42, 145), own = false)
        assertEquals("FRIEND", enemy.text); assertNull(enemy.fill)
        assertEquals("16", ndsEvoLabel(mon(1, 70), own = true).text)
        assertEquals("---", ndsEvoLabel(mon(3, 70), own = true).text)
        assertEquals("30", ndsEvoLabel(mon(281, 70, female = true), own = true).text)
    }
}
