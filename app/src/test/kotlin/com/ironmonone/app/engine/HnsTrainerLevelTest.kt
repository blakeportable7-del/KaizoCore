package com.ironmonone.app.engine

import com.ironmonone.app.engine.hns.HnsRandomizer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * HnsRandomizer.trainerLevel (Blake, 2026-10-11): a flat +60% put almost all of Kanto on the cap at 100, ten teams
 * entirely. The raise now eases off above base level 50 so only Red's strongest (base 93) reaches 100.
 */
class HnsTrainerLevelTest {
    private fun flat(level: Int, mod: Int) = minOf(100, Math.round(level * (1 + mod / 100.0)).toInt())

    @Test
    fun `up to base level 50 the raise is the plain percent, so Johto is unchanged`() {
        for (mod in listOf(10, 50, 60)) for (l in 1..50) assertEquals(flat(l, mod), HnsRandomizer.trainerLevel(l, mod), "base $l at +$mod%")
    }

    @Test
    fun `Kaizo's Kanto gym leaders land between 85 and 89 and only Red's strongest reaches 100`() {
        // The leaders' strongest at base level: Surge 60, Erika 62, Misty 63, Janine 64, Sabrina and Brock 66, Blaine 67, Blue 69.
        val leaders = listOf(60, 62, 63, 64, 66, 66, 67, 69).map { HnsRandomizer.trainerLevel(it, 60) }
        assertEquals(listOf(85, 86, 86, 87, 87, 87, 88, 89), leaders)
        assertEquals(100, HnsRandomizer.trainerLevel(93, 60), "Red's strongest")
        // Base 92 rounds up to 100 (99.5); no Heart & Soul trainer has a Pokemon at base 90 to 92 (Red's others are 77-80).
        assertTrue((51..91).all { HnsRandomizer.trainerLevel(it, 60) < 100 }, "nothing below base 92 reaches 100")
    }

    @Test
    fun `never above the plain percent, never lower for a stronger trainer, and a lowered level is unchanged`() {
        for (mod in -50..100) {
            var last = 0
            for (l in 1..100) {
                val got = HnsRandomizer.trainerLevel(l, mod)
                assertTrue(got <= flat(l, mod), "base $l at $mod%: $got above ${flat(l, mod)}")
                assertTrue(got >= last, "base $l at $mod%: $got below $last")
                if (mod <= 0) assertEquals(flat(l, mod), got)
                last = got
            }
        }
    }
}
