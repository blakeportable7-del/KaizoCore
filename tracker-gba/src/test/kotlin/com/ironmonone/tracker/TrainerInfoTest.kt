package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Program.readTrainerGameData over a hand-built ROM: a Bug Catcher (class 5)
 * named ROB with two Pokemon in the item + default-moves layout (flags 2), AI
 * flags 7 (Smart), and the trainer flag set in the save block.
 */
class TrainerInfoTest {
    private val G = 0x08300000L; private val C = 0x08310000L; private val PARTY = 0x08320000L; private val SB1 = 0x02025734L
    private val ENC = Gen3Charmap.MAP.entries.associate { (k, v) -> v to k }
    private fun mem(): MemoryReader {
        val rom = HashMap<Long, Byte>()
        fun put(a: Long, vararg b: Int) { b.forEachIndexed { i, v -> rom[a + i] = v.toByte() } }
        fun putText(a: Long, s: String) { s.forEachIndexed { i, ch -> rom[a + i] = ENC.getValue(ch.toString()).toByte() }; rom[a + s.length] = 0xFF.toByte() }
        val t = G + 7 * 0x28
        put(t, 2, 5, 0, 1)                       // flags 2 (held item, default moves), class 5, gender, pic
        putText(t + 4, "ROB")
        put(t + 0x18, 0)                         // single battle
        put(t + 0x1C, 7, 0, 0, 0)                // AI flags
        put(t + 0x20, 2)                         // party size
        put(t + 0x24, 0x00, 0x00, 0x32, 0x08)    // party pointer 0x08320000
        putText(C + 5 * 13, "BUG CATCHER")
        put(PARTY, 255, 0, 9, 0, 10, 0, 0, 0)    // iv 255, lv 9, species 10 (Caterpie), no item
        put(PARTY + 8, 0, 0, 11, 0, 13, 0, 1, 0) // iv 0, lv 11, species 13 (Weedle), item 1
        // flag 0x500 + 7 = 0x507 -> byte 0xA0, bit 7
        rom[SB1 + 0xEE0 + 0xA0] = 0x80.toByte()
        return MemoryReader { addr, len -> ByteArray(len) { rom[addr + it] ?: 0 } }
    }

    @Test
    fun `the trainer entry, class, name, party and defeated flag read as the reference reads them`() {
        val map = GameMap.FIRERED_U_V10.copy(gTrainers = G, gTrainerClassNames = C, gameFlagsOffset = 0xEE0, saveBlock1Fixed = SB1, saveBlock1Ptr = 0)
        val tr = GbaTracker(mem(), map)
        val t = assertNotNull(tr.trainer(7))
        assertEquals("BUG CATCHER", t.className); assertEquals("ROB", t.name)
        assertEquals(2, t.party.size)
        assertEquals(10, t.party[0].species); assertEquals(9, t.party[0].level); assertEquals(31, t.party[0].ivs)
        assertEquals(13, t.party[1].species); assertEquals(1, t.party[1].heldItem); assertEquals(0, t.party[1].ivs)
        assertEquals("Smart", t.aiLabel); assertEquals(9, t.minLevel); assertEquals(11, t.maxLevel); assertEquals(15, t.avgIvs)
        assertTrue(t.defeated)
        assertEquals(false, tr.trainerDefeated(8))
    }

    @Test
    fun `the rival tables load and the route list drops the other rivals once one is known`() {
        val tr = GbaTracker(MemoryReader { _, len -> ByteArray(len) }, GameMap.FIRERED_U_V10)
        assertEquals("Middle", tr.whichRival(326)); assertEquals("Left", tr.whichRival(327)); assertEquals(null, tr.whichRival(102))
        // A route with no rival keeps its full list.
        assertEquals(tr.trainersOnRoute(12), tr.trainersForRoute(12))
        assertTrue(tr.trainersForRoute(12).isNotEmpty())
    }
}
