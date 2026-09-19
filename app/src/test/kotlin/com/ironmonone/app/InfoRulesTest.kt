package com.ironmonone.app

import com.ironmonone.tracker.RandomizedFlags
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PokemonData.canShowUnknown* and "Reveal info if randomized" (DataHelper.lua). */
class InfoRulesTest {
    private val vanilla = RandomizedFlags(false, false, false, false, false, false, false, false, false, false)
    private val shuffled = RandomizedFlags(true, true, true, true, true, true, true, true, true, true)

    @AfterTest fun defaults() {
        TrackerOptions.showDataForVanillaGame = true
        TrackerOptions.openBookPlayMode = false
        TrackerOptions.revealInfoIfRandomized = true
    }

    @Test
    fun `a vanilla game shows the opponent in full, a randomized one does not`() {
        assertTrue(InfoRules.canShowAbilities(vanilla))
        assertTrue(InfoRules.canShowStats(vanilla))
        assertTrue(InfoRules.canShowMoves(vanilla))
        assertFalse(InfoRules.canShowAbilities(shuffled))
        assertFalse(InfoRules.canShowStats(shuffled))
        assertFalse(InfoRules.canShowMoves(shuffled))
    }

    @Test
    fun `unknown randomization reveals nothing`() {
        assertFalse(InfoRules.canShowStats(null))
        TrackerOptions.revealInfoIfRandomized = false
        assertTrue(InfoRules.hideOwnEffectiveness(null))
        assertEquals(shuffled, InfoRules.hiddenMoveInfo(null))
    }

    @Test
    fun `the vanilla option off hides even a vanilla game, Open Book shows even a randomized one`() {
        TrackerOptions.showDataForVanillaGame = false
        assertFalse(InfoRules.canShowMoves(vanilla))
        TrackerOptions.openBookPlayMode = true
        assertTrue(InfoRules.canShowMoves(shuffled))
    }

    @Test
    fun `reveal off hides the randomized move facts, and Open Book overrides it`() {
        assertNull(InfoRules.hiddenMoveInfo(shuffled))
        TrackerOptions.revealInfoIfRandomized = false
        assertEquals(shuffled, InfoRules.hiddenMoveInfo(shuffled))
        assertTrue(InfoRules.hideOwnEffectiveness(shuffled))
        assertFalse(InfoRules.hideOwnEffectiveness(vanilla))
        TrackerOptions.openBookPlayMode = true
        assertNull(InfoRules.hiddenMoveInfo(shuffled))
    }
}
