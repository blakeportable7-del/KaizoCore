package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Diamond and Pearl start their pointer chain at 0xB70, not Platinum's
 * 0xBA8. A RAM image with the chain planted at 0xB70 and a Potion at D/P's
 * items pocket is read with the D/P map and with Platinum's: D/P must find
 * it, Platinum must find nothing - which is the check that fails if the
 * chain start is still the shared constant.
 */
class NdsDpMapTest {

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
        putU32(NdsGameMap.DP.globalPointer, globalRel)
        putU32(globalRel + NdsGameMap.VERSION_POINTER_OFFSET, versionRel)
        // One Potion (id 17, qty 2) at D/P's items pocket, then a terminator.
        putU32(versionRel + NdsGameMap.DP.itemStartNoBattle, 17L or (2L shl 16))
        putU32(versionRel + NdsGameMap.DP.itemStartNoBattle + 4, 0)
        putU32(versionRel + NdsGameMap.DP.berryBagStart, 0)
        // Badges: the first three.
        ram[(versionRel + 0x292).toInt()] = 0b111
        putU32(NdsGameMap.CARTRIDGE_HEADER - RAM + 0x0C, NdsGameMap.CODE_DIAMOND)
        return ram
    }

    private fun reader(ram: ByteArray) = NdsMemoryReader { address, length ->
        val off = (address - RAM).toInt()
        if (off >= 0 && off + length <= ram.size) ram.copyOfRange(off, off + length) else ByteArray(0)
    }

    @Test
    fun `D and P find the bag through their own chain, Platinum does not`() {
        val r = reader(ram())
        val dp = NdsTracker(r, null, NdsGameMap.DP)
        val plat = NdsTracker(r, null, NdsGameMap.PLATINUM)
        assertEquals(40 to 2, dp.readHeals(100, inBattle = false), "D/P map missed its own bag")
        assertEquals(0 to 0, plat.readHeals(100, inBattle = false), "Platinum read D/P's bag: the chain start is still shared")
    }

    @Test
    fun `badges read off the D and P badge byte`() {
        val dp = NdsTracker(reader(ram()), null, NdsGameMap.DP)
        assertEquals(3, Integer.bitCount(dp.readBadges()))
    }
}
