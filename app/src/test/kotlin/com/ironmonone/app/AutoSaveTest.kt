package com.ironmonone.app

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
 * The auto slot's writer (AutoSave): newest state wins, the stamp never runs
 * ahead of its state, and no state is written for a run it cannot name.
 */
class AutoSaveTest {
    private val dir = Files.createTempDirectory("autosave").toFile()
    private val state = File(dir, "autosave.bin")
    private val stamp = File(dir, "autosave.id")
    private val auto = AutoSave(state, stamp)

    @Test
    fun `a state taken before the one on disk never replaces it`() {
        assertNull(auto.write(byteArrayOf(2, 2), capturedAt = 2_000, stamp = "emerald-u/00000000000000aa"))
        assertNull(auto.write(byteArrayOf(1), capturedAt = 1_000, stamp = "emerald-u/00000000000000aa"))
        assertContentEquals(byteArrayOf(2, 2), state.readBytes(), "a periodic snapshot queued before a pause's must not win")
        assertNull(auto.write(byteArrayOf(3), capturedAt = 3_000, stamp = "emerald-u/00000000000000aa"))
        assertContentEquals(byteArrayOf(3), state.readBytes())
    }

    @Test
    fun `the stamp is written after its state, so a failed write leaves the old pair`() {
        state.writeBytes(byteArrayOf(9)); stamp.writeText("emerald-u/00000000000000aa")
        // A directory where the .tmp goes makes the state write fail, as a full disk does.
        File(dir, "autosave.bin.tmp").mkdir()
        assertNotNull(auto.write(byteArrayOf(4, 4), 5_000, "firered-u-v11/00000000000000bb"))
        assertContentEquals(byteArrayOf(9), state.readBytes())
        assertEquals("emerald-u/00000000000000aa", stamp.readText(), "the old state must not carry the new run's stamp")
    }

    @Test
    fun `no state is saved for a run whose seed or game is not on disk`() {
        auto.save(byteArrayOf(5), 1_000, "emerald-u/?")
        auto.save(byteArrayOf(5), 1_000, "?/?")
        auto.save(ByteArray(0), 1_000, "emerald-u/00000000000000aa")
        Thread.sleep(300)   // the writer thread, had anything been queued
        assertFalse(state.exists(), "a state stamped for no run would load on whatever run comes next")
        assertTrue(PrepStore.stampKnown("lib-1f1c08fb") && PrepStore.stampKnown("emerald-u/00000000000000aa"))
    }

    @Test
    fun `every three minutes of play, and at a battle unless one was just saved`() {
        auto.started(100_000)
        assertFalse(auto.due(100_000 + AutoSave.EVERY_MS - 1), "the core coming up counts as the last write")
        assertTrue(auto.due(100_000 + AutoSave.EVERY_MS))
        auto.save(byteArrayOf(1), 200_000, "emerald-u/00000000000000aa")
        assertFalse(auto.battleDue(200_000 + AutoSave.BATTLE_GAP_MS - 1))
        assertTrue(auto.battleDue(200_000 + AutoSave.BATTLE_GAP_MS))
        assertFalse(auto.due(200_000 + AutoSave.EVERY_MS - 1))
    }

    // ------------------------------------------------------------------ the left mark (2026-09-30, UX audit P0-4)

    @Test
    fun `a snapshot taken as the game is left marks the slot, and any later one takes the mark away`() {
        val dir = java.nio.file.Files.createTempDirectory("autosave-left").toFile()
        try {
            val state = java.io.File(dir, "autosave.bin"); val stampFile = java.io.File(dir, "autosave.id")
            val mark = java.io.File(dir, "autosave.left")
            val a = AutoSave(state, stampFile)
            val stamp = "emerald-u/00000000000000aa"
            assertNull(a.write(byteArrayOf(1), 100, stamp))
            assertFalse(mark.exists(), "a periodic snapshot is not the moment the game was left")
            assertNull(a.write(byteArrayOf(2), 200, stamp, leaving = true))
            assertEquals("200", mark.readText())
            assertContentEquals(byteArrayOf(2), state.readBytes())
            // Play went on after a pause: the next periodic write is newer than the pause, so the mark goes.
            assertNull(a.write(byteArrayOf(3), 300, stamp))
            assertFalse(mark.exists())
            // A snapshot older than the one on disk is dropped whole, mark included.
            assertNull(a.write(byteArrayOf(9), 250, stamp, leaving = true))
            assertFalse(mark.exists())
            assertContentEquals(byteArrayOf(3), state.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun `the Play screen's own auto-save is the leaving one, and the slot knows where its mark is`() {
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("store.stateStamp(session), leaving = true)" in play, "autoSave() runs on pause and on leaving the tab")
        val dir = java.nio.file.Files.createTempDirectory("autosave-slot").toFile()
        try {
            val run = GameSession.forRun(java.io.File(dir, "prep/runs/current.gba"), com.ironmonone.core.RomKind.EMERALD_U)
            val slot = StateSlots.auto(dir, run)
            assertEquals(java.io.File(slot.file.parentFile, "autosave.left"), slot.leftMark, "the same file AutoSave writes")
        } finally { dir.deleteRecursively() }
    }
}
