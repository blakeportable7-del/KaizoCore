package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The DS reference reads friendship at block A +0x0C and the female bit at block B +0x18 bit 1. */
class NdsFriendshipTest {
    @Test
    fun `friendship and gender decode from the party entry`() {
        val e = Gen4.encodeParty(pid = 0x12345678L, species = 42, level = 22, curHp = 50, maxHp = 60,
            moves = listOf(1, 0, 0, 0), friendship = 180, female = true)
        val m = Gen4.decodeParty(e)!!
        assertEquals(180, m.friendship)
        assertTrue(m.isFemale)
        assertFalse(Gen4.decodeParty(Gen4.encodeParty(0x12345678L, 42, 22, 50, 60, listOf(1, 0, 0, 0)))!!.isFemale)
    }

    @Test
    fun `evolution and base friendship come from the reference's data`() {
        assertEquals("FRIEND", NdsLogData.evoFor(42, female = false))   // Golbat
        assertEquals(70, NdsLogData.baseFriendship(42))
        assertEquals(0, NdsLogData.baseFriendship(1), "set only on friendship evolvers")
        assertEquals("30/DWN", NdsLogData.evoFor(281, female = false))   // Kirlia: {male, female}
        assertEquals("30", NdsLogData.evoFor(281, female = true))
        assertEquals("", NdsLogData.evoFor(415, female = false))          // Combee: male does not evolve
        assertEquals("21 F", NdsLogData.evoFor(415, female = true))
        assertEquals("16", NdsLogData.evoMethod(1))
    }
}
