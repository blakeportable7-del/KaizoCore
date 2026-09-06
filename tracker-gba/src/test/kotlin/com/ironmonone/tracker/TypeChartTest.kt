package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Gen 3 type chart is hand-transcribed data, which is exactly the kind of
 * thing that is wrong in one cell and looks right everywhere on screen. These
 * cover the matchups that actually decide IronMON fights, plus the structural
 * properties that catch a whole row being mistyped.
 */
class TypeChartTest {

    private val NORMAL = 0; private val FIGHTING = 1; private val FLYING = 2
    private val POISON = 3; private val GROUND = 4; private val ROCK = 5
    private val BUG = 6; private val GHOST = 7; private val STEEL = 8
    private val FIRE = 10; private val WATER = 11; private val GRASS = 12
    private val ELECTRIC = 13; private val PSYCHIC = 14; private val ICE = 15
    private val DRAGON = 16; private val DARK = 17

    @Test
    fun `the three immunities that get people killed`() {
        // Reaching for a STAB move that does literally nothing is how runs end.
        assertEquals(0.0, Gen3Types.effect(ELECTRIC, GROUND))
        assertEquals(0.0, Gen3Types.effect(NORMAL, GHOST))
        assertEquals(0.0, Gen3Types.effect(GHOST, NORMAL))
        assertEquals(0.0, Gen3Types.effect(FIGHTING, GHOST))
        assertEquals(0.0, Gen3Types.effect(POISON, STEEL))
        assertEquals(0.0, Gen3Types.effect(GROUND, FLYING))
    }

    @Test
    fun `psychic is immune to dark, which is a Gen 2 onwards rule`() {
        assertEquals(0.0, Gen3Types.effect(PSYCHIC, DARK))
        // But Dark hits Psychic hard, which is not symmetric.
        assertEquals(2.0, Gen3Types.effect(DARK, PSYCHIC))
    }

    @Test
    fun `steel resists most of the chart`() {
        // Steel's resistance list is the longest in the game and the easiest to
        // mistype, so check its shape rather than one cell.
        val resisted = Gen3Types.ALL.count { Gen3Types.effect(it, STEEL) < 1.0 }
        assertEquals(12, resisted)
        assertEquals(2.0, Gen3Types.effect(FIGHTING, STEEL))
        assertEquals(2.0, Gen3Types.effect(FIRE, STEEL))
        assertEquals(2.0, Gen3Types.effect(GROUND, STEEL))
    }

    @Test
    fun `dual types multiply`() {
        // Fire/Flying takes quadruple from Rock, Grass/Flying from Ice.
        assertEquals(4.0, Gen3Types.effect(ROCK, FIRE, FLYING))
        assertEquals(4.0, Gen3Types.effect(ICE, GRASS, FLYING))
        // Rock/Flying is NOT 4x to Electric: Electric is neutral on Rock. This
        // one is a common mis-memory and was wrong in this test first time.
        assertEquals(2.0, Gen3Types.effect(ELECTRIC, ROCK, FLYING))
        // And an immunity beats any doubling on the other half.
        assertEquals(0.0, Gen3Types.effect(GROUND, FLYING, ROCK))
        // A single-typed defender must not be counted twice.
        assertEquals(2.0, Gen3Types.effect(WATER, FIRE, FIRE))
    }

    @Test
    fun `neutral is the default, not a hole in the table`() {
        assertEquals(1.0, Gen3Types.effect(NORMAL, WATER))
        assertEquals(1.0, Gen3Types.effect(DRAGON, WATER))
        assertEquals(0.5, Gen3Types.effect(DRAGON, STEEL))
        assertEquals(2.0, Gen3Types.effect(DRAGON, DRAGON))
    }

    @Test
    fun `every multiplier is one of the four legal values`() {
        for (a in Gen3Types.ALL) {
            for (d in Gen3Types.ALL) {
                val m = Gen3Types.effect(a, d)
                if (m != 0.0 && m != 0.5 && m != 1.0 && m != 2.0) {
                    throw AssertionError("bad multiplier $m for $a vs $d")
                }
            }
        }
    }

    @Test
    fun `the chart has the expected number of non-neutral cells`() {
        // 110 non-neutral entries, counted from the reference tracker's own
        // MoveData.TypeToEffectiveness. A dropped or duplicated row moves this
        // count even when every spot-check above still passes.
        var n = 0
        for (a in Gen3Types.ALL) for (d in Gen3Types.ALL) {
            if (Gen3Types.effect(a, d) != 1.0) n++
        }
        assertEquals(110, n)
    }
}
