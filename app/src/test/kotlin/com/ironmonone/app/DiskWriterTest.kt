package com.ironmonone.app

import androidx.compose.ui.graphics.Color
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The background writer for the files play keeps changing (DiskWriter): the tracker notes and the time played synced to
 * disk on the main thread as a battle began and every 10 s of play (rc32 audit P2 #90, P3 #60), and the colours, the
 * presets and the image settings did on each tap (P3 #69). Held still here ([DiskWriter.hold]), the writer shows that
 * each of those calls returns before anything reaches the disk.
 */
class DiskWriterTest {
    private val dir: File = Files.createTempDirectory("diskwriter").toFile()

    // Whatever another test left queued is written first, so the counts here are this test's own.
    @BeforeTest fun settle() { DiskWriter.release(); DiskWriter.drain() }

    @AfterTest fun cleanup() {
        DiskWriter.release(); DiskWriter.drain()
        ThemeStore.detach(); ThemeStore.show(ThemeStore.FACTORY); TrackerBackground.detach()
        dir.deleteRecursively()
    }

    @Test
    fun `a burst of saves of one file is one write of the newest text, and what reads it back sees that text at once`() {
        val f = File(dir, "a.txt")
        DiskWriter.hold()
        val before = DiskWriter.written.get()
        for (i in 1..50) DiskWriter.write(f, "v$i")
        assertFalse(f.exists(), "nothing is written while the writer is held")
        assertEquals("v50", DiskWriter.read(f), "a reader sees the newest text before the disk")
        DiskWriter.release()
        assertTrue(DiskWriter.drain())
        assertEquals("v50", f.readText())
        assertEquals(1, DiskWriter.written.get() - before, "fifty saves, one write")
        assertEquals("v50", DiskWriter.read(f))
    }

    @Test
    fun `a write forgotten before it ran never lands, and one that cannot happen leaves the file as it was`() {
        val f = File(dir, "notes.txt").apply { writeText("old run") }
        DiskWriter.hold()
        DiskWriter.write(f, "stale")
        DiskWriter.forget(listOf(f))
        f.delete()
        DiskWriter.release(); DiskWriter.drain()
        assertFalse(f.exists(), "a new run's delete is not undone by a save still queued")
        assertNull(DiskWriter.read(f))
        // A folder where the temp file goes, as a full phone refuses the write: the old file stays whole.
        val g = File(dir, "kept.txt").apply { writeText("whole") }
        File(dir, "kept.txt.tmp").mkdirs()
        DiskWriter.write(g, "new")
        DiskWriter.drain()
        assertEquals("whole", g.readText())
    }

    @Test
    fun `a battle's tracker notes return before they are written, and are written once`() {
        val marks = StatMarks(File(dir, "marks.txt"))
        DiskWriter.hold()
        val before = DiskWriter.written.get()
        // A wild battle begins: the encounter, the route, the moves it shows.
        marks.trackEncounter(261, wild = true)
        marks.trackEncounter(261, wild = true)
        marks.trackEncounter(263, wild = true)
        marks.seeOnRoute(17, 261)
        marks.addMovesSeen(261, listOf(33 to "Tackle"), 3)
        assertEquals(2, marks.encounters(261, wild = true), "counted in memory at once")
        for (n in listOf("encounters.txt", "routes.txt", "moves.txt")) assertFalse(File(dir, n).exists(), "$n was written on the caller's thread")
        // Play opened again at once: the new tracker reads what is still on its way to disk.
        assertEquals(2, StatMarks(File(dir, "marks.txt")).encounters(261, wild = true))
        DiskWriter.release(); DiskWriter.drain()
        assertEquals(3, DiskWriter.written.get() - before, "three encounters, one write of the file; the route; the moves")
        assertTrue(File(dir, "encounters.txt").readText().contains("261:2,0,0"))
    }

    @Test
    fun `clearing the notes drops the saves still queued, so they never come back`() {
        val marks = StatMarks(File(dir, "marks.txt"))
        DiskWriter.hold()
        marks.cycle(25, 0); marks.setNote(25, "fast"); marks.seeOnRoute(17, 261); marks.trackEncounter(25, wild = true)
        marks.clear()
        DiskWriter.release(); DiskWriter.drain()
        val again = StatMarks(File(dir, "marks.txt"))
        assertFalse(again.hasAny(25)); assertEquals("", again.noteFor(25)); assertTrue(again.seenOnRoute(17).isEmpty())
        assertEquals(0, again.encounters(25, wild = true))
    }

    /**
     * RC35-NOTICED N #14: the queued writes were dropped by a hook on the marks map's own clear(), which only worked while
     * clear() emptied that map first and the files were read before the hook was armed. clear() drops them itself now,
     * before anything, and the map is a plain one.
     */
    @Test
    fun `clear drops the queued writes itself, and the marks map is a plain map`() {
        val src = File("src/main/kotlin/com/ironmonone/app/StatMarks.kt").readText().replace("\r\n", "\n")
        val body = src.substringAfter("fun clear(keepRunCounters: Boolean = false) {\n")
        val first = body.lineSequence().map { it.trim() }.first { it.isNotEmpty() && !it.startsWith("//") }
        assertEquals("dropQueuedWrites()", first, "the first thing clear does")
        assertTrue("private val marks = HashMap<Int, IntArray>()" in src)
        assertFalse("inner class Marks" in src, "no hook left on the map")
        // And it still holds after a clear that keeps the run's counters: the newest count is written again.
        val marks = StatMarks(File(dir, "marks.txt"))
        DiskWriter.hold()
        marks.cycle(25, 0); marks.stepDsHiddenPower(forward = true)
        marks.clear(keepRunCounters = true)
        DiskWriter.release(); DiskWriter.drain()
        val read = StatMarks(File(dir, "marks.txt"))
        assertFalse(read.hasAny(25)); assertEquals("DARK", read.dsHiddenPowerType())
    }

    @Test
    fun `play time returns from its ten second save at once and reaches the disk as the newest count`() {
        val f = File(dir, "run-clock.txt")
        RunClock.load(f)
        DiskWriter.hold()
        RunClock.observe("emerald-u#1", 0)
        for (t in 1_000L..12_000L step 1_000) RunClock.observe("emerald-u#1", t)
        assertFalse(f.exists(), "the save at 10 s was made on the caller's thread")
        assertEquals(mapOf("emerald-u#1" to 11L), RunClock.readSeconds(f), "the save 10 s into play, queued, read back as it will be")
        RunClock.save()
        assertEquals(12L, RunClock.readSeconds(f)["emerald-u#1"], "Your stats reads the queued count")
        DiskWriter.release(); DiskWriter.drain()
        assertEquals("emerald-u#1=12\n", f.readText())
    }

    @Test
    fun `a colour, a preset and the image settings return before the sync`() {
        val theme = File(dir, "prep/theme.txt")
        ThemeStore.load(theme)
        DiskWriter.hold()
        ThemeStore.edit(ThemeStore.KEYS[0], Color(0xFF123456))
        ThemeStore.edit(ThemeStore.KEYS[0], Color(0xFF654321))
        assertFalse(theme.exists(), "each valid hex typed synced on the main thread")
        TrackerBackground.load(dir)
        TrackerBackground.changeDim(55); TrackerBackground.save()
        assertFalse(File(dir, TrackerBackground.SETTINGS_FILE).exists())
        DiskWriter.release(); DiskWriter.drain()
        assertTrue(theme.readText().startsWith(ThemeStore.hex(Color(0xFF654321))), "the last colour")
        assertEquals("dim=55\nfit=fill\nsee=35\n", File(dir, TrackerBackground.SETTINGS_FILE).readText())
        // The presets answer whether they were saved, so the screen waits for them off the main thread instead.
        val picker = File("src/main/kotlin/com/ironmonone/app/ThemePicker.kt").readText().replace("\r\n", "\n")
        assertTrue("withContext(Dispatchers.IO) { ThemePresets.saveYours(typed, theme) }" in picker)
        assertTrue("withContext(Dispatchers.IO) { ThemePresets.removeYours(inUse.name) }" in picker)
    }

    @Test
    fun `a backgrounded app waits for the queue`() {
        val main = File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText().replace("\r\n", "\n")
        assertTrue("DiskWriter.drain(1000)" in main.substringAfter("override fun onStop()"))
    }
}
