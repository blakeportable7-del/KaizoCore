package com.ironmonone.app

import com.ironmonone.app.engine.Randomizers
import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Each game keeps its own run (VILLAIN, 2026-10-12: "Continue attempt" only offered the last game played, so a FireRed
 * run saved mid-game was lost to a HeartGold run). A new run of another game sets the run in place aside whole, its
 * game, log, record, notes and save states, and Continue on the first game puts it back; the in-game save is one per
 * game and was never touched.
 */
class ParkedRunTest {
    private val filesDir = Files.createTempDirectory("parked").toFile()
    private val store = PrepStore(filesDir)
    private val fireRed = RomKind.FIRERED_U_V11
    private val emerald = RomKind.EMERALD_U
    private val settingsFr = File(filesDir, "prep/settings/FRLG Kaizo.rnqs").apply { parentFile.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }
    private val settingsE = File(filesDir, "prep/settings/RSE Kaizo.rnqs").apply { writeBytes(byteArrayOf(4, 5, 6)) }
    private val prep = File(filesDir, "prep")
    private val saves = File(filesDir, "saves").apply { mkdirs() }

    private fun stub(tag: Int): (File, Long) -> Unit = { dest, seed ->
        dest.writeBytes(ByteArray(64) { tag.toByte() })
        Randomizers.logFor(dest).writeText("log $tag seed $seed")
    }

    private fun start(kind: RomKind, settings: File, tag: Int, seed: Long) {
        val prepared = File(filesDir, "prep/prepared/${kind.id}.${kind.fileExtension}").apply { parentFile.mkdirs(); writeBytes(ByteArray(256) { 7 }) }
        RunStart.start(store, kind, prepared, settings, seed = seed, app = "test", prePass = null, secondPass = null,
            take = { null }, stop = {}, randomize = stub(tag))
    }

    @Test
    fun `a FireRed run set aside by an Emerald run continues where it was`() {
        start(fireRed, settingsFr, 1, 0x11L)
        File(prep, "marks.txt").writeText("FireRed's marks")
        File(saves, "autosave.bin").writeText("FireRed's state"); File(saves, "autosave.id").writeText("stamp")
        File(saves, "${fireRed.id}.srm").writeText("FireRed's in-game save")
        assertEquals(1, store.attempt(fireRed.id))

        start(emerald, settingsE, 2, 0x22L)
        assertEquals(emerald.id, store.loadLastRun()?.first)
        val parked = assertNotNull(store.parkedRun(fireRed.id), "FireRed's run is set aside, not ended")
        assertEquals(1, parked.attempt); assertEquals(settingsFr.name, parked.settingsName)
        assertTrue(RunHistory(store.runHistoryFile(fireRed)).all().isEmpty(), "and not filed as ended")
        assertFalse(File(prep, "marks.txt").exists(), "its notes left with it")
        assertFalse(File(saves, "autosave.bin").exists(), "and its save states")
        assertEquals("FireRed's in-game save", File(saves, "${fireRed.id}.srm").readText(), "the in-game save stays where it is")
        File(prep, "marks.txt").writeText("Emerald's marks")

        // Continue on FireRed: FireRed's run back in place, Emerald's set aside in its turn.
        assertTrue(store.continueRun(fireRed))
        assertEquals(fireRed.id to settingsFr.name, store.loadLastRun())
        assertEquals("%016x".format(0x11L), store.lastSeed())
        assertContentEquals(ByteArray(64) { 1 }, store.currentRunFor(fireRed).readBytes())
        assertEquals("log 1 seed 17", Randomizers.logFor(store.currentRunFor(fireRed)).readText())
        assertEquals("FireRed's marks", File(prep, "marks.txt").readText())
        assertEquals("FireRed's state", File(saves, "autosave.bin").readText())
        assertEquals(1, store.attempt(fireRed.id))
        assertNull(store.parkedRun(fireRed.id))
        assertNotNull(store.parkedRun(emerald.id), "Emerald's run is the one set aside now")

        // And back again: Emerald's notes are its own.
        assertTrue(store.continueRun(emerald))
        assertEquals("Emerald's marks", File(prep, "marks.txt").readText())
        assertContentEquals(ByteArray(64) { 2 }, store.currentRunFor(emerald).readBytes())
    }

    @Test
    fun `a new run of a game replaces that game's run set aside and files it as ended`() {
        start(fireRed, settingsFr, 1, 0x11L)
        start(emerald, settingsE, 2, 0x22L)
        assertNotNull(store.parkedRun(fireRed.id))
        start(fireRed, settingsFr, 3, 0x33L)
        assertNull(store.parkedRun(fireRed.id), "the old FireRed run is gone")
        assertEquals(listOf(1), RunHistory(store.runHistoryFile(fireRed)).all().map { it.attempt }, "filed as ended, as a new run of the same game does")
        assertEquals(2, store.attempt(fireRed.id))
        assertNotNull(store.parkedRun(emerald.id), "Emerald's run is kept")
    }

    @Test
    fun `a run that ended is not set aside`() {
        start(fireRed, settingsFr, 1, 0x11L)
        // The game over files it (as Play's game over does): nothing of it is left to continue.
        RunHistory(store.runHistoryFile(fireRed)).record(RunRecord(attempt = 1, seed = "%016x".format(0x11L), ruleset = settingsFr.name,
            started = 0, ended = 1, playSeconds = 0, outcome = RunRecord.Outcome.ENDED, badges = 0, lead = null, killer = null,
            trainer = "", location = ""))
        assertFalse(store.runIsOpen())
        start(emerald, settingsE, 2, 0x22L)
        assertNull(store.parkedRun(fireRed.id))
        assertFalse(store.continueRun(fireRed))
    }
}
