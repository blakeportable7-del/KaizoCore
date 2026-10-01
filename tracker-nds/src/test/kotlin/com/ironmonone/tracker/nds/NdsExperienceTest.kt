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

    @Test
    fun `experience is read from block A`() {
        val m = Gen4.decodeParty(Gen4.encodeParty(0x3456L, 25, 5, 20, 20, listOf(84, 0, 0, 0), experience = 89))!!
        assertEquals(89L, m.experience)
        val g5 = Gen4.decodeParty(Gen4.encodeParty(0x3456L, 25, 5, 20, 20, listOf(84, 0, 0, 0), gen5 = true, experience = 1234567), gen5 = true)!!
        assertEquals(1234567L, g5.experience)
    }
}
