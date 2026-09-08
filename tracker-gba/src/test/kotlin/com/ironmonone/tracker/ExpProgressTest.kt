package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Program.getNextLevelExp over a hand-built table: growth rate 4 (fast),
 * level 10 at 800 and level 11 at 1064; a mon with 900 exp is 100 into a
 * 264-point level. Level 100 has no next level and reads null.
 */
class ExpProgressTest {
    private val T = 0x08300000L
    private fun tracker(): GbaTracker {
        val rom = HashMap<Long, Byte>()
        fun put32(a: Long, v: Long) { for (i in 0 until 4) rom[a + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        put32(T + 4 * 0x194 + 10 * 4, 800); put32(T + 4 * 0x194 + 11 * 4, 1064)
        return GbaTracker(MemoryReader { a, n -> ByteArray(n) { rom[a + it] ?: 0 } }, GameMap.EMERALD_U.copy(expTables = T))
    }
    private fun mon(level: Int, exp: Long) = PokemonDecoder.Mon(
        pid = 1, level = level, nickname = "", species = 1, heldItem = 0, friendship = 0,
        moves = List(4) { 0 }, pp = List(4) { 0 }, ivs = List(6) { 0 }, evs = List(6) { 0 }, ppUps = List(4) { 0 },
        abilitySlot = 0, nature = 0, shiny = false, status = 0, curHp = 1, maxHp = 1, atk = 0, def = 0, spe = 0, spAtk = 0, spDef = 0, exp = exp,
    )
    private val base = BaseStats(1, 1, 1, 1, 1, 1, 0, 0, 0, 0, growthRate = 4)

    @Test
    fun `exp into the level and the level's span come from the table`() {
        assertEquals(100 to 264, tracker().expProgress(mon(10, 900), base))
    }

    @Test
    fun `level 100 and an unpinned table read null`() {
        assertNull(tracker().expProgress(mon(100, 900), base))
        assertNull(GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.EMERALD_U).expProgress(mon(10, 900), base))
    }
}
