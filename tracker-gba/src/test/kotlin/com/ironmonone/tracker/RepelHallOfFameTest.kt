package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Program.ActiveRepel.shouldDisplay (Program.lua:249-253): the repel bar is
 * hidden in the Hall of Fame (RouteData.Locations.IsInHallOfFame: FireRed and
 * LeafGreen 218, Emerald 431, Ruby and Sapphire 298 + 1 as the game numbers
 * maps). We drew it there.
 */
class RepelHallOfFameTest {
    private val SB1 = 0x02025000L

    private fun state(m: GameMap, rawMapId: Int, repel: Int, sb1Fixed: Boolean = false): TrackerState {
        val ram = HashMap<Long, Byte>()
        fun put(a: Long, v: Long, n: Int) { for (i in 0 until n) ram[a + i] = ((v shr (8 * i)) and 0xFF).toByte() }
        val sb1 = if (sb1Fixed) m.saveBlock1Fixed else SB1.also { put(m.saveBlock1Ptr, it, 4) }
        put(sb1 + m.repelStepsOffset, repel.toLong(), 1)
        put(m.mapHeader + 0x12, rawMapId.toLong(), 2)
        val t = GbaTracker(MemoryReader { a, n -> ByteArray(n) { ram[a + it] ?: 0 } }, m)
        t.read()
        return t.read()                 // the map id settles over two polls
    }

    @Test
    fun `the bar hides in the Hall of Fame and shows elsewhere`() {
        val hof = state(GameMap.FIRERED_U_V10, 218, repel = 90)
        assertTrue(hof.inHallOfFame)
        assertEquals(90, hof.repelSteps, "the repel is still read, only not drawn")
        assertFalse(hof.repelVisible)
        val league = state(GameMap.FIRERED_U_V10, 217, repel = 90)
        assertFalse(league.inHallOfFame)
        assertTrue(league.repelVisible)
        assertFalse(state(GameMap.FIRERED_U_V10, 217, repel = 0).repelVisible, "no repel, no bar")
    }

    @Test
    fun `each game has its own Hall of Fame map`() {
        assertTrue(state(GameMap.LEAFGREEN_U, 218, 50).inHallOfFame)
        assertTrue(state(GameMap.EMERALD_U, 431, 50).inHallOfFame)
        assertFalse(state(GameMap.EMERALD_U, 218, 50).inHallOfFame)
        // Ruby keys its maps one higher than Emerald past 107; the reference's 298 + 1.
        val ruby = state(GameMap.RUBY_U, 299, 50, sb1Fixed = true)
        assertTrue(ruby.inHallOfFame)
        assertFalse(ruby.repelVisible)
        assertFalse(state(GameMap.RUBY_U, 298, 50, sb1Fixed = true).inHallOfFame)
    }

    @Test
    fun `Nat Dex uses its base game's map`() {
        for ((name, want) in listOf("firered-natdex-121.gba" to setOf(218), "emerald-natdex-121.gba" to setOf(431))) {
            val rom = File("C:/Users/bepor/IronMonOne/.vendor/roms/$name")
            if (!rom.exists()) { println("SKIP: $name missing"); continue }
            val bytes = rom.readBytes()
            val m = GameMap.resolve(MemoryReader { a, n ->
                val off = (a - 0x08000000L).toInt()
                if (a >= 0x08000000L && off + n <= bytes.size) bytes.copyOfRange(off, off + n) else ByteArray(n)
            })
            assertEquals(want, m.hallOfFameMapIds, name)
        }
    }
}
