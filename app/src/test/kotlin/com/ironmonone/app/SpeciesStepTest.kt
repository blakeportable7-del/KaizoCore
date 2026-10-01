package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** The info screen's previous/next arrows (InfoScreen.showNextPokemon). */
class SpeciesStepTest {
    @Test fun `Gen 3 steps over the empty 252-276 slots both ways`() {
        assertEquals(277, stepSpeciesId(251, 1, 411))
        assertEquals(251, stepSpeciesId(277, -1, 411))
        assertEquals(1, stepSpeciesId(411, 1, 411))
        assertEquals(411, stepSpeciesId(1, -1, 411))
    }

    @Test fun `Gold, Silver and Crystal wrap at 251, Red, Blue and Yellow at 151`() {
        assertEquals(1, stepSpeciesId(251, 1, 251))
        assertEquals(251, stepSpeciesId(1, -1, 251))
        assertEquals(1, stepSpeciesId(151, 1, 151))
    }

    @Test fun `the Nat Dex keeps going past 411`() {
        assertEquals(412, stepSpeciesId(411, 1, 1283))
        assertEquals(277, stepSpeciesId(251, 1, 1283))
    }
}
