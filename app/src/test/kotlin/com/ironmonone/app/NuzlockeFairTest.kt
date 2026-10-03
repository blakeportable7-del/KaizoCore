package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * "Nuzlocke fair" (2026-09-30, UX audit P0-7): a randomized Nuzlocke starts from wild Pokémon and trainers' teams
 * random but close in strength, levels as the game has them and nothing else touched, instead of the official
 * Kaizo IronMON settings with every level raised. It is never an IronMON mode.
 */
class NuzlockeFairTest {
    private val dirs = ArrayList<File>()
    private fun newStore(): PrepStore = PrepStore(Files.createTempDirectory("fair").toFile().also { dirs += it })

    @AfterTest fun cleanUp() { dirs.forEach { it.deleteRecursively() }; dirs.clear() }

    // No fallback to an empty bundle: with none, the "no hidden pass" check below passed whatever the file was (rc32 audit P3 #84).
    private val bundled: Map<String, ByteArray> by lazy {
        File("src/main/assets/presets").listFiles { f -> f.name.endsWith(".rnqs") }!!.associate { it.name to it.readBytes() }
    }

    private fun field(kind: RomKind, f: File, name: String): String {
        val cls = GameBuild.settingsClass(kind)
        return GameBuild.get(cls, GameBuild.read(cls, f), name)
    }

    @Test
    fun `it randomizes wild Pokemon and trainers by similar strength, and changes nothing else`() {
        for (kind in listOf(RomKind.EMERALD_U, RomKind.FIRERED_U_V11, RomKind.FIRERED_NATDEX_121, RomKind.CRYSTAL_U, RomKind.RED_U, RomKind.PLATINUM_U, RomKind.BLACK_U)) {
            val f = NuzlockeFair.file(newStore(), kind).getOrThrow()
            assertEquals("RANDOM", field(kind, f, "wildPokemonMod"), kind.id)
            assertEquals("SIMILAR_STRENGTH", field(kind, f, "wildPokemonRestrictionMod"), kind.id)
            assertEquals("RANDOM", field(kind, f, "trainersMod"), kind.id)
            assertEquals("true", field(kind, f, "trainersUsePokemonOfSimilarStrength"), kind.id)
            assertEquals("false", field(kind, f, "wildLevelsModified"), "${kind.id}: levels as the game has them")
            assertEquals("false", field(kind, f, "trainersLevelModified"), "${kind.id}: levels as the game has them")
            for (untouched in listOf("startersMod", "evolutionsMod", "typesMod", "movesetsMod", "fieldItemsMod"))
                assertEquals("UNCHANGED", field(kind, f, untouched), "${kind.id}: $untouched")
        }
    }

    @Test
    fun `it is made once per game family and kept, under a name that is never an official mode`() {
        val store = newStore()
        val a = NuzlockeFair.file(store, RomKind.EMERALD_U).getOrThrow()
        val b = NuzlockeFair.file(store, RomKind.EMERALD_U).getOrThrow()
        assertEquals(a, b, "asked twice, one file")
        assertEquals("RSE (Nuzlocke fair).rnqs", a.name)
        assertEquals("FRLG NatDex (Nuzlocke fair).rnqs", NuzlockeFair.fileName(RomKind.FIRERED_NATDEX_121))
        assertEquals(1, store.listSettings().count { "Nuzlocke fair" in it.name })
        assertNull(RnqsInfo.of(a).ruleset, "no IronMON mode behind it")
        assertFalse(RulesetCatalog.forRom(RomKind.EMERALD_U, store.listSettings()).any { m -> m.preset == a || a in m.alternatives },
            "the Kaizo IronMON screen never shows it as a mode")
        // No hidden pass: the official level pass is for the official files only. What a run decides is prePassOn, with
        // the player's choices as they stand (none here), and the official Kaizo file shows the check can go red.
        assertTrue(bundled.isNotEmpty())
        val choices = ExtraPasses.Choices(File(store.cacheDirFor(), "choices.txt"))
        assertTrue(ExtraPasses.prePassOn(RomKind.EMERALD_U, "RSE Kaizo.rnqs", bundled.getValue("RSE Kaizo.rnqs"), bundled, choices), "control")
        assertFalse(ExtraPasses.prePassOn(RomKind.EMERALD_U, a.name, a.readBytes(), bundled, choices))
        assertFalse(ExtraPasses.prePassByDefault(RomKind.EMERALD_U, a.name, a.readBytes(), bundled))
    }

    @Test
    fun `the Nuzlocke screen starts on it, and says what each choice does`() {
        val screen = File("src/main/kotlin/com/ironmonone/app/NuzlockeScreen.kt").readText()
        assertTrue("val fairPicked = modeKey == null || modeKey == NuzlockeFair.KEY" in screen, "picked until the player picks a mode")
        assertTrue("values = listOf(NuzlockeFair.KEY) + modes.map { it.key }," in screen, "first in the row")
        assertTrue("if (fairPicked) NuzlockeFair.LINE else NuzlockeFair.IRONMON_LINE" in screen)
        assertFalse("modes.firstOrNull { it.key == \"kaizo\" }" in screen, "Kaizo is no longer what a randomized Nuzlocke opens on")
        for (s in listOf(NuzlockeFair.LABEL, NuzlockeFair.LINE, NuzlockeFair.IRONMON_LINE))
            assertFalse('—' in s || '–' in s, s)
    }
}
