package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** TypeDefensesScreen's buckets, with the Gen 1 tracker's three chart rules. */
class TypeDefensesTest {
    private val GHOST = 7; private val POISON = 3; private val PSYCHIC = 14; private val BUG = 6; private val GROUND = 4; private val NORMAL = 0; private val FIGHTING = 1

    @Test
    fun `Gengar in Gen 3 is weak to Psychic and Ground and immune to Normal and Fighting`() {
        val d = Gen3Types.defenses(GHOST, POISON)
        assertTrue(d.getValue(0.0).containsAll(listOf(Gen3Types.name(NORMAL), Gen3Types.name(FIGHTING))))
        assertTrue(d.getValue(2.0).containsAll(listOf(Gen3Types.name(PSYCHIC), Gen3Types.name(GROUND))))
        assertEquals(listOf(0.0, 0.25, 0.5, 2.0).filter { it in d.keys }, d.keys.toList())
    }

    @Test
    fun `the Gen 1 tracker's chart makes Ghost useless against Psychic and Bug and Poison hit each other hard`() {
        assertEquals(0.0, Gen3Types.effect(GHOST, PSYCHIC, gen1 = true))
        assertEquals(2.0, Gen3Types.effect(GHOST, PSYCHIC, gen1 = false))
        assertEquals(2.0, Gen3Types.effect(BUG, POISON, gen1 = true))
        assertEquals(2.0, Gen3Types.effect(POISON, BUG, gen1 = true))
        // A pure Psychic in Gen 1 takes nothing from Ghost: it lands in the 0x bucket.
        assertTrue(Gen3Types.name(GHOST) in Gen3Types.defenses(PSYCHIC, PSYCHIC, gen1 = true).getValue(0.0))
    }

    /** rc32 audit P2 #128: Red, Blue and Yellow have no ICE, FIRE row (pokered and pokeyellow data/types/type_matchups.asm). */
    @Test
    fun `in Gen 1 Fire takes Ice at 1x, so Charizard and Moltres are weak to it`() {
        val ice = 15; val fire = 10; val flying = 2
        assertEquals(1.0, Gen3Types.effect(ice, fire, gen1 = true))
        assertEquals(0.5, Gen3Types.effect(ice, fire, gen1 = false), "Gold, Silver and Crystal keep the resist (pokecrystal type_matchups.asm:42)")
        assertTrue(Gen3Types.name(ice) in Gen3Types.defenses(fire, flying, gen1 = true).getValue(2.0), "Charizard")
        assertTrue(Gen3Types.name(ice) !in Gen3Types.defenses(fire, fire, gen1 = true).getValue(0.5), "a pure Fire type no longer resists it")
        assertTrue(Gen3Types.name(ice) in Gen3Types.defenses(fire, fire, gen1 = false).getValue(0.5))
    }
}
