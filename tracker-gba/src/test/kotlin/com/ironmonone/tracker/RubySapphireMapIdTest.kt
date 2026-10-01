package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Ruby and Sapphire report map ids one higher than Emerald's above 108 (an empty Lilycove layout
 * sits at 108); the tracker keys its tables Emerald's way (GameMap.rsMapShift). Two places mixed
 * the numberings: the log's wild sets (routesets-rs.tsv is in the game's raw ids) and the route
 * debug log, which named a raw id as if it were the tracker's (parity audit, 2026-09-28).
 */
class RubySapphireMapIdTest {
    private class Fake : MemoryReader {
        val bytes = HashMap<Long, Byte>()
        fun put(addr: Long, value: Long, len: Int) {
            for (i in 0 until len) bytes[addr + i] = ((value shr (8 * i)) and 0xFF).toByte()
        }
        override fun read(address: Long, length: Int) = ByteArray(length) { bytes[address + it] ?: 0 }
    }

    @Test
    fun `the log's R-S wild sets come back as the tracker's map ids`() {
        val rs = GbaTracker(Fake(), GameMap.RUBY_U).logRouteSets()
        // RandomizerLog.lua: set 61 = 163 + offset (Victory Road 1F), 39 = 142 + offset (Mt. Pyre 6F).
        assertEquals(163, rs[61]); assertEquals(142, rs[39])
        assertEquals("Victory Road 1F", GbaTracker(Fake(), GameMap.RUBY_U).routeInfo(rs.getValue(61))?.first)
        assertEquals(17, rs[85], "below 108 nothing moves")
        // Emerald and FireRed are not shifted.
        assertEquals(162, GbaTracker(Fake(), GameMap.EMERALD_U).logRouteSets()[125])
        assertEquals(335, GbaTracker(Fake(), GameMap.FIRERED_U_V10).logRouteSets()[1], "Monean Chamber")
    }

    @Test
    fun `R-S route info is keyed the game-correct way, gyms and Route 124 Water included`() {
        // Blake, 2026-09-29: keep the correct numbers. The reference writes these without its
        // offset, so on R/S it showed Sootopolis Gym 1F's data in Mossdeep Gym and never showed
        // Route 124's underwater encounters.
        val t = GbaTracker(Fake(), GameMap.RUBY_U)
        assertEquals("Mossdeep Gym", t.routeInfo(108)?.first)
        assertEquals("Sootopolis Gym 1F", t.routeInfo(109)?.first)
        assertEquals("Champion's Room", t.routeInfo(115)?.first)
        assertEquals(listOf("Underwater"), t.routeEncounters(274).keys.toList())
        assertEquals(274, t.mapIdFromRouteKey(t.routeKey(274)!!))
        assertEquals(108, t.mapIdFromRouteKey(t.routeKey(108)!!))
        // The map whose raw id Route 124 Water's key takes has no route data of its own.
        assertEquals(null, t.routeKey(273))
        assertTrue(t.routeEncounters(273).isEmpty())
        // Every other R/S map above 108 still goes by the raw id: Magma Hideout 1F is raw 144.
        assertEquals(144, t.routeKey(143))
        assertEquals(143, t.mapIdFromRouteKey(144))
        // Emerald is untouched.
        val e = GbaTracker(Fake(), GameMap.EMERALD_U)
        assertEquals(273, e.routeKey(273))
        assertEquals(listOf("Underwater"), e.routeEncounters(274).keys.toList())
    }

    @Test
    fun `the route log names the map the game reports`() {
        val m = Fake()
        val t = GbaTracker(m, GameMap.RUBY_U)
        fun at(raw: Int): String {
            m.put(GameMap.RUBY_U.mapHeader + 0x12, raw.toLong(), 2)
            t.read(); t.read()
            return t.routeLogSnapshot().last()
        }
        // Raw 144 is Ruby's Magma Hideout 1F, raw 109 its Mossdeep Gym (RSTrainerRouteData.lua).
        assertTrue(at(144).endsWith("map=144 route=Magma Hideout 1F wild=0"), t.routeLogSnapshot().last())
        assertTrue(at(109).contains("map=109 route=Mossdeep Gym "), t.routeLogSnapshot().last())
        assertTrue(at(17).contains("map=17 route=Route 101 "), t.routeLogSnapshot().last())
    }
}
