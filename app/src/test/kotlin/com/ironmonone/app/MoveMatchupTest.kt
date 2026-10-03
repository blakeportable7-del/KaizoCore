package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The popup's type-chart lines are general facts about the move's type.
 * Gen 3 ids: Normal 0, Fighting 1, Ghost 7, Water 11, Electric 13, Psychic 14, Dark 17.
 */
class MoveMatchupTest {

    @Test
    fun `Water hits Fire Ground Rock and is resisted by Water Grass Dragon`() {
        val g = MoveMatchup.general(11)!!
        assertEquals(listOf("Fire", "Ground", "Rock"), g.strongAgainst.sorted())
        assertEquals(listOf("Dragon", "Grass", "Water"), g.resistedBy.sorted())
        assertEquals(emptyList(), g.noEffectOn)
    }

    @Test
    fun `immunities are listed, not folded into resisted`() {
        assertEquals(listOf("Ghost"), MoveMatchup.general(0)!!.noEffectOn)     // Normal
        assertEquals(listOf("Ghost"), MoveMatchup.general(1)!!.noEffectOn)     // Fighting
        assertEquals(listOf("Ground"), MoveMatchup.general(13)!!.noEffectOn)   // Electric
        assertEquals(listOf("Dark"), MoveMatchup.general(14)!!.noEffectOn)     // Psychic
    }

    /** The Gen 1 tracker's chart, for Red, Blue and Yellow's move info. */
    @Test
    fun `in Gen 1's chart Ghost does nothing to Psychic, and Poison and Bug hit each other hard`() {
        assertEquals(listOf("Normal", "Psychic"), MoveMatchup.general(7, gen1 = true)!!.noEffectOn)
        assertTrue("Psychic" in MoveMatchup.general(7)!!.strongAgainst)
        assertTrue("Bug" in MoveMatchup.general(3, gen1 = true)!!.strongAgainst)
        assertTrue("Poison" in MoveMatchup.general(6, gen1 = true)!!.strongAgainst)
        assertTrue("Poison" in MoveMatchup.general(6)!!.resistedBy)
    }

    @Test
    fun `unknown or unused type ids give nothing`() {
        assertNull(MoveMatchup.general(null))
        assertNull(MoveMatchup.general(9))      // the unused Mystery slot
        assertNull(MoveMatchup.general(99))
    }

    @Test
    fun `on the Nat Dex expansion Fairy moves have a chart, and Steel takes Ghost and Dark at 1x`() {
        // rc33 audit P1 #70: a Fairy move's info had no chart lines at all, and Ghost and Dark read as resisted by Steel.
        val fairy = MoveMatchup.general(18, natDex = true)!!
        assertEquals(listOf("Dark", "Dragon", "Fighting"), fairy.strongAgainst)
        assertEquals(listOf("Fire", "Poison", "Steel"), fairy.resistedBy)
        assertNull(MoveMatchup.general(18), "no Fairy outside the expansion")
        assertTrue("Fairy" in MoveMatchup.general(16, natDex = true)!!.noEffectOn)
        assertTrue("Fairy" in MoveMatchup.general(3, natDex = true)!!.strongAgainst)
        kotlin.test.assertFalse("Steel" in MoveMatchup.general(7, natDex = true)!!.resistedBy)
        kotlin.test.assertFalse("Steel" in MoveMatchup.general(17, natDex = true)!!.resistedBy)
        assertTrue("Steel" in MoveMatchup.general(7)!!.resistedBy)
    }
}
