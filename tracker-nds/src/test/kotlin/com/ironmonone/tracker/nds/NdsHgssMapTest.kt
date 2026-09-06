package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The tracker reads THROUGH the map, not past it.
 *
 * The same RAM image, with a Potion in the bag at HGSS's pocket offset
 * (0xB74) and badges at HGSS's two badge bytes, is read once with the HGSS
 * map and once with Platinum's. HGSS must find them; Platinum must find
 * nothing - which is the check that fails if any offset is still hardcoded.
 */
class NdsHgssMapTest {

    private val RAM = 0x02000000L
    private val globalRel = 0x100000L
    private val versionRel = 0x180000L

    private fun ram(): ByteArray {
        val ram = ByteArray(0x400000)
        fun putU32(rel: Long, v: Long) {
            val i = rel.toInt()
            ram[i] = (v and 0xFF).toByte(); ram[i + 1] = ((v shr 8) and 0xFF).toByte()
            ram[i + 2] = ((v shr 16) and 0xFF).toByte(); ram[i + 3] = ((v shr 24) and 0xFF).toByte()
        }
        putU32(NdsGameMap.GLOBAL_POINTER, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
        // One Potion (id 17, qty 2) at HGSS's items pocket, then a terminator.
        putU32(versionRel + NdsGameMap.HGSS.itemStartNoBattle, 17L or (2L shl 16))
        putU32(versionRel + NdsGameMap.HGSS.itemStartNoBattle + 4, 0)
        // Berries pocket empty (terminator) for both maps.
        putU32(versionRel + NdsGameMap.HGSS.berryBagStart, 0)
        putU32(versionRel + NdsGameMap.PLATINUM.berryBagStart, 0)
        putU32(versionRel + NdsGameMap.PLATINUM.itemStartNoBattle, 0)
        // Badges: 3 Johto (bits 0-2), 1 Kanto (bit 0).
        ram[(versionRel + 0x8E).toInt()] = 0b111
        ram[(versionRel + 0x93).toInt()] = 0b1
        // HGSS header code, so detect() agrees with the map handed in.
        putU32(NdsGameMap.CARTRIDGE_HEADER - RAM + 0x0C, NdsGameMap.CODE_HEART_GOLD)
        return ram
    }

    private fun reader(ram: ByteArray) = NdsMemoryReader { address, length ->
        val off = (address - RAM).toInt()
        if (off >= 0 && off + length <= ram.size) ram.copyOfRange(off, off + length) else ByteArray(0)
    }

    @Test
    fun `HGSS finds the bag where HGSS keeps it, Platinum does not`() {
        val r = reader(ram())
        val hgss = NdsTracker(r, null, NdsGameMap.HGSS)
        val plat = NdsTracker(r, null, NdsGameMap.PLATINUM)
        assertEquals(40 to 2, hgss.readHeals(100, inBattle = false), "HGSS map missed its own bag")
        assertEquals(0 to 0, plat.readHeals(100, inBattle = false), "Platinum map read HGSS's bag: an offset is still hardcoded")
    }

    @Test
    fun `two badge bytes combine into one bitfield`() {
        val r = reader(ram())
        val hgss = NdsTracker(r, null, NdsGameMap.HGSS)
        assertEquals(0b1_0000_0111, hgss.readBadges(), "Johto in bits 0-7, Kanto in 8-15")
        assertEquals(0, NdsTracker(r, null, NdsGameMap.PLATINUM).readBadges())
    }

    @Test
    fun `detect and the map agree on this image`() {
        assertEquals(NdsGameMap.HGSS, NdsGameMap.detect(reader(ram())))
    }

    @Test
    fun `the state names its badge art after the map, located or not`() {
        // This image has no party, so read() returns the unlocated state;
        // the badge set must still be right, or the panel draws DPPT badges
        // over an HGSS run until the party is found.
        val r = reader(ram())
        assertEquals("HGSS", NdsTracker(r, null, NdsGameMap.HGSS).read().badgeSet)
        assertEquals("DPPT", NdsTracker(r, null, NdsGameMap.PLATINUM).read().badgeSet)
    }
}
