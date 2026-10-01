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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The panel's lookups on a Game Boy game. PlayScreen handed the panel
 * trackerRef's answers only, and trackerRef is the Gen 3 tracker, null on
 * Red to Crystal: move summaries, weight, evolution, weaknesses and the
 * "Moves x/y (next)" levels were all empty there (parity audit 2026-09-28).
 */
class PanelLookupsTest {

    /** A Game Boy tracker's answers, one distinctive value each. */
    private val gb = object : GbLookups {
        override val generation = 1
        override fun speciesName(species: Int) = "GB-$species"
        override fun baseStats(species: Int): BaseStats? = null
        override fun moveRowFor(id: Int): MoveRow? = null
        override fun moveDescription(id: Int) = "gb move $id"
        override fun weight(species: Int) = "1.$species"
        override fun evolution(species: Int) = if (species == 1) "16" else null
        override fun effectivenessAgainst(species: Int) = mapOf(0.0 to listOf("Ghost"))
        override fun learnLevels(species: Int) = listOf(7, 13)
    }

    @Test
    fun `with no Gen 3 tracker the Game Boy tracker answers every lookup`() {
        val l = PanelLookups({ null }, { gb })
        assertEquals("gb move 67", l.moveDescription(67))
        assertEquals("1.4", l.weight(4)); assertEquals("16", l.evolution(1)); assertNull(l.evolution(3))
        assertEquals(mapOf(0.0 to listOf("Ghost")), l.effectiveness(64))
        assertEquals(listOf(7, 13), l.moveLevels(1))
        assertEquals("GB-25", l.speciesName(25))
    }

    @Test
    fun `a Gen 3 tracker's answer stands, null included, so GBA games are unchanged`() {
        val nothing = MemoryReader { _, len -> ByteArray(len) }
        val gba = GbaTracker(nothing, GameMap.EMERALD_U)
        val l = PanelLookups({ gba }, { gb })
        assertEquals(gba.weight(1), l.weight(1)); assertEquals("6.9", l.weight(1))
        assertEquals(gba.evolution(3), l.evolution(3)); assertNull(l.evolution(3), "Venusaur does not evolve: not the Game Boy answer")
        assertEquals(gba.moveDescription(1), l.moveDescription(1))
        assertTrue(l.moveLevels(1).isEmpty(), "no learnset in blank memory; the Game Boy list must not leak in")
    }

    @Test
    fun `PlayScreen routes both tracker panels and the Game Boy poller through the lookups`() {
        val src = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        val panels = Regex("\\bTrackerPanel\\(").findAll(src).map { m -> src.substring(m.range.first, (m.range.first + 6000).coerceAtMost(src.length)) }.toList()
        assertEquals(2, panels.size, "the portrait and the landscape panel")
        for (call in panels) {
            for (arg in listOf("onMoveDescription", "onWeight", "onEvolution", "onEffectiveness", "onMoveLevels", "onSpeciesBase", "onSpeciesName")) {
                val line = Regex("\\b$arg = ([^\\n]*)").find(call)?.groupValues?.get(1)
                assertTrue(line != null && line.startsWith("panelLookups::"), "$arg is wired to: $line")
            }
        }
        assertTrue(Regex("gbRef = gbc \\?: gb1").containsMatchIn(src), "the Game Boy poller hands its tracker to the lookups")
    }
}
