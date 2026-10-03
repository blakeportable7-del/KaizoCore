package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals

/** MiscUtils.calculateExperiencePercent and DrawingUtils.drawExperienceBar, as the DS tracker computes them. */
class NdsExperienceTest {
    @Test
    fun `the reference's Fluctuating totals, its own floating point included`() {
        // 5^3 x (6/3 + 24) / 50 = 65; 6^3 x (7/3 + 24) / 50 = 113.76, floored.
        assertEquals(65L, NdsExperience.fluctuatingAt(5))
        assertEquals(113L, NdsExperience.fluctuatingAt(6))
        // 20^3 x (20 + 14) / 50 and 50^3 x (25 + 32) / 50.
        assertEquals(5440L, NdsExperience.fluctuatingAt(20))
        assertEquals(142500L, NdsExperience.fluctuatingAt(50))
        assertEquals(0L, NdsExperience.fluctuatingAt(100))
    }

    @Test
    fun `the fraction through the level, and the bar's width`() {
        assertEquals(0.5, NdsExperience.fraction(5, 89))
        assertEquals(0.0, NdsExperience.fraction(5, 0), "no experience read: an empty bar")
        assertEquals(28, NdsExperience.barWidth(0.5))
        assertEquals(57, NdsExperience.barWidth(2.0), "clamped at full")
        // Level 99 (at least 1,581,587 by this formula): the next total reads 0, the fraction
        // goes below 0 and nothing is drawn. Level 100 divides by 0: a full bar.
        assertEquals(0, NdsExperience.barWidth(NdsExperience.fraction(99, 1_600_000)))
        assertEquals(57, NdsExperience.barWidth(NdsExperience.fraction(100, 1_640_000)))
    }

    /**
     * rc32 audit P3 #118: every growth rate, against the games' totals (pokecrystal data/growth_rates.asm for the four Gen 2
     * ones, the Gen 3 table in Complete-Fire-Red-Upgrade src/Tables/experience_tables.c for Erratic).
     */
    @Test
    fun `each growth rate's totals are the games' own`() {
        assertEquals(27_000L, NdsExperience.totalAt(NdsExperience.MEDIUM_FAST, 30))
        assertEquals(21_760L, NdsExperience.totalAt(NdsExperience.MEDIUM_SLOW, 30))
        assertEquals(9L, NdsExperience.totalAt(NdsExperience.MEDIUM_SLOW, 2))
        assertEquals(0L, NdsExperience.totalAt(NdsExperience.MEDIUM_SLOW, 1), "the formula's -54, as 0")
        assertEquals(21_600L, NdsExperience.totalAt(NdsExperience.FAST, 30))
        assertEquals(33_750L, NdsExperience.totalAt(NdsExperience.SLOW, 30))
        assertEquals(1_250_000L, NdsExperience.totalAt(NdsExperience.SLOW, 100))
        // Erratic's four pieces: 2, 50 and 51, 69, 99 and 100.
        assertEquals(listOf(15L, 125_000L, 131_324L, 267_406L, 591_882L, 600_000L),
            listOf(2, 50, 51, 69, 99, 100).map { NdsExperience.totalAt(NdsExperience.ERRATIC, it) })
        assertEquals(NdsExperience.fluctuatingAt(20), NdsExperience.totalAt(NdsExperience.FLUCTUATING, 20), "the reference's own")
        assertEquals(null, NdsExperience.totalAt(6, 30))
    }

    @Test
    fun `the fraction through the level goes by the growth rate`() {
        assertEquals(0.0, NdsExperience.fraction(30, 27_000, NdsExperience.MEDIUM_FAST))
        assertEquals(0.5, NdsExperience.fraction(30, 23_027, NdsExperience.MEDIUM_SLOW)!!, 0.001, "halfway from 21,760 to 24,294")
        assertEquals(NdsExperience.fraction(5, 89), NdsExperience.fraction(5, 89, NdsExperience.FLUCTUATING), "Fluctuating is the reference's")
        assertEquals(1.0, NdsExperience.fraction(100, 1_000_000, NdsExperience.MEDIUM_FAST), "full at 100, as the reference's is")
        assertEquals(0.0, NdsExperience.fraction(30, 0, NdsExperience.SLOW), "no experience read: an empty bar")
        assertEquals(null, NdsExperience.fraction(30, 27_000, 9))
    }

    /** The sidecar the engine writes beside a DS run carries the growth rate last; one written before it gives -1. */
    @Test
    fun `the sidecar's growth rate column is read, and an older sidecar has none`() {
        val f = java.io.File.createTempFile("species", ".tsv")
        try {
            f.writeText("25\tPikachu\tELECTRIC\t\t320\tStatic\t\t0\n41\tZubat\tPOISON\tFLYING\t245\tInner Focus\t\n")
            val t = NdsTracker({ _, n -> ByteArray(n) }, f)
            assertEquals(NdsExperience.MEDIUM_FAST, t.speciesInfoFor(25)?.growthRate)
            assertEquals(-1, t.speciesInfoFor(41)?.growthRate)
        } finally { f.delete() }
    }

    @Test
    fun `experience is read from block A`() {
        val m = Gen4.decodeParty(Gen4.encodeParty(0x3456L, 25, 5, 20, 20, listOf(84, 0, 0, 0), experience = 89))!!
        assertEquals(89L, m.experience)
        val g5 = Gen4.decodeParty(Gen4.encodeParty(0x3456L, 25, 5, 20, 20, listOf(84, 0, 0, 0), gen5 = true, experience = 1234567), gen5 = true)!!
        assertEquals(1234567L, g5.experience)
    }
}
