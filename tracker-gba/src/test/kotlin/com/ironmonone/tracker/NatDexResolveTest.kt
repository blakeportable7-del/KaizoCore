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
}
