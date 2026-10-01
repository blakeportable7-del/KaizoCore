package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Nat. Dex save offsets the extension reads from the ROM's slot table
 * (NatDexExtension.lua:17563-17576, 17725-17726): the flags (0x08000150),
 * the vars (0x08000154), the repel step count (vars + 0x080003EE), the
 * rival's name (0x08000420) and the trainer flag start (0x08000404). We used
 * the vanilla values, so defeated trainers, the repel bar, the Safari flag and
 * the FireRed rival's name all read unrelated save data.
 */
class NatDexSaveOffsetsTest {
    private val SB1 = 0x02025000L

    private class Mem(val rom: ByteArray) {
        val ram = HashMap<Long, Byte>()
        fun put8(a: Long, v: Int) { ram[a] = v.toByte() }
        fun put32(a: Long, v: Long) { for (i in 0 until 4) put8(a + i, ((v shr (i * 8)) and 0xFF).toInt()) }
        fun reader() = MemoryReader { address, length ->
            if (address >= 0x08000000L) {
                val off = (address - 0x08000000L).toInt()
                if (off >= 0 && off + length <= rom.size) rom.copyOfRange(off, off + length) else ByteArray(0)
            } else ByteArray(length) { ram[address + it] ?: 0 }
        }
    }

    private fun rom(name: String): ByteArray? =
        File("C:/Users/bepor/IronMonOne/.vendor/roms/$name").takeIf { it.exists() }?.readBytes()
            ?: run { println("SKIP: $name missing"); null }

    @Test
    fun `both builds publish their own offsets`() {
        val fr = rom("firered-natdex-121.gba") ?: return
        val m = GameMap.resolve(Mem(fr).reader())
        assertEquals(0x1090L, m.gameFlagsOffset)
        assertEquals(0x11B0L, m.gameVarsOffset)
        assertEquals(0x11B0L + 0x40, m.repelStepsOffset)
        assertEquals(0x3829L, m.rivalNameOffset)
        assertEquals(0x500, m.trainerFlagStart)
        val em = rom("emerald-natdex-121.gba") ?: return
        val e = GameMap.resolve(Mem(em).reader())
        assertEquals(0x1458L, e.gameFlagsOffset)
        assertEquals(0x1584L, e.gameVarsOffset)
        assertEquals(0x1584L + 0x52, e.repelStepsOffset, "Emerald Nat. Dex moves the repel var to +0x52")
        // The vanilla maps are untouched.
        assertEquals(0xEE0L, GameMap.FIRERED_U_V10.gameFlagsOffset)
        assertEquals(0x1040L, GameMap.FIRERED_U_V10.repelStepsOffset)
        assertEquals(0x3A4CL, GameMap.FIRERED_U_V10.rivalNameOffset)
    }

    @Test
    fun `FireRed Nat Dex reads the repel, the flags and the rival's name where they are`() {
        val fr = rom("firered-natdex-121.gba") ?: return
        val mem = Mem(fr)
        val m = GameMap.resolve(mem.reader())
        mem.put32(m.saveBlock1Ptr, SB1)
        mem.put8(SB1 + 0x11B0 + 0x40, 120)                          // a Super Repel, 120 steps left
        val brock = 0x500 + 414
        mem.put8(SB1 + 0x1090 + brock / 8, 1 shl (brock % 8))    // Brock beaten
        mem.put8(SB1 + 0x1090 + 0x800 / 8, 1)                     // SYS_SAFARI_MODE
        "GARY".forEachIndexed { i, c -> mem.put8(SB1 + 0x3829 + i, 0xBB + (c - 'A')) }
        mem.put8(SB1 + 0x3829 + 4, 0xFF)
        val t = GbaTracker(mem.reader(), m)
        assertEquals(120, t.readRepelSteps())
        assertTrue(t.trainerDefeated(414))
        assertFalse(t.trainerDefeated(415))
        assertTrue(t.isInSafariZone())
        // Trainer 326 is a rival (rivals-frlg.tsv): its name comes from the save, as Program.lua:1063 reads it.
        assertEquals("GARY", t.trainer(326)?.name)
    }
}
