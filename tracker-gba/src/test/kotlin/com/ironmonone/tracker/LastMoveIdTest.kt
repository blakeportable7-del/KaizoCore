package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/** The highest move id a game has, which the log viewer's move list runs to (rc32 audit P2 #28). */
class LastMoveIdTest {
    @Test
    fun `the five games end at Psycho Boost and the Nat Dex builds at the last line of their list`() {
        // The log viewer stopped at 354 on every build, so a Nat. Dex run's newer moves were never drawn as same-type.
        assertEquals(354, GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.EMERALD_U).lastMoveId)
        assertEquals(354, GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.FIRERED_U_V10).lastMoveId)
        assertEquals(847, GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.EMERALD_U.copy(namesFromLists = true, expandedSpeciesIds = true)).lastMoveId)
        // MaxDex's list comes through its name set, not namesFromLists: it stopped at 354 until rc34.
        assertEquals(841, GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.MAXDEX_FR_10).lastMoveId)
    }
}
