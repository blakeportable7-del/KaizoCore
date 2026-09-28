package com.ironmonone.editor

import com.dabomstew.pkrandom.Settings
import java.io.File
import java.io.FileInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Proves the reflected editor model actually drives the engine: options enumerate,
 * mutations change the settings string, and everything survives the string round-trip
 * that the engine itself uses for presets.
 */
class SettingsReflectorTest {

    private val kaizo = File(
        "C:\\PokemonIronmon\\EmeraldNatDex\\Tracker\\Ironmon-Tracker\\extensions\\natdex" +
            "\\rnqs_files\\RSE NatDex v1.2 Kaizo.rnqs"
    )

    @Test
    fun `enumerates a real editor surface`() {
        val opts = SettingsReflector.options(Settings::class.java)
        val bools = opts.count { it is Option.Bool }
        val ints = opts.count { it is Option.IntValue }
        val choices = opts.count { it is Option.Choice }
        println("editor surface: ${opts.size} options ($bools bool, $ints int, $choices choice)")

        assertTrue(opts.size > 100, "expected >100 options, got ${opts.size}")
        assertTrue(choices >= 20, "expected the ~22 mode enums, got $choices")
        // Every ZX section is populated - an empty section means the routing regressed.
        val bySection = SettingsReflector.bySection(Settings::class.java)
        Section.entries.forEach { s ->
            assertTrue(!bySection[s].isNullOrEmpty(), "section ${s.title} is empty")
        }
        bySection.forEach { (s, o) -> println("  ${s.title}: ${o.size}") }
    }

    /**
     * A bare Settings() has null internals (romName, selectedEXPCurve) and cannot
     * serialize — the engine only ever builds Settings by loading a preset, and so
     * does the app. The editor's contract is "edit a loaded preset", so the tests
     * start from the real Kaizo file and skip when it is absent.
     */
    private fun loadedSettings(): Settings? {
        if (!kaizo.exists()) { println("SKIP: Kaizo rnqs not present"); return null }
        return FileInputStream(kaizo).use { Settings.read(it) }
    }

    @Test
    fun `mutating a boolean changes the settings string and round-trips`() {
        val s = loadedSettings() ?: return
        val opt = SettingsReflector.options(Settings::class.java)
            .filterIsInstance<Option.Bool>()
            .first { it.id == "randomizeMovePowers" }

        val before = s.toString()
        opt.set(s, !opt.get(s))
        val after = s.toString()
        assertNotEquals(before, after, "flipping an option must change the settings string")

        val reloaded = Settings.fromString(after)
        assertEquals(opt.get(s), opt.get(reloaded), "value must survive the round-trip")
    }

    @Test
    fun `mutating a mode enum round-trips`() {
        val s = loadedSettings() ?: return
        val opt = SettingsReflector.options(Settings::class.java)
            .filterIsInstance<Option.Choice>()
            .first { it.id == "trainersMod" }
        val target = opt.values.first { it != opt.get(s) }

        opt.set(s, target)
        val reloaded = Settings.fromString(s.toString())
        assertEquals(target, opt.get(reloaded))
    }

    @Test
    fun `edits the real Kaizo preset and round-trips it`() {
        if (!kaizo.exists()) { println("SKIP: Kaizo rnqs not present"); return }
        val s = FileInputStream(kaizo).use { Settings.read(it) }

        val opts = SettingsReflector.options(Settings::class.java)
        // Read every option once: any getter/setter mismatch throws here, not in the UI.
        opts.forEach { o ->
            when (o) {
                is Option.Bool -> o.get(s)
                is Option.IntValue -> o.get(s)
                is Option.Choice -> assertTrue(o.get(s) in o.values, "${o.id} value not in enum")
            }
        }

        val flip = opts.filterIsInstance<Option.Bool>().first { it.id == "randomizeWildPokemonHeldItems" }
        flip.set(s, !flip.get(s))
        val reloaded = Settings.fromString(s.toString())
        assertEquals(flip.get(s), flip.get(reloaded))
        println("real Kaizo preset: ${opts.size} options readable, edit round-trips")
    }

    @Test
    fun `labels read like settings, not code`() {
        assertEquals("Randomize move powers", SettingsReflector.prettify("randomizeMovePowers"))
        // Plural acronyms, as they read on the phone before 2026-09-27: "Keep field move t ms", "TMS HMS compatibility mod".
        assertEquals("Keep field move TMs", SettingsReflector.prettify("keepFieldMoveTMs"))
        // Gen 6 and 7 settings are hidden for every game the app runs.
        assertTrue(SettingsReflector.unsupportedIn("allowTotemAltFormes", "GBA3"))
        assertTrue(SettingsReflector.unsupportedIn("swapTrainerMegaEvos", "NDS5"))
        assertFalse(SettingsReflector.unsupportedIn("allowTotemAltFormes", null))
        assertEquals("TMs HMs compatibility mod", SettingsReflector.prettify("tmsHmsCompatibilityMod"))
        assertEquals("TMs force good damaging", SettingsReflector.prettify("tmsForceGoodDamaging"))
        assertEquals("TM levels", SettingsReflector.prettify("tmLevels"))
        // Ranges from the desktop randomizer, not 0..255 for everything.
        assertEquals(0..100, SettingsReflector.rangeOf("tmsGoodDamagingPercent"))
        assertEquals(-50..50, SettingsReflector.rangeOf("wildLevelModifier"))
        assertEquals(2..4, SettingsReflector.rangeOf("guaranteedMoveCount"))
        assertEquals(0..5, SettingsReflector.rangeOf("additionalBossTrainerPokemon"))
        assertEquals("Random every level", SettingsReflector.prettifyEnum("RANDOM_EVERY_LEVEL"))
    }

    @Test
    fun `the fallback joins every plural acronym and splits numbers off`() {
        // As the audit read them on 2026-09-27: "i vs", "o ts", "p ps", "op shop", "Limit600".
        assertEquals("Randomize in game trades IVs", SettingsReflector.prettify("randomizeInGameTradesIVs"))
        assertEquals("Randomize in game trades OTs", SettingsReflector.prettify("randomizeInGameTradesOTs"))
        assertEquals("Randomize move PPs", SettingsReflector.prettify("randomizeMovePPs"))
        assertEquals("Ban OP shop items", SettingsReflector.prettify("banOPShopItems"))
        assertEquals("Limit 600", SettingsReflector.prettify("limit600"))
        assertEquals("Elite Four unique Pok\u00e9mon number", SettingsReflector.prettify("eliteFourUniquePokemonNumber"))
    }

    @Test
    fun `labels come from the desktop, and switch and number are told apart`() {
        val opts = SettingsReflector.options(Settings::class.java).associateBy { it.id }
        assertEquals("Change trainer levels", opts.getValue("trainersLevelModified").label)
        assertEquals("Trainer level change (%)", opts.getValue("trainersLevelModifier").label)
        assertEquals("Change wild Pok\u00e9mon levels", opts.getValue("wildLevelsModified").label)
        assertEquals("Random shiny trainer Pok\u00e9mon", opts.getValue("shinyChance").label)
        assertEquals("Keep field move TMs", opts.getValue("keepFieldMoveTMs").label)
        // No two options may read the same, and none may read like code.
        val labels = opts.values.map { it.label }
        assertEquals(labels.size, labels.toSet().size, "duplicate labels: " + labels.groupBy { it }.filter { it.value.size > 1 }.keys)
        // Both engines' fields: every one has a written label, none relies on prettify.
        (opts.values + SettingsReflector.options(com.dabomstew.pkrandomzx.Settings::class.java)).forEach { o ->
            assertTrue(o.id in SettingsReflector.LABELS, "${o.id} has no desktop label")
        }
        opts.values.forEach { o ->
            assertTrue(!o.label.endsWith(" mod"), "${o.id} still reads ${o.label}")
            assertTrue("pokemon" !in o.label.lowercase(), "${o.id} spells Pokemon without the accent: ${o.label}")
            assertTrue(!Regex("[a-z][0-9]").containsMatchIn(o.label), "${o.id}: ${o.label}")
        }
    }

    @Test
    fun `enum values read as the desktop's buttons`() {
        assertEquals("Random, even distribution, main game", SettingsReflector.valueLabel("trainersMod", "MAINPLAYTHROUGH"))
        assertEquals("Catch 'em all", SettingsReflector.valueLabel("wildPokemonRestrictionMod", "CATCH_EM_ALL"))
        assertEquals("off", SettingsReflector.valueLabel("trainersLevelModified", "false"))
        // Every value of every choice the editor shows has a written label, so
        // none falls back to the raw constant ("Mainplaythrough").
        SettingsReflector.options(Settings::class.java).filterIsInstance<Option.Choice>().forEach { c ->
            c.values.forEach { v ->
                assertTrue(SettingsReflector.VALUE_LABELS[c.id]?.containsKey(v) == true, "${c.id}.$v has no label")
            }
        }
    }

    @Test
    fun `settings with no editable data are hidden`() {
        val opts = SettingsReflector.options(Settings::class.java)
        val ids = opts.map { it.id }.toSet()
        assertTrue("limitPokemon" !in ids)
        assertTrue("standardizeEXPCurves" !in ids)
        val starters = opts.filterIsInstance<Option.Choice>().first { it.id == "startersMod" }
        assertTrue("CUSTOM" !in starters.values, "Custom starters has no picker here")
        assertEquals("Other", Section.MISC.title)
    }

    @Test
    fun `sections follow the desktop tab, not substrings`() {
        assertEquals(Section.TRAITS, SettingsReflector.sectionOf("removeTimeBasedEvolutions"))   // "move" in "remove"
        assertEquals(Section.ITEMS, SettingsReflector.sectionOf("banRegularShopItems"))
        assertEquals(Section.MISC, SettingsReflector.sectionOf("banIrregularAltFormes"))
        assertEquals(Section.TMHM, SettingsReflector.sectionOf("blockBrokenTMMoves"))
        assertEquals(Section.TMHM, SettingsReflector.sectionOf("tmLevelUpMoveSanity"))
        assertEquals(Section.TRAINERS, SettingsReflector.sectionOf("rivalCarriesStarterThroughout"))
        assertEquals(Section.STARTERS, SettingsReflector.sectionOf("limitMainGameLegendaries"))
        assertEquals(Section.TRAITS, SettingsReflector.sectionOf("allowWonderGuard"))
        assertEquals(Section.WILD, SettingsReflector.sectionOf("balanceShakingGrass"))
        assertEquals(Section.TRAINERS, SettingsReflector.sectionOf("doubleBattleMode"))
        assertEquals(Section.TRAINERS, SettingsReflector.sectionOf("shinyChance"))
        assertEquals(Section.TRAINERS, SettingsReflector.sectionOf("eliteFourUniquePokemonNumber"))
        assertEquals(Section.STARTERS, SettingsReflector.sectionOf("limit600"))
        assertEquals(Section.WILD, SettingsReflector.sectionOf("wildPokemonBSTLimit"))
    }

    @Test
    fun `desktop ranges for the BST cap and the generation combos`() {
        assertEquals(307..780, SettingsReflector.rangeOf("wildPokemonBSTLimit"))
        assertEquals(0..780, SettingsReflector.stepperRange("wildPokemonBSTLimit"))
        // One step down from the lowest cap is "off"; one up from off is the lowest cap.
        assertEquals(0, SettingsReflector.snapValue("wildPokemonBSTLimit", 307, 306))
        assertEquals(307, SettingsReflector.snapValue("wildPokemonBSTLimit", 0, 1))
        assertEquals(307, SettingsReflector.snapValue("wildPokemonBSTLimit", 600, 250))
        assertEquals(0, SettingsReflector.snapValue("wildPokemonBSTLimit", 600, 40))
        assertEquals(500, SettingsReflector.snapValue("wildPokemonBSTLimit", 600, 500))
        // A Gen 3 game: moves 4..9, base stats 6..9; a Gen 5 game: moves 6..9.
        assertEquals(4..9, SettingsReflector.rangeOf("updateMovesToGeneration", "GBA3"))
        assertEquals(6..9, SettingsReflector.rangeOf("updateBaseStatsToGeneration", "GEN3"))
        assertEquals(6..9, SettingsReflector.rangeOf("updateMovesToGeneration", "NDS5"))
    }

    @Test
    fun `the Nat Dex legendary exclusion is a named choice, not a number`() {
        val c = SettingsReflector.options(Settings::class.java).first { it.id == "wildBSTLimit" }
        assertTrue(c is Option.Choice, "wildBSTLimit is ${c::class.simpleName}")
        assertEquals(listOf("0", "1", "2"), c.values)
        assertEquals("Legendaries and Mythicals", SettingsReflector.valueLabel("wildBSTLimit", "1"))
    }
}
