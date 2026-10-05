package com.ironmonone.tracker

import com.ironmonone.tracker.nuzlocke.NuzlockeFamilies
import com.ironmonone.tracker.nuzlocke.NuzlockeSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul's species numbering for the app's screens and the Nuzlocke engine (HnsSpecies, 2026-10-05). Its ids are
 * the expansion's, which part from Gen 3's (and the Nat. Dex build's) at 252, so every place that pictures or groups
 * species goes through the Nat. Dex id of the same Pokemon.
 */
class HnsSpeciesTest {
    @Test
    fun `the ids run to 1572 with the egg after them, and each maps to the Nat Dex build's id of the same Pokemon`() {
        assertEquals(1572, HnsSpecies.TOTAL)
        assertEquals(1573, HnsSpecies.EGG)
        assertEquals(1284, HnsSpecies.natDexId(HnsSpecies.EGG), "the sprite pack's egg")
        assertEquals(1, HnsSpecies.natDexId(1))
        assertEquals(25, HnsSpecies.natDexId(25))
        // Heart & Soul's 252 is Treecko; Gen 3 and the Nat. Dex build keep it at 277 (252 to 276 are empty slots there).
        assertEquals(277, HnsSpecies.natDexId(252))
        assertEquals(412, HnsSpecies.natDexId(387), "Turtwig")
    }

    @Test
    fun `the Nuzlocke dupes clause sees Heart and Soul's evolution lines`() {
        val treecko = Gen3Nuzlocke.engineSpecies(252, hns = true)
        val grovyle = Gen3Nuzlocke.engineSpecies(253, hns = true)
        assertTrue(NuzlockeFamilies.sameLine(treecko, grovyle, NuzlockeSystem.GEN3), "Treecko and Grovyle are one line")
        assertFalse(NuzlockeFamilies.sameLine(treecko, Gen3Nuzlocke.engineSpecies(255, hns = true), NuzlockeSystem.GEN3), "Torchic is not")
        // Any other game keeps its own numbers.
        assertEquals(252, Gen3Nuzlocke.engineSpecies(252, hns = false))
    }
}
