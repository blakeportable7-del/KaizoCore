package com.ironmonone.tracker.nuzlocke

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How a Pokemon is named in the ledger (emulator QA, 2026-09-29). A Gen 3 Pokemon with no nickname of its own carries
 * its species name in capitals as its nickname, so "a nickname that is the species name" is no nickname: the ledger
 * printed "Treecko (Treecko Lv 7)", and on a real game it would have printed "ZIGZAGOON (Zigzagoon Lv 3)".
 */
class NuzlockeNamesTest {

    private fun texts(s: Sim) = s.ledger.events.joinToString("\n") { it.text }

    @Test
    fun `the game's default nickname is no nickname, and the name is not printed twice`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3, nickname = "PIDGEY"))
        val caught = s.ledger.roster.getValue(100L)
        assertFalse(caught.hasNickname)
        assertEquals("Pidgey", caught.shownName)
        assertEquals("Pidgey Lv 3", caught.label(3))
        assertFalse("PIDGEY (" in texts(s) || "Pidgey (Pidgey" in texts(s), texts(s))
        assertTrue(s.ledger.openWarnings.any { it.text == "Pidgey has no nickname." }, "the nickname rule still asks for one")
    }

    @Test
    fun `a real nickname leads, with the species and level after it`() {
        val s = Sim().starter()
        s.catchIt(foe(100, Sp.PIDGEY, "PIDGEY", 3), mon(100, Sp.PIDGEY, "PIDGEY", 3, nickname = "Peck"))
        val caught = s.ledger.roster.getValue(100L)
        assertTrue(caught.hasNickname)
        assertEquals("Peck (Pidgey Lv 3)", caught.label(3))
        assertTrue(s.ledger.openWarnings.none { it.kind == WarnKind.NICKNAME }, "no nickname warning for a named one")
    }

    @Test
    fun `a death names a Pokemon with no nickname once`() {
        val s = Sim().starter(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, nickname = "SQUIRTLE", types = listOf(WATER)))
        s.wildBattle(foe(100))
        s.party = listOf(mon(1, Sp.SQUIRTLE, "SQUIRTLE", 5, hp = 0, nickname = "SQUIRTLE", types = listOf(WATER)))
        s.poll()
        assertTrue(s.ledger.events.any { it.text.startsWith("Squirtle Lv 5 died at Route 3") }, texts(s))
    }
}
