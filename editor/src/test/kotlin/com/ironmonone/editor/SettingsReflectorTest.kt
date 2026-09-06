package com.ironmonone.editor

import com.dabomstew.pkrandom.Settings
import java.io.File
import java.io.FileInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

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
        assertEquals("Random every level", SettingsReflector.prettifyEnum("RANDOM_EVERY_LEVEL"))
    }
}
