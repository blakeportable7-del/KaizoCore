package com.ironmonone.app

import com.ironmonone.tracker.BaseStats
import com.ironmonone.tracker.GameMap
import com.ironmonone.tracker.GbLookups
import com.ironmonone.tracker.GbaTracker
import com.ironmonone.tracker.MemoryReader
import com.ironmonone.tracker.MoveRow
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Evolution methods in the reference's words: the Pokemon info screen's "Fire Stone",
 * "5 Diff. Stones", "220 Friendship" (Utils.getDetailedEvolutionsInfo), and the log viewer's
 * short label under each evolution (LogTabPokemonDetails). The info screen printed the internal
 * keys ("fire", "eevee_stones", "friend") and the log viewer printed none until 2026-09-29.
 */
class EvolutionLabelsTest {
    private val gb = object : GbLookups {
        override val generation = 1
        override fun speciesName(species: Int) = "GB-$species"
        override fun baseStats(species: Int): BaseStats? = null
        override fun moveRowFor(id: Int): MoveRow? = null
        override fun moveDescription(id: Int): String? = null
        override fun weight(species: Int): String? = null
        override fun evolution(species: Int) = if (species == 25) "THUNDER" else null
        override fun effectivenessAgainst(species: Int) = emptyMap<Double, List<String>>()
        override fun learnLevels(species: Int) = emptyList<Int>()
    }

    @Test
    fun `the info screen's lines come from the tracker's evolution in the reference's words`() {
        val gba = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, GameMap.EMERALD_U)
        val l = PanelLookups({ gba }, { null })
        assertEquals(listOf("Level 16"), l.evolutionDetails(1))
        assertEquals(listOf("5 Diff. Stones"), l.evolutionDetails(133))
        assertEquals(listOf("220 Friendship"), l.evolutionDetails(42), "Golbat, at the requirement the ROM gives (220 by default)")
        assertEquals(listOf("Leaf Stone", "Sun Stone"), l.evolutionDetails(44))
        assertEquals(listOf("---"), l.evolutionDetails(3))
        assertEquals(listOf("Thunder Stone"), PanelLookups({ null }, { gb }).evolutionDetails(25), "the Game Boy trackers' spelling")
    }

    @Test
    fun `the log viewer labels each evolution with its method, and a pre-evolution with its method into this one`() {
        assertEquals("Water", LogEvoLabels.forward(listOf("Thunder", "Water", "Fire", "Sun", "Moon"), 1))
        assertEquals("Lv.16", LogEvoLabels.forward(listOf("Lv.16"), 2), "more evolutions than methods: the first")
        assertEquals("Fire", LogEvoLabels.into(listOf("Thunder", "Water", "Fire"), listOf("JOLTEON", "VAPOREON", "FLAREON"), "Flareon"))
        assertEquals("", LogEvoLabels.into(listOf("Lv.16"), listOf("IVYSAUR"), "Charmeleon"), "not listed: blank, as the reference leaves it")
    }

    @Test
    fun `the info screen and the log viewer draw the labels`() {
        val pc = File("src/main/kotlin/com/ironmonone/app/PcTracker.kt").readText().replace("\r\n", "\n")
        assertTrue("evolution.ifEmpty { listOf(\"---\") }.forEachIndexed { i, line -> PcInfoRow(if (i == 0) \"Evolves\" else \"\", line) }" in pc)
        assertFalse("evolution.lowercase()" in pc)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        assertEquals(2, Regex("onEvolution = panelLookups::evolutionDetails,").findAll(play).count())
        val viewer = File("src/main/kotlin/com/ironmonone/app/LogViewer.kt").readText().replace("\r\n", "\n")
        assertTrue("com.ironmonone.tracker.EvoText.short(t.evolution(it))" in viewer)
    }
}
