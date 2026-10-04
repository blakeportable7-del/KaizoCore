package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TrackerState.settling (rc35, "very slow to start displaying data"): true while a read is about to change what the
 * tracker shows, so the app reads again soon (TrackerPoll): a map id seen once, and a battle begun whose data is not
 * ready yet.
 */
class TrackerSettlingTest {
    private val map = GameMap.FIRERED_U_V10

    private class Mem : MemoryReader {
        val bytes = HashMap<Long, Byte>()
        override fun read(address: Long, length: Int) = ByteArray(length) { bytes[address + it] ?: 0 }
        fun put(a: Long, v: Long, n: Int) { for (i in 0 until n) bytes[a + i] = (v shr (8 * i)).toByte() }
    }

    @Test
    fun `a new map id settles on the read that adopts it`() {
        val mem = Mem()
        mem.put(map.mapHeader + 0x12, 12, 2)
        val t = GbaTracker(mem, map)
        val first = t.read()
        assertTrue(first.settling, "seen once")
        assertEquals(null, first.mapId)
        val second = t.read()
        assertFalse(second.settling)
        assertEquals(12, second.mapId)
        mem.put(map.mapHeader + 0x12, 13, 2)
        assertTrue(t.read().settling, "a map change, seen once")
        assertEquals(13, t.read().mapId)
    }

    @Test
    fun `a battle begun settles until its data is ready, the reference's DataStart`() {
        val mem = Mem()
        mem.put(map.mapHeader + 0x12, 12, 2)
        val t = GbaTracker(mem, map)
        t.read(); t.read()
        // A wild battle: two battlers, a real species in gBattleMons, no outcome; the intro still sliding in.
        mem.put(map.battlersCount, 2, 1)
        mem.put(map.battleMons, 25, 2)
        mem.put(map.battleMainFunc, 0x08001234L, 4)
        val begun = t.read()
        assertFalse(begun.inBattle)
        assertTrue(begun.settling, "the battle has begun, its data is not ready")
        mem.put(map.battleMainFunc, map.introDrawPartySummary, 4)
        val ready = t.read()
        assertTrue(ready.inBattle)
        assertFalse(ready.settling)
    }
}
