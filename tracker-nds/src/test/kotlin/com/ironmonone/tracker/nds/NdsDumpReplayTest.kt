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
 *   whole heap sits 0x40 below the fixed addresses this app used to read. Since
 *   NDS-Ironmon-Tracker 6.3.11 the game's own pointer (u32 at 0x24, here
 *   0x02204CC4) says so, and the tracker follows it. The scan used to find it.
 * - b2-clean-intro.bin: a Black 2 in the bedroom before any Pokemon:
 *   nothing located, no false party. Its pointer says 0x204CC4 too.
 *
 * Every test below states HOW the base was found (NdsTracker.baseSource and
 * scans), not only that the party was: with a second way to find it in the
 * tracker, "the party was read" would pass with the way under test removed.
 * The other paths are driven with the same dump, changed in memory only.
 */
class NdsDumpReplayTest {
    private fun ram(f: File): ByteArray = f.readBytes()

    private fun reader(ram: ByteArray): NdsMemoryReader = NdsMemoryReader { addr, len ->
        val off = addr - 0x02000000L
        if (off < 0 || off >= ram.size) ByteArray(0) else ram.copyOfRange(off.toInt(), minOf(ram.size, (off + len).toInt()))
    }

    private fun dump(name: String): ByteArray? {
        val dir = System.getenv("IRONMON_DUMPS")?.let { File(it) }?.takeIf { it.isDirectory } ?: return null
        return File(dir, name).takeIf { it.isFile }?.let { ram(it) }
    }

    private fun setU32(ram: ByteArray, off: Int, v: Long) { for (i in 0 until 4) ram[off + i] = ((v shr (8 * i)) and 0xFF).toByte() }
    private fun u32(ram: ByteArray, off: Int): Long = (0 until 4).fold(0L) { acc, i -> acc or ((ram[off + i].toLong() and 0xFF) shl (8 * i)) }

    /** What the rival battle dump must read as, by whichever way its heap was found. */
    private fun assertRivalBattle(t: NdsTracker, s: NdsTrackerState) {
        assertTrue(s.located, "party located")
        assertEquals(483, s.party.first().mon.species); assertEquals(5, s.party.first().mon.level); assertEquals(19, s.party.first().mon.curHp)
        assertTrue(s.inBattle, "in battle")
        assertEquals(false, s.isWildBattle, "the rival is a trainer")
        val e = assertNotNull(s.enemy, "enemy card")
        assertEquals(246, e.mon.species); assertEquals(8, e.mon.level)
        // The map headers moved with the heap: the first rival battle is in Aspertia City.
        assertEquals(427, s.mapId)
        assertEquals("Aspertia City", s.areaName)
        // Wherever the base came from, every heap address of the live map is the same 0x40 below the fixed one.
        assertEquals(-0x40L, t.live.playerBase - NdsGameMap.B2W2.playerBase)
        assertEquals(-0x40L, t.live.enemyBase - NdsGameMap.B2W2.enemyBase)
        assertEquals(-0x40L, t.live.mainBattleDataPtr - NdsGameMap.B2W2.mainBattleDataPtr)
    }

    @Test
    fun `a randomized Black 2 battle is read through the game's own pointer, and no scan runs`() {
        val ram = dump("b2-rand-rival-battle.bin") ?: return
        val r = reader(ram)
        val map = assertNotNull(NdsGameMap.detect(r))
        assertEquals("Pokemon Black 2", map.name)
        // The pointer the reference reads (MAIN_POINTER 0x24), masked with 0xFFFFFF: 0x02204CC4 names 0x204CC4.
        assertEquals(0x02204CC4L, u32(ram, 0x24))
        val t = NdsTracker(r, null, map)
        val s = t.read()
        assertRivalBattle(t, s)
        assertEquals(NdsTracker.BaseSource.POINTER, t.baseSource, "found through the pointer")
        assertEquals(0x204CC4L, t.pointerBase)
        assertEquals(0, t.scans, "the pointer is enough: nothing is scanned for")
        assertEquals(0L, t.scanShift)
        println("DUMP_REPLAY pointer ok base=0x%X".format(t.pointerBase))
    }

    @Test
    fun `the same battle is found by the scan when the pointer word is wiped`() {
        val ram = dump("b2-rand-rival-battle.bin") ?: return
        setU32(ram, 0x24, 0)
        val r = reader(ram)
        val t = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r)))
        val s = t.read()
        assertRivalBattle(t, s)
        assertEquals(NdsTracker.BaseSource.SCAN, t.baseSource, "no pointer to use, so the party was searched for")
        assertEquals(null, t.pointerBase)
        assertTrue(t.scans >= 1)
        assertEquals(-0x40L, t.scanShift)
    }

    @Test
    fun `a pointer that leads nowhere is overruled by the scan once a battle is on, and stays overruled`() {
        val ram = dump("b2-rand-rival-battle.bin") ?: return
        // Still a pointer into main RAM, so it can be used, but 0x100 too high: no party where it says.
        setU32(ram, 0x24, 0x02204DC4L)
        val r = reader(ram)
        val t = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r)))
        val s = t.read()
        assertRivalBattle(t, s)
        assertEquals(NdsTracker.BaseSource.SCAN, t.baseSource, "the fixed battle flag said battle, the pointer's party was missing")
        assertEquals(1, t.scans)
        assertEquals(-0x40L, t.scanShift)
        // The pointer still holds the same value on the next read. It is not followed back: no flip between the two.
        val again = t.read()
        assertRivalBattle(t, again)
        assertEquals(NdsTracker.BaseSource.SCAN, t.baseSource)
        assertEquals(1, t.scans, "no second scan: the party decodes where the scan put it")
        // A pointer with a new value gets its chance: this one is right, so it is followed.
        setU32(ram, 0x24, 0x02204CC4L)
        val back = t.read()
        assertRivalBattle(t, back)
        assertEquals(NdsTracker.BaseSource.POINTER, t.baseSource)
        assertEquals(1, t.scans)
    }

    @Test
    fun `a pointer that leads nowhere is not scanned around when no battle is on`() {
        val ram = dump("b2-rand-rival-battle.bin") ?: return
        setU32(ram, 0x24, 0x02204DC4L)
        // The fixed battle flag reads as "not in battle" (Black 2's is at 0x1B5138).
        ram[0x1B5138] = 0; ram[0x1B5139] = 0
        val r = reader(ram)
        val t = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r)))
        repeat(NdsTracker.SCAN_EVERY + 5) { assertEquals(false, t.read().located) }
        assertEquals(NdsTracker.BaseSource.POINTER, t.baseSource)
        assertEquals(0, t.scans, "no battle, so nothing says a party exists: a scan would only walk 4 MB")
    }

    @Test
    fun `a Black 2 before the starter locates nothing, and does not scan for it`() {
        val ram = dump("b2-clean-intro.bin") ?: return
        val r = reader(ram)
        val t = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r)))
        repeat(NdsTracker.SCAN_EVERY + 5) {
            val s = t.read()
            assertEquals(false, s.located)
            assertEquals(false, s.inBattle)
        }
        assertEquals(0x02204CC4L, u32(ram, 0x24))
        assertEquals(NdsTracker.BaseSource.POINTER, t.baseSource, "the pointer is set before any Pokemon exists")
        assertEquals(0x204CC4L, t.pointerBase)
        assertEquals(0, t.scans)
    }

    @Test
    fun `the same intro with no pointer scans on its cooldown, as it always did, and finds nothing`() {
        val ram = dump("b2-clean-intro.bin") ?: return
        setU32(ram, 0x24, 0)
        val r = reader(ram)
        val t = NdsTracker(r, null, assertNotNull(NdsGameMap.detect(r)))
        repeat(NdsTracker.SCAN_EVERY + 5) { assertEquals(false, t.read().located) }
        assertEquals(NdsTracker.BaseSource.STATIC, t.baseSource)
        assertTrue(t.scans >= 1, "no pointer, so the scan is the way to look")
    }
}
