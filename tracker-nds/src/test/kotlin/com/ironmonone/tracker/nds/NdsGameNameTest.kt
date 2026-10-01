package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The reference names its past-runs log after the game (SeedLogger(self, gameInfo.NAME),
 * savedData/<name>.pastlog, SeedLogger.lua:233). One map serves Diamond and Pearl, another
 * HeartGold and SoulSilver, so the name comes from the cartridge header's code, not the map.
 */
class NdsGameNameTest {
    private fun header(code: Long) = NdsMemoryReader { address, length ->
        if (address == NdsGameMap.CARTRIDGE_HEADER + 0x0C && length == 4) ByteArray(4) { i -> ((code shr (8 * i)) and 0xFF).toByte() }
        else ByteArray(0)
    }

    @Test
    fun `the game is named from the header, not the map`() {
        assertEquals("Pokemon Pearl", NdsTracker(header(NdsGameMap.CODE_PEARL), null, NdsGameMap.DP).gameName)
        assertEquals("Pokemon Diamond", NdsTracker(header(NdsGameMap.CODE_DIAMOND), null, NdsGameMap.DP).gameName)
        assertEquals("Pokemon SoulSilver", NdsTracker(header(NdsGameMap.CODE_SOUL_SILVER), null, NdsGameMap.HGSS).gameName)
        assertEquals("Pokemon White 2", NdsTracker(header(NdsGameMap.CODE_WHITE2), null, NdsGameMap.WHITE2).gameName)
        assertEquals("Pokemon Platinum", NdsTracker(header(NdsGameMap.CODE_PLATINUM), null, NdsGameMap.PLATINUM).read().gameName,
            "the state carries it, before a party exists too")
    }

    @Test
    fun `an unreadable or foreign header falls back to the map's first game`() {
        assertEquals("Pokemon Diamond", NdsTracker(NdsMemoryReader { _, _ -> ByteArray(0) }, null, NdsGameMap.DP).gameName)
        assertEquals("Pokemon HeartGold", NdsTracker(header(NdsGameMap.CODE_PLATINUM), null, NdsGameMap.HGSS).gameName)
    }
}
