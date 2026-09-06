package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The coverage calculator, against the real Emerald ROM.
 *
 * This is the PC tracker's Coverage Calculator: for every species in the game,
 * the best multiplier your moveset gets against its type pair. It runs off the
 * live base-stat table, so it is only as right as the type reads are - which is
 * why these assert against species whose real types are known rather than
 * against a total that any bug could still produce.
 */
class CoverageTest {

    private val cleanRom = File(
        "C:\\Users\\bepor\\IronMonOne\\.vendor\\clean-emerald" +
            "\\Pokemon - Emerald Version (USA, Europe).gba"
    )

    private fun romReader(rom: ByteArray) = MemoryReader { address, length ->
        val off = (address - 0x08000000L).toInt()
        if (address >= 0x08000000L && off >= 0 && off + length <= rom.size)
            rom.copyOfRange(off, off + length)
        else ByteArray(0)
    }

    private fun tracker(): GbaTracker? {
        if (!cleanRom.exists()) return null
        return GbaTracker(romReader(cleanRom.readBytes()))
    }

    private val GROUND = 4
    private val ELECTRIC = 13
    private val NORMAL = 0
    private val WATER = 11
    private val ICE = 15

    @Test
    fun `an electric-only moveset cannot touch ground types`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        val cov = t.coverage(listOf(ELECTRIC))
        val immune = cov[0.0] ?: emptyList()

        // Geodude (74) and Sandshrew (27) are Ground; an Electric move does
        // nothing to either. If the type read were broken these would land in
        // some other bucket and the test would fail rather than pass quietly.
        assertTrue(74 in immune, "Geodude must be immune to an Electric-only moveset")
        assertTrue(27 in immune, "Sandshrew must be immune to an Electric-only moveset")

        // And Pikachu (25), a pure Electric type, is not immune - Electric is
        // merely resisted, so it belongs in the half bucket, not the zero one.
        assertTrue(25 !in immune)
        assertTrue(25 in (cov[0.5] ?: emptyList()))
    }

    @Test
    fun `adding a second type closes the hole`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        val electricOnly = (t.coverage(listOf(ELECTRIC))[0.0] ?: emptyList()).size
        val withIce = (t.coverage(listOf(ELECTRIC, ICE))[0.0] ?: emptyList()).size

        // Ice hits Ground for double, so every Ground type that Electric could
        // not touch is now covered. The zero bucket must shrink, and this is the
        // whole point of the screen.
        assertTrue(
            withIce < electricOnly,
            "Ice coverage must reduce the immune count ($withIce vs $electricOnly)",
        )
    }

    @Test
    fun `a normal-only moseset is walled by ghosts`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        val immune = t.coverage(listOf(NORMAL))[0.0] ?: emptyList()
        assertTrue(92 in immune, "Gastly is Ghost and takes nothing from Normal")
        assertTrue(93 in immune, "Haunter likewise")
    }

    @Test
    fun `every species lands in exactly one bucket, and none are lost`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        val cov = t.coverage(listOf(WATER))
        val all = cov.values.flatten()
        assertEquals(all.size, all.toSet().size, "a species must not appear twice")
        // Sanity on scale: Gen 3 has 386 real species, and the walk skips the
        // unused 252..276 block. A count near zero would mean the base-stat read
        // failed and every bucket came back empty, which must not read as "clean".
        assertTrue(all.size > 300, "expected the full dex, got ${all.size}")
    }

    @Test
    fun `no move types means no coverage claimed`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        val cov = t.coverage(emptyList())
        assertEquals(0, cov.values.sumOf { it.size })
    }
}
