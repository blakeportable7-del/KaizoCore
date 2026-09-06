package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun `unknown or unused type ids give nothing`() {
        assertNull(MoveMatchup.general(null))
        assertNull(MoveMatchup.general(9))      // the unused Mystery slot
        assertNull(MoveMatchup.general(99))
    }
}
