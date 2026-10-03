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
    /** The clock that only goes forward (elapsedRealtime on the phone): this test moves it by hand. */
    private var clock = 100_000L
    private val auto = AutoSave(state, stamp) { clock }

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
        // Waited for, not slept on (rc32 audit P3 #83): a write queued on the writer thread would land by the end of this.
        AutoSave.drain()
        assertFalse(state.exists(), "a state stamped for no run would load on whatever run comes next")
        assertTrue(PrepStore.stampKnown("lib-1f1c08fb") && PrepStore.stampKnown("emerald-u/00000000000000aa"))
    }

    @Test
    fun `every three minutes of play, and at a battle unless one was just saved`() {
        auto.started()
        clock += AutoSave.EVERY_MS - 1
        assertFalse(auto.due(), "the core coming up counts as the last write")
        clock += 1
        assertTrue(auto.due())
        clock = 200_000
        auto.save(byteArrayOf(1), 1_700_000_000_000, "emerald-u/00000000000000aa")
        clock += AutoSave.BATTLE_GAP_MS - 1
        assertFalse(auto.battleDue(0))
        clock += 1
        assertTrue(auto.battleDue(0))
        clock = 200_000 + AutoSave.EVERY_MS - 1
        assertFalse(auto.due())
        AutoSave.drain()
    }

    /**
     * rc32 audit P2 #10: the phone's clock set back (the game's own clock follows it, so players move it). Ordered on the
     * wall clock, the snapshot taken as the game was left counted as older than the one on disk and was dropped with
     * its mark, and no periodic or battle write came for as long as the clock had moved.
     */
    @Test
    fun `setting the phone's clock back changes nothing, the newest snapshot wins and the writes keep coming`() {
        val stampText = "emerald-u/00000000000000aa"
        val wall = 1_800_000_000_000L
        val mark = File(dir, "autosave.left")
        // Left at a wall time an hour ahead: written, with its mark.
        auto.save(byteArrayOf(1), wall + 3_600_000, stampText, leaving = true)
        AutoSave.drain()
        assertTrue(mark.isFile)
        // Back an hour later by the phone's clock set back, and played on: the battle is due after 30 s of play.
        clock += AutoSave.BATTLE_GAP_MS
        assertTrue(auto.battleDue(wall), "no battle write for as long as the clock had moved")
        auto.save(byteArrayOf(2), wall, stampText)
        AutoSave.drain()
        assertContentEquals(byteArrayOf(2), state.readBytes(), "the newer snapshot is on disk")
        assertFalse(mark.exists(), "and the older moment's mark is gone")
        clock += AutoSave.EVERY_MS
        assertTrue(auto.due(), "the three-minute write comes three minutes later")
        // Every caller's time: Play's two and keepFresh's, never the wall clock for ordering.
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("it.battleDue(now) }?.save(s, now, store.stateStamp(session))" in play, "the Play screen's calls, unchanged")
        val src = File("src/main/kotlin/com/ironmonone/app/AutoSave.kt").readText()
        assertFalse("System.currentTimeMillis()" in src.substringAfter("suspend fun keepFresh"), "keepFresh paces on the clock")
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

    /**
     * rc32 audit P2 #51: a Game Boy or GBA in-game save reached the disk only on a pause, leaving Play and NEW RUN, so
     * a crash or a dead battery lost every in-game save since, and the crash resume played on with the older one.
     */
    @Test
    fun `the battery save is written while the game runs, once it has held still since the game saved`() {
        val disk = byteArrayOf(1, 1, 1)
        val w = SramWatch(disk)
        assertFalse(w.offer(disk), "unchanged from the disk")
        assertFalse(w.offer(disk))
        val saved = byteArrayOf(1, 2, 1)
        assertFalse(w.offer(saved), "changed this tick: the game may be part way through its save")
        assertTrue(w.offer(saved), "held still a tick: written")
        w.wrote(saved)
        assertFalse(w.offer(saved), "written once")
        assertFalse(w.offer(ByteArray(0)), "a core with nothing to give is never written")
        val failed = SramWatch(null)
        failed.offer(saved)
        assertTrue(failed.offer(saved))
        assertTrue(failed.offer(saved), "a write that failed is tried again next tick")
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("}, sram = if (platform.coreOwnsSaves) null else sramFile())" in play, "Play hands the battery save to the flush, DS excepted")
        val src = java.io.File("src/main/kotlin/com/ironmonone/app/AutoSave.kt").readText().replace("\r\n", "\n")
        val loop = src.substringAfter("suspend fun keepFresh(").substringBefore("\n    }\n")
        assertTrue(loop.indexOf("withContext(kotlinx.coroutines.Dispatchers.IO) { flushSram(view, sram, watch) }") in 0 until loop.indexOf("if (!due()) continue"),
            "every tick of play, not every three minutes, and off the main thread")
        assertTrue("val failed = StateSlots.writeSram(f, bytes, at)" in src)
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
