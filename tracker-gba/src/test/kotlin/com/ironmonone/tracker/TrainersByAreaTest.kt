package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The carousel's Trainers defeated line counts as the PC tracker does (docs/parity/gaps.tsv row 11): a combined area's
 * maps together (Program.getDefeatedTrainersByCombinedArea, Program.lua:1567-1581; RouteData.CombinedAreas, FRLG at
 * RouteData.lua:465-484), and no rival the run will not face (TrainerData.shouldUseTrainer, TrainerData.lua:304-316).
 */
class TrainersByAreaTest {
    private val SB1 = 0x02025734L
    private val map = GameMap.FIRERED_U_V10.copy(gameFlagsOffset = 0xEE0, saveBlock1Fixed = SB1, saveBlock1Ptr = 0)

    private class Mem : MemoryReader {
        val bytes = HashMap<Long, Byte>()
        override fun read(address: Long, length: Int) = ByteArray(length) { bytes[address + it] ?: 0 }
        fun put16(a: Long, v: Int) { bytes[a] = v.toByte(); bytes[a + 1] = (v shr 8).toByte() }
    }

    private fun beat(mem: Mem, trainerId: Int) {
        val flag = map.trainerFlagStart + trainerId
        val a = SB1 + map.gameFlagsOffset + flag / 8
        mem.bytes[a] = ((mem.bytes[a]?.toInt() ?: 0) or (1 shl (flag % 8))).toByte()
    }

    @Test
    fun `Mt Moon's three floors count as one area, and a map of its own counts alone`() {
        val t = GbaTracker(Mem(), map)
        assertEquals("Mt. Moon", t.combinedArea(114))
        assertEquals(listOf(114, 115, 116), t.areaMaps(116))
        // 1F {181,91,120,121,169,108,109} and B2F {170,351,352,353,354}, RouteData.lua:1231-1253.
        assertEquals(listOf(181, 91, 120, 121, 169, 108, 109, 170, 351, 352, 353, 354), t.trainersInArea(114))
        assertEquals(t.trainersInArea(114), t.trainersInArea(115), "B1F has no trainers but is in the area")
        // The S.S. Anne: its 2F, its deck and every cabin with trainers, from whichever of its maps.
        val anne = t.trainersInArea(123)
        assertTrue(anne.containsAll(listOf(426, 427, 428, 134, 135)) && anne.size == 19, "$anne")
        assertEquals(anne, t.trainersInArea(120))
        assertNull(t.combinedArea(12))
        assertEquals(t.trainersOnRoute(12), t.trainersInArea(12), "a route is its own area")
    }

    @Test
    fun `the rivals this run will not face drop out once the first rival battle says which`() {
        val t = GbaTracker(Mem(), map)
        assertEquals(listOf(326, 327, 328), t.trainersInArea(5), "Oak's Lab: every rival until one is known")
        t.restoreRival("Left")
        assertEquals(listOf(327), t.trainersInArea(5))
    }

    @Test
    fun `the state counts the area's beaten trainers on every floor`() {
        val mem = Mem()
        beat(mem, 181); beat(mem, 351)   // one on 1F, one on B2F
        beat(mem, 426)                   // the S.S. Anne: not Mt. Moon's
        mem.put16(map.mapHeader + 0x12, 116)
        val t = GbaTracker(mem, map)
        t.read()
        val s = t.read()                 // a map id is adopted on its second read
        assertEquals(116, s.mapId)
        assertEquals(listOf(170, 351, 352, 353, 354), s.routeTrainers, "the map's own list still gates the line")
        assertEquals(2, s.routeTrainersDefeated)
        assertEquals(12, s.routeTrainersTotal)
    }
}
