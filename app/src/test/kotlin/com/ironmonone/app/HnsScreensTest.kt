package com.ironmonone.app

import com.ironmonone.core.RomKind
import com.ironmonone.tracker.GbaTracker
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Heart & Soul as a full KaizoCore game (docs/NEW-GAME-CHECKLIST.md, 2026-10-05): the Notebook, auto themes and Walking Pals by Heart & Soul's ids.
 */
class HnsScreensTest {
    private val hns = RomKind.HEARTSOUL_KAIZO_206
    private val books = File("src/main/assets/rulesets")



    @Test
    fun `the Notebook counts Heart and Soul's own Pokemon, Treecko at 252, no empty slot`() {
        val ids = NotebookSpecies.heartSoul
        assertTrue(252 in ids && 1 in ids, "Treecko and Bulbasaur")
        assertTrue(ids.none { HnsNumbers.toPack(it) == null }, "no empty slot (906 to 1025 are empty in Heart & Soul)")
        assertEquals(ids.size, ids.map { HnsNumbers.toPack(it) }.toSet().size, "one id per Pokemon or form the pack draws")
        assertTrue(ids.size in 1025..1430, "${ids.size}: every National Dex Pokemon, and the forms the pack draws apart")
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `an auto theme follows a Heart and Soul lead by the same Pokemon's Gen 3 id`() {
        try {
            AutoTheme.heartSoul = true
            assertEquals(AutoTheme.gbaTheme(277), AutoTheme.gbaThemeInPlay(252), "Treecko")
            assertEquals(AutoTheme.gbaTheme(1), AutoTheme.gbaThemeInPlay(1), "Bulbasaur")
            AutoTheme.heartSoul = false
            assertEquals(AutoTheme.gbaTheme(252), AutoTheme.gbaThemeInPlay(252), "any other game: its own id")
        } finally { AutoTheme.heartSoul = false }
        assertTrue(AutoTheme.gbaTheme(277) != null, "Treecko has a theme")
    }

    @Test
    fun `Castform's weather forms walk as Castform on Heart and Soul too`() {
        val layout = File("src/main/assets/hns/layout-kaizo.json").readText()
        fun species(n: String) = Regex("\"$n\":\\s*(\\d+)").find(layout)!!.groupValues[1].toInt()
        assertEquals(species("SPECIES_CASTFORM"), PalForms.CASTFORM_HNS)
        assertEquals(listOf(species("SPECIES_CASTFORM_SUNNY"), species("SPECIES_CASTFORM_RAINY"), species("SPECIES_CASTFORM_SNOWY")), PalForms.CASTFORM_WEATHER_HNS.toList())
        for (f in PalForms.CASTFORM_WEATHER_HNS) assertEquals(PalForms.CASTFORM_HNS, PalForms.overworld(f, WalkingPals.Dex.HNS))
        assertEquals(1162, PalForms.overworld(1162, WalkingPals.Dex.HNS), "Nat. Dex's ids are not Heart & Soul's")
    }
}
