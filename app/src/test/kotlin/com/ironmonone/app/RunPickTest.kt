package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc32 audit P2 #78: on the Kaizo IronMON screen any job's end (an export, an import, a failed start) re-read the
 * lists and put the game and mode back to the last run's, and an imported settings file with no game in its name was
 * never the one a game opened on, though the screen lists it.
 */
class RunPickTest {
    private val presets = File("src/main/assets/presets")
    private val frlgKaizo = File(presets, "FRLG Kaizo.rnqs")
    private val rseKaizo = File(presets, "RSE Kaizo.rnqs")
    private val rseSurvival = File(presets, "RSE Survival.rnqs")
    private val fireRed = RomKind.FIRERED_U_V11 to File("firered-u-v11.gba")
    private val emerald = RomKind.EMERALD_U to File("emerald-u.gba")

    @Test
    fun `a refresh keeps the game and mode the player picked`() {
        // The last run was FireRed Kaizo, so the screen opens there; the player picked Emerald and Survival.
        val prepared = listOf(fireRed, emerald)
        val settings = listOf(frlgKaizo, rseKaizo, rseSurvival)
        val game = RunPick.game(prepared, "emerald-u", opening = fireRed)
        assertEquals(emerald, game)
        assertEquals(rseSurvival, RunPick.settings(settings, game?.first, "RSE Survival.rnqs") { frlgKaizo })
        // The lists are new objects after a refresh: the pick is found in them by id and name.
        assertEquals(emerald, RunPick.game(listOf(fireRed, RomKind.EMERALD_U to File("emerald-u.gba")), "emerald-u", fireRed))
    }

    @Test
    fun `a pick that is gone falls back to the opening one`() {
        assertEquals(fireRed, RunPick.game(listOf(fireRed), "emerald-u", opening = fireRed), "the game was deleted")
        assertEquals(fireRed, RunPick.game(listOf(fireRed, emerald), null, opening = fireRed), "nothing picked yet")
        assertEquals(rseKaizo, RunPick.settings(listOf(rseKaizo), RomKind.EMERALD_U, "RSE Survival.rnqs") { rseKaizo }, "the file was deleted")
        assertEquals(rseKaizo, RunPick.settings(listOf(frlgKaizo, rseKaizo), RomKind.EMERALD_U, "FRLG Kaizo.rnqs") { rseKaizo },
            "a file the screen does not list for this game is not kept")
    }

    @Test
    fun `an untagged settings file the player picked is the one the game opens on`() {
        val dir = Files.createTempDirectory("myrun").toFile()
        val mine = File(dir, "My run.rnqs").apply { writeBytes(frlgKaizo.readBytes()) }
        val official = File(dir, "FRLG Kaizo.rnqs").apply { writeBytes(frlgKaizo.readBytes()) }
        val all = listOf(official, mine)
        assertTrue(RulesetCatalog.listedFor(RomKind.FIRERED_U_V11, mine), "the screen lists it for FireRed")
        assertEquals(mine, RulesetCatalog.openingFile(RomKind.FIRERED_U_V11, all, remembered = "My run.rnqs", lastRun = null))
        assertEquals(mine, RulesetCatalog.openingFile(RomKind.FIRERED_U_V11, all, remembered = null, lastRun = "My run.rnqs"))
        // Not for a Nat. Dex game, whose engine it is not.
        assertFalse(RulesetCatalog.listedFor(RomKind.FIRERED_NATDEX_121, mine))
        assertTrue(RulesetCatalog.openingFile(RomKind.FIRERED_NATDEX_121, all, remembered = "My run.rnqs", lastRun = null) != mine)
    }

    @Test
    fun `the screen keeps its pick across a refresh and lists by the one rule`() {
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText().replace("\r\n", "\n")
        assertFalse("remember(refresh) { mutableStateOf(firstRom) }" in run, "the pick started over on every refresh")
        assertTrue("RunPick.game(preparedList, kept.game.takeIf { keepPick }, firstRom)" in run)
        assertTrue("kept.note(selectedRom?.first?.id, selectedSettings?.name, lastRun)" in run)
        assertTrue("settingsList.filter { f -> RulesetCatalog.listedFor(rom, f) }" in run, "the list")
    }
}
