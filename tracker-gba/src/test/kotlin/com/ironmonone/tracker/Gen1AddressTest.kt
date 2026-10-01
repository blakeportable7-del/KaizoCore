package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gen 1 addresses are offsets into the core's SYSTEM_RAM, the Game Boy's 8 KB
 * work RAM (0xC000-0xDFFF), as the reference writes them. Until 2026-09-28 they
 * were raw Game Boy addresses (0xD16B), every read fell outside that RAM and
 * came back empty on a device, and the unit tests' 64 KB fake memory hid it.
 */
class Gen1AddressTest {
    private fun wram(m: Gen1Map) = listOf(m.partyCount, m.partySpecies, m.partyMons, m.enemyMon,
        m.inBattle, m.enemyMove, m.badges, m.numItems, m.items, m.aiTurns, m.statMods, m.statMods + 0x14 + 5, m.curMap)

    @Test
    fun `every Gen 1 address lies inside the 8 KB work RAM`() {
        for (m in listOf(Gen1Map.RED_BLUE, Gen1Map.YELLOW))
            for (a in wram(m)) assertTrue(a in 0L until 0x2000L, "${m.name}: 0x%X is outside work RAM".format(a))
    }

    @Test
    fun `Red and Blue match the reference's own offsets`() {
        // Ironmon-gen-tracker GameSettings.setWramAddresses (Red): pstats, estats, eMove,
        // gBattleTypeFlags, badgeOffset, bagPocket_Items_offset, bagPocket_Items_Size, and
        // the party count it files under gBattlerPartyIndexes.
        val m = Gen1Map.RED_BLUE
        assertEquals(0x116BL, m.partyMons)
        assertEquals(0x0FE5L, m.enemyMon)
        assertEquals(0x0FCCL, m.enemyMove)
        assertEquals(0x1057L, m.inBattle)
        assertEquals(0x1356L, m.badges)
        assertEquals(0x131EL, m.items)
        assertEquals(0x131DL, m.numItems)
        assertEquals(0x1163L, m.partyCount)
        // StatChange and gMapHeader; Yellow shifts gMapHeader by one and keeps StatChange and gTurn.
        assertEquals(0x0D1AL, m.statMods)
        assertEquals(0x135EL, m.curMap)
        assertEquals(0x135DL, Gen1Map.YELLOW.curMap)
        assertEquals(m.statMods, Gen1Map.YELLOW.statMods); assertEquals(m.aiTurns, Gen1Map.YELLOW.aiTurns)
    }
}
