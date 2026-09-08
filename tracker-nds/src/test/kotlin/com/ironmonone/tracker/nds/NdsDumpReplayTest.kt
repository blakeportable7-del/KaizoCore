package com.ironmonone.tracker.nds

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The tracker over real 4 MB dumps of DS main RAM taken from the emulator on
 * 2026-09-08 (IRONMON_DUMPS points at the folder; .vendor/dumps in the repo):
 *
 * - b2-rand-rival-battle.bin: a Kaizo-randomized Black 2 in the first rival
 *   battle, starter Dialga (483) Lv5 19/19 against Larvitar (246) Lv8. The
 *   whole heap sits 0x40 below the PC tracker's addresses; the party was
 *   found by the scan and every other address followed.
 * - b2-clean-intro.bin: a clean Black 2 in the bedroom before any Pokemon:
 *   nothing located, no false party.
 */
class NdsDumpReplayTest {
    private fun reader(f: File): NdsMemoryReader {
        val ram = f.readBytes()
        return NdsMemoryReader { addr, len ->
            val off = addr - 0x02000000L
            if (off < 0 || off >= ram.size) ByteArray(0) else ram.copyOfRange(off.toInt(), minOf(ram.size, (off + len).toInt()))
        }
    }

    @Test
    fun `a randomized Black 2 battle is read through the measured shift`() {
        val dir = System.getenv("IRONMON_DUMPS")?.let { File(it) }?.takeIf { it.isDirectory } ?: return
        val f = File(dir, "b2-rand-rival-battle.bin").takeIf { it.isFile } ?: return
        val r = reader(f)
        val map = assertNotNull(NdsGameMap.detect(r))
        assertEquals("Pokemon Black 2", map.name)
        val t = NdsTracker(r, null, map)
        val s = t.read()
        assertTrue(s.located, "party located")
        assertEquals(-0x40L, t.scanShift)
        assertEquals(483, s.party.first().mon.species); assertEquals(5, s.party.first().mon.level); assertEquals(19, s.party.first().mon.curHp)
        assertTrue(s.inBattle, "in battle")
        assertEquals(false, s.isWildBattle, "the rival is a trainer")
        val e = assertNotNull(s.enemy, "enemy card")
        assertEquals(246, e.mon.species); assertEquals(8, e.mon.level)
        println("DUMP_REPLAY ok shift=${t.scanShift}")
    }

    @Test
    fun `a clean Black 2 before the starter locates nothing`() {
        val dir = System.getenv("IRONMON_DUMPS")?.let { File(it) }?.takeIf { it.isDirectory } ?: return
        val f = File(dir, "b2-clean-intro.bin").takeIf { it.isFile } ?: return
        val r = reader(f)
        val t = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r)))
        val s = t.read()
        assertEquals(false, s.located)
        assertEquals(false, s.inBattle)
    }
}
