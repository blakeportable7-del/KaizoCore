package com.ironmonone.app

import com.ironmonone.tracker.RandomizedFlags
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * "Reveal info if randomized" off hides randomized types as the "?" icon where the reference
 * does: the viewed opponent on the tracker (TrackerScreen.lua:1135), the Notebook
 * (NotebookPokemonNoteView.lua:260) and the Pokemon info screen, except for your lead
 * (DataHelper.lua:432). Until 2026-09-29 the option hid only move facts.
 */
class RevealTypesTest {
    private val vanilla = RandomizedFlags(false, false, false, false, false, false, false, false, false, false)
    private val shuffled = RandomizedFlags(true, true, true, true, true, true, true, true, true, true)
    private val grassPoison = listOf("Grass" to 12, "Poison" to 3)

    @AfterTest fun defaults() {
        TrackerOptions.revealInfoIfRandomized = true
        TrackerOptions.showDataForVanillaGame = true
        TrackerOptions.openBookPlayMode = false
    }

    @Test
    fun `the opponent and the Notebook show one unknown icon for randomized types with the option off`() {
        assertEquals(grassPoison, InfoRules.typeIcons(grassPoison, InfoRules.hidesRandomizedTypes(shuffled)), "on by default: shown")
        TrackerOptions.revealInfoIfRandomized = false
        assertEquals(listOf("Unknown" to 9), InfoRules.typeIcons(grassPoison, InfoRules.hidesRandomizedTypes(shuffled)))
        assertTrue(InfoRules.hidesRandomizedTypes(null), "randomization unknown counts as randomized")
        assertFalse(InfoRules.hidesRandomizedTypes(vanilla), "unrandomized types show")
        TrackerOptions.openBookPlayMode = true
        assertTrue(InfoRules.hidesRandomizedTypes(shuffled), "TrackerScreen.lua:1135 does not check Open Book")
    }

    @Test
    fun `the info screen hides them too, except for your lead or where types may show`() {
        TrackerOptions.revealInfoIfRandomized = false
        assertTrue(InfoRules.infoScreenHidesTypes(shuffled, ownLead = false))
        assertFalse(InfoRules.infoScreenHidesTypes(shuffled, ownLead = true))
        assertFalse(InfoRules.infoScreenHidesTypes(vanilla, ownLead = false))
        TrackerOptions.openBookPlayMode = true
        assertFalse(InfoRules.infoScreenHidesTypes(shuffled, ownLead = false), "canShowUnknownTypes: Open Book shows them")
        TrackerOptions.openBookPlayMode = false
        TrackerOptions.revealInfoIfRandomized = true
        assertFalse(InfoRules.infoScreenHidesTypes(shuffled, ownLead = false))
    }

    @Test
    fun `the enemy card, the info screens and the Notebook draw types through the rules`() {
        val panel = File("src/main/kotlin/com/ironmonone/app/TrackerPanel.kt").readText().replace("\r\n", "\n")
        assertTrue("typeChips = InfoRules.typeIcons(GhostCard.types(e), InfoRules.hidesRandomizedTypes(rand))" in panel)
        assertTrue("InfoRules.infoScreenHidesTypes(state?.randomized, ownLead = species == state?.party?.firstOrNull()?.mon?.species)" in panel)
        assertEquals(3, Regex("types = infoTypes\\(").findAll(panel).count(), "the species, party and starter info screens")
        val notebook = File("src/main/kotlin/com/ironmonone/app/Notebook.kt").readText().replace("\r\n", "\n")
        assertTrue("InfoRules.hidesRandomizedTypes(tracker?.randomized())" in notebook)
    }
}
