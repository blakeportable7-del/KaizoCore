package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The per-game maps are copied from NDS-Ironmon-Tracker's
 * constants/MemoryAddresses.lua and GameInfo.lua. These pin the copy.
 */
class NdsGameMapTest {

    @Test
    fun `game codes pick the map, and an unknown game gets none`() {
        assertSame(NdsGameMap.PLATINUM, NdsGameMap.forCode(0x45555043))    // CPUE
        assertSame(NdsGameMap.HGSS, NdsGameMap.forCode(0x454B5049))        // IPKE
        assertSame(NdsGameMap.HGSS, NdsGameMap.forCode(0x45475049))        // IPGE
        assertSame(NdsGameMap.BW, NdsGameMap.forCode(0x4F425249))         // Black
        assertNull(NdsGameMap.forCode(0x00000000))
        assertNull(NdsGameMap.forCode(0x45434B41))                         // "AKCE": not a Pokemon game
    }

    @Test
    fun `HGSS offsets match the reference block`() {
        val m = NdsGameMap.HGSS
        assertEquals(0xA8L, m.playerBase)
        assertEquals(0x4F068L, m.enemyBase)
        assertEquals(0x440AAL, m.enemyTrainerId)
        assertEquals(0x49E7CL, m.playerBattleMonPid)
        assertEquals(0x49F3CL, m.enemyBattleMonPid)
        assertEquals(0x49E2CL, m.statStagesPlayer)
        assertEquals(0x49EECL, m.statStagesEnemy)
        assertEquals(0x47184L, m.battleSubscriptMsgs)
        assertEquals(0xB74L, m.itemStartNoBattle)
        assertEquals(0x46AD8L, m.itemStartBattle)
        assertEquals(0xC14L, m.berryBagStart)
        assertEquals(0x46B78L, m.berryBagStartBattle)
        assertEquals(listOf(0x8EL, 0x93L), m.badgeOffsets)                 // johto, kanto
        assertEquals(0x246F48L, m.battleStatus)
        assertEquals("HGSS", m.badgePrefix)
    }

    @Test
    fun `Platinum offsets are the ones the tracker always used`() {
        val m = NdsGameMap.PLATINUM
        assertEquals(0xB4L, m.playerBase)
        assertEquals(0x4BE5CL, m.enemyBase)
        assertEquals(0xB60L, m.itemStartNoBattle)
        assertEquals(0xC00L, m.berryBagStart)
        assertEquals(listOf(0x96L), m.badgeOffsets)
        assertEquals(0x24A55AL, m.battleStatus)
    }

    @Test
    fun `detect reads the header code out of RAM`() {
        fun ramWithCode(code: Long) = NdsMemoryReader { addr, len ->
            if (addr == NdsGameMap.CARTRIDGE_HEADER + 0x0C && len == 4)
                byteArrayOf(code.toByte(), (code shr 8).toByte(), (code shr 16).toByte(), (code shr 24).toByte())
            else ByteArray(len)
        }
        assertSame(NdsGameMap.HGSS, NdsGameMap.detect(ramWithCode(0x454B5049)))
        assertSame(NdsGameMap.PLATINUM, NdsGameMap.detect(ramWithCode(0x45555043)))
        assertNull(NdsGameMap.detect(ramWithCode(0)))
        // A reader that cannot see the header is a refusal, not Platinum.
        assertNull(NdsGameMap.detect(NdsMemoryReader { _, _ -> ByteArray(0) }))
    }
}
