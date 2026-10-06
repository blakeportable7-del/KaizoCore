package com.ironmonone.tracker.nuzlocke

import com.ironmonone.tracker.CalcAtk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Survival chances (2026-10-06): the rolls by hand, Calc Atk's range for the same hit, and the words. */
class NuzlockeOddsTest {

    private val hit = NuzlockeOdds.Hit(generation = 3, level = 50, power = 100, attack = 100, defense = 100, stab = false, effectiveness = 1.0)

    @Test
    fun `Gen 3 rolls by hand`() {
        // floor(floor(22 * 100 * 100 / 100) / 50) = 44, + 2 = 46; 46 * 85..100 / 100.
        val r = NuzlockeOdds.rolls(hit, crit = false)
        assertEquals(16, r.size)
        assertEquals(39, r.first()); assertEquals(46, r.last())
        // The same range Calc Atk works backwards from.
        assertEquals(39 to 46, CalcAtk.damageRange(CalcAtk.Inputs(level = 50, damage = 0, defense = 100, power = 100), 100))
    }

    @Test
    fun `survival counts every roll and the critical hit`() {
        // At 46 HP only the top roll faints it, and every critical hit does.
        val o = NuzlockeOdds.odds(hit, hp = 46, maxHp = 46, critChance = 1.0 / 16)
        assertEquals(15.0 / 16, o.surviveNoCrit)
        assertEquals(0.0, o.surviveCrit)
        assertEquals(15.0 / 16 * 15.0 / 16, o.survive, 1e-9)
        assertEquals("88 in 100", NuzlockeOdds.inHundred(o.survive))
        assertTrue(NuzlockeOdds.words(o).startsWith("Survives one hit 88 in 100."))
    }

    @Test
    fun `plain words at the ends`() {
        val always = NuzlockeOdds.odds(hit, hp = 200, maxHp = 200, critChance = 1.0 / 16)
        assertEquals("Always survives one hit. Takes 19 to 23% of its HP, 5 to 6 hits from full.", NuzlockeOdds.words(always))
        val critOnly = NuzlockeOdds.odds(hit, hp = 50, maxHp = 50, critChance = 1.0 / 16)
        assertTrue("only a critical hit faints it" in NuzlockeOdds.words(critOnly))
        val immune = NuzlockeOdds.odds(hit.copy(effectiveness = 0.0), hp = 10, maxHp = 10, critChance = 1.0 / 16)
        assertEquals("Does nothing to it.", NuzlockeOdds.words(immune))
        assertEquals("1 in 100", NuzlockeOdds.inHundred(0.001))
        assertEquals("99 in 100", NuzlockeOdds.inHundred(0.999))
    }

    @Test
    fun `Game Boy rolls run 217 to 255 out of 255`() {
        val r = NuzlockeOdds.rolls(hit.copy(generation = 2), crit = false)
        assertEquals(39, r.size)
        assertEquals(46 * 217 / 255, r.first()); assertEquals(46, r.last())
    }

    @Test
    fun `stat formula`() {
        // Base 100, IV 31, no EVs, level 50, neutral: (200 + 31) * 50 / 100 + 5 = 120.
        assertEquals(120, NuzlockeOdds.stat(100, 31, 0, 50))
        assertEquals(175, NuzlockeOdds.stat(100, 31, 0, 50, hp = true))
        assertEquals(132, NuzlockeOdds.stat(100, 31, 0, 50, nature = 1.1))
    }
}
