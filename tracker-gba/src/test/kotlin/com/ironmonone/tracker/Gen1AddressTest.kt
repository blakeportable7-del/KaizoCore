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
    /**
     * Every work RAM read of the map, which are its Long fields (the ROM tables are Ints), found by reflection so a read
     * added later is checked too: a hand list stopped at curMap and left out the twelve Nuzlocke reads (rc32 audit
     * P3 #117). Then the far end of each block read in one go, and the tracker's own fixed reads. 0 is a read the map
     * does not have.
     */
    private fun wram(m: Gen1Map): List<Long> {
        val fields = Gen1Map::class.java.declaredFields.filter { it.type == java.lang.Long.TYPE }
            .map { f -> f.isAccessible = true; f.getLong(m) }.filter { it != 0L }
        return fields + listOf(m.partyMons + 6 * Gen1Tracker.PARTY_STRIDE - 1, m.enemyMon + Gen1Tracker.ENEMY_SIZE - 1,
            m.items + 2 * Gen1Tracker.ITEM_SLOTS, m.statMods + 0x14 + 5, m.nicks + 6 * 11 - 1,
            Gen1Tracker.MENU + 6, Gen1Tracker.TILE_MAP + 0x10 * 20 + 0x0F, Gen1Tracker.PLAYER_MON_NUMBER)
    }

    @Test
    fun `every Gen 1 address lies inside the 8 KB work RAM`() {
        for (m in listOf(Gen1Map.RED_BLUE, Gen1Map.YELLOW)) {
            val reads = wram(m)
            assertTrue(reads.size >= 30, "${m.name}: only ${reads.size} reads found; the reflection lost the map's fields")
            for (a in reads) assertTrue(a in 0L until 0x2000L, "${m.name}: 0x%X is outside work RAM".format(a))
        }
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
