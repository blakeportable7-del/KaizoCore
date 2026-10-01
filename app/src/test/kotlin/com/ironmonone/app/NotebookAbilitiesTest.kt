package com.ironmonone.app

import com.ironmonone.tracker.RandomizedFlags
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Notebook's Pokemon page names abilities the way NotebookPokemonNoteView.lua:273-298 does:
 * the ROM's abilities only where PokemonData.canShowUnknownAbilities allows, otherwise the
 * tracked ones. Until 2026-09-29 it fell back to the ROM's abilities whenever none were tracked,
 * so a randomized game's abilities showed before they were seen.
 */
class NotebookAbilitiesTest {
    private val vanilla = RandomizedFlags(false, false, false, false, false, false, false, false, false, false)
    private val shuffled = RandomizedFlags(true, true, true, true, true, true, true, true, true, true)
    private val rom = listOf("Static", "Lightning Rod")

    @AfterTest fun defaults() {
        TrackerOptions.showDataForVanillaGame = true
        TrackerOptions.openBookPlayMode = false
    }

    private fun lines(tracked: List<String>, rom: List<String>, flags: RandomizedFlags?) =
        notebookAbilityLines(tracked, rom, InfoRules.canShowAbilities(flags))

    @Test
    fun `a randomized game shows only what was tracked`() {
        assertEquals("---" to "---", lines(emptyList(), rom, shuffled), "nothing tracked: no spoiler")
        assertEquals("Static /" to "?", lines(listOf("Static"), rom, shuffled))
        assertEquals("Static /" to "Lightning Rod", lines(listOf("Static", "Lightning Rod"), rom, shuffled))
        assertEquals("---" to "---", lines(emptyList(), rom, null), "randomization unknown counts as randomized")
    }

    @Test
    fun `unrandomized abilities, or Open Book, show the ROM's`() {
        assertEquals("Static /" to "Lightning Rod", lines(emptyList(), rom, vanilla))
        assertEquals("Overgrow" to "---", lines(listOf("Overgrow"), listOf("Overgrow"), vanilla), "one ability: no slash, the second line blank")
        TrackerOptions.showDataForVanillaGame = false
        assertEquals("---" to "---", lines(emptyList(), rom, vanilla), "the vanilla option off hides them again")
        TrackerOptions.openBookPlayMode = true
        assertEquals("Static /" to "Lightning Rod", lines(emptyList(), rom, shuffled))
    }

    @Test
    fun `the page draws its two lines from the rule`() {
        val src = File("src/main/kotlin/com/ironmonone/app/Notebook.kt").readText().replace("\r\n", "\n")
        assertTrue(Regex("notebookAbilityLines\\(\\s*marks\\.abilitiesFor\\(id\\), tracker\\?\\.possibleAbilities\\(id\\) \\?: emptyList\\(\\),\\s*InfoRules\\.canShowAbilities\\(tracker\\?\\.randomized\\(\\)\\),\\s*\\)").containsMatchIn(src))
        assertTrue("line(\"Abilities\", ability1)" in src && "line(\"\", ability2)" in src)
    }
}
