package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Nat. Dex 1.21 address resolution, pinned against the real ROM.
 *
 * The fidelity audit found five EWRAM/IWRAM addresses hardcoded to their
 * VANILLA values in the Nat. Dex branch - saveBlock1/2, mapHeader,
 * battleOutcome, trainerOpponent - plus a learnset table address from v1.13
 * that holds zeros in 1.21. All six are published in the ROM's own slot
 * table (the extension itself reads them there), so the fix resolves them
 * at runtime. These tests prove the resolution lands where the 1.21 ROM
 * actually publishes, not where vanilla Emerald keeps things.
 */
class NatDexResolveTest {

    private val rom = File(
        "C:/Users/bepor/IronMonOne/.vendor/roms/emerald-natdex-121.gba"
    )

    private fun tracker(): GbaTracker? {
        if (!rom.exists()) return null
        val bytes = rom.readBytes()
        val mem = MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= bytes.size)
                bytes.copyOfRange(off, off + length)
            else ByteArray(0)
        }
        // resolve() is what production runs; constructing with the default
        // map silently tests vanilla Emerald instead.
        return GbaTracker(mem, GameMap.resolve(mem))
    }

    @Test
    fun `saveblock pointers come from the slot table, not vanilla`() {
        val t = tracker() ?: run { println("SKIP: natdex rom missing"); return }
        // 1.21 publishes 0x03004C1C / 0x03004C20. The old hardcode was the
        // vanilla 0x03005D8C, which on this ROM points at unrelated memory -
        // badges, heals and the route panel all read garbage through it.
        assertEquals(0x03004C1CL, t.map.saveBlock1Ptr)
        assertEquals(0x03004C20L, t.map.saveBlock2Ptr)
        assertEquals(0x020370D4L, t.map.mapHeader)
        assertEquals(0x0202431EL, t.map.battleOutcome)
        assertEquals(0x02038986L, t.map.trainerOpponent)
    }

    @Test
    fun `learnsets resolve to the 1_21 table and decode wide entries`() {
        val t = tracker() ?: run { println("SKIP: natdex rom missing"); return }
        assertEquals(0x0835879CL, t.map.levelUpLearnsets)
        assertTrue(t.map.learnsetWide)
        // Bulbasaur: a real learnset, not the zeros at the stale 1.13 address.
        val moves = t.learnset(1)
        assertTrue(moves.size >= 4, "expected a real learnset, got $moves")
        assertTrue(moves.all { it.first in 0..100 }, "levels sane: $moves")
        assertTrue(moves.all { it.second in 1..2000 }, "move ids sane: $moves")
        // Level-up lists are non-decreasing by level.
        assertTrue(moves.zipWithNext().all { (a, b) -> a.first <= b.first },
            "levels ordered: $moves")
    }

    @Test
    fun `Emerald Nat Dex uses the same 104-byte struct`() {
        val t = tracker() ?: run { println("SKIP: natdex rom missing"); return }
        // Reported against Emerald Nat. Dex too, with its own documented
        // addresses (party=020244D4 count=020244D1) - which is what ruled the
        // addresses out and pointed at the struct.
        assertEquals(104, t.map.monLayout.size)
        assertEquals(0x24, t.map.monLayout.enc)
        assertEquals(0x58, t.map.monLayout.level)
        // sizeofPokemonNickname, the ROM's byte at 0x08000176 (NatDexExtension.lua:17600): 12, not the games' 10 (rc32 audit P3 #112).
        assertEquals(12, t.map.monLayout.nickLen)
        assertEquals(10, GameMap.EMERALD_U.monLayout.nickLen)
    }

    @Test
    fun `fairy is type 18 and the chart knows it`() {
        // Reference TypeIndexMap: 0x12 = FAIRY. The old id 23 never occurs in
        // the ROM, so every fairy type displayed as "T18".
        assertEquals("Fairy", Gen3Types.name(18))
        // Fairy hits Dragon 2x; Dragon does 0x into Fairy.
        assertEquals(2.0, Gen3Types.effect(18, 16))
        assertEquals(0.0, Gen3Types.effect(16, 18))
    }

    @Test
    fun `on Nat Dex Fairy attacks, and Steel no longer resists Ghost or Dark`() {
        // rc33 audit P1 #70: every walk of the chart used Gen3Types.ALL, which has no Fairy, so Type Defenses, the info
        // screen's weaknesses and the move info never counted a Fairy attack; and the chart kept Gen 3's Steel resists,
        // which the expansion's tracker drops (NatDexExtension.lua updateMoveData).
        assertTrue("Fairy" in Gen3Types.defenses(16, 16, natDex = true).getValue(2.0), "Fairy hits a Dragon hard")
        kotlin.test.assertFalse("Fairy" in Gen3Types.defenses(16, 16).values.flatten(), "no Fairy in a Gen 3 game")
        val steel = Gen3Types.defenses(8, 8, natDex = true)
        kotlin.test.assertFalse("Ghost" in steel.getValue(0.5) || "Dark" in steel.getValue(0.5))
        assertTrue("Fairy" in steel.getValue(0.5))
        assertTrue(listOf("Ghost", "Dark").all { it in Gen3Types.defenses(8, 8).getValue(0.5) }, "Gen 3 keeps its resists")
        assertEquals(1.0, Gen3Types.effect(7, 8, 8, natDex = true))
        assertEquals(0.5, Gen3Types.effect(7, 8, 8))
        assertEquals(1.0, Gen3Types.effect(17, 8, false, natDex = true))
        assertEquals(0.0, Gen3Types.effect(7, 14, true), "Gen 1's chart is its own")
        assertEquals(Gen3Types.ALL + 18, Gen3Types.typesFor(true))
        assertEquals(Gen3Types.ALL, Gen3Types.typesFor(false))
    }

    @Test
    fun `evolutions and weights are the extension's on a Nat Dex ROM`() {
        // rc33 audit P1 #67: the base game's table was used, wrong for dozens of species and blank past 411.
        val t = tracker() ?: run { println("SKIP: natdex rom missing"); return }
        assertEquals("LINKING_CORD", t.evolution(64), "Kadabra")
        assertEquals("43", t.evolution(57), "Primeape")
        assertEquals("EEVEE_STONES_NATDEX", t.evolution(133), "Eevee")
        assertEquals("SHINY", t.evolution(176), "Togetic")
        assertEquals("RAZOR_FANG", t.evolution(207), "Gligar")
        assertEquals("18", t.evolution(412), "Turtwig")
        assertEquals(null, t.evolution(414), "Torterra")
        assertEquals("10.2", t.weight(412))
        val vanilla = GbaTracker(MemoryReader { _, _ -> ByteArray(0) }, GameMap.EMERALD_U)
        assertEquals("37", vanilla.evolution(64), "the base game keeps IronMON's level 37")
        assertEquals(null, vanilla.evolution(412))
    }
}
