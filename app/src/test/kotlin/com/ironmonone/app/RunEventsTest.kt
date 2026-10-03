package com.ironmonone.app

import com.ironmonone.core.RomKind
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The run's events, loads, restores, retries and resumes (RunEvents): kept
 * in order, readable after a crash cut a line short, and gone with the run's
 * other notes on a new run but kept with them in a saved attempt.
 */
class RunEventsTest {
    private val filesDir = Files.createTempDirectory("record").toFile()
    private val store = PrepStore(filesDir)
    private val run = GameSession.forRun(File(filesDir, "prep/runs/current.gba"), RomKind.EMERALD_U)

    @Test
    fun `every event comes back in order with its time, slot and detail`() {
        val r = store.runEvents(run)!!
        r.add(RunEvents.Kind.LOAD, "3", "state from 1000", at = 5_000)
        r.add(RunEvents.Kind.UNDO, "3", at = 6_000)
        r.add(RunEvents.Kind.RESTORE, "# 2 - Route 101", "made 4000", at = 7_000)
        r.add(RunEvents.Kind.RETRY, "battle start", at = 8_000)
        r.add(RunEvents.Kind.RESUME, "auto", "saved 7500, the app crashed", at = 9_000)
        assertEquals(
            listOf(
                RunEvents.Entry(5_000, RunEvents.Kind.LOAD, "3", "state from 1000"),
                RunEvents.Entry(6_000, RunEvents.Kind.UNDO, "3", ""),
                RunEvents.Entry(7_000, RunEvents.Kind.RESTORE, "# 2 - Route 101", "made 4000"),
                RunEvents.Entry(8_000, RunEvents.Kind.RETRY, "battle start", ""),
                RunEvents.Entry(9_000, RunEvents.Kind.RESUME, "auto", "saved 7500, the app crashed"),
            ),
            r.entries(),
        )
        assertEquals(null, store.runEvents(GameSession(File("x.gba"), com.ironmonone.core.Platform.GBA, null, "x", "lib-1", false)),
            "a library game is not a run and keeps no events")
    }

    @Test
    fun `a line cut off by a crash is skipped, and the next event is not run into it`() {
        val r = store.runEvents(run)!!
        r.add(RunEvents.Kind.LOAD, "1", at = 1_000)
        r.file.appendText("2000\tlo")   // the kill landed mid-append
        r.add(RunEvents.Kind.RESUME, "auto", "after a crash", at = 3_000)
        assertEquals(listOf(1_000L, 3_000L), r.entries().map { it.at })
        // A tab or a line break inside a label cannot split a record either.
        r.add(RunEvents.Kind.RESTORE, "a\tb\nc", "d\re", at = 4_000)
        assertEquals(RunEvents.Entry(4_000, RunEvents.Kind.RESTORE, "a b c", "d e"), r.entries().last())
    }

    @Test
    fun `a new run clears the events with the other notes, and a saved attempt keeps them`() {
        val r = store.runEvents(run)!!
        r.add(RunEvents.Kind.LOAD, "2", at = 1_000)
        File(filesDir, "prep/marks.txt").writeText("25:1,0,0,0,0,0")
        val rom = store.currentRunFor(RomKind.EMERALD_U).apply { parentFile.mkdirs(); writeBytes(ByteArray(8)) }
        assertTrue(store.saveAttempt(RomKind.EMERALD_U, 4, "00000000000000aa", null))
        val saved = File(filesDir, "attempts").listFiles()!!.single()
        assertEquals(r.file.readText(), File(saved, r.file.name).readText(), "the attempt carries its events")
        store.clearRunNotes()
        assertFalse(r.file.exists(), "the next run starts with no events")
        assertTrue(r.entries().isEmpty())
        assertTrue(rom.exists())
    }

    @Test
    fun `a saved attempt is refused once a new run is in place, so its files never go in the old folder`() {
        // rc33 audit P1 #41: "Save this attempt" then "New game" copied the NEW run's randomizer log into the old folder.
        store.currentRunFor(RomKind.EMERALD_U).apply { parentFile.mkdirs(); writeBytes(ByteArray(8)) }
        File(filesDir, "prep/lastseed.txt").writeText("00000000000000bb")
        assertFalse(store.saveAttempt(RomKind.EMERALD_U, 4, "00000000000000aa", null), "the seed named is not the run in place")
        assertTrue(File(filesDir, "attempts").listFiles().isNullOrEmpty(), "and nothing was written")
        assertTrue(store.saveAttempt(RomKind.EMERALD_U, 5, "00000000000000bb", null), "the run in place saves")
        val src = File("src/main/kotlin/com/ironmonone/app/PrepStore.kt").readText()
        assertTrue("fun saveAttempt(kind: RomKind, attempt: Int, seed: String, state: ByteArray?): Boolean = synchronized(RUN_FILES)" in src)
        assertTrue("): Unit = synchronized(RUN_FILES) {" in src, "installRun takes the same lock")
        val host = File("src/main/kotlin/com/ironmonone/app/GameOverHost.kt").readText()
        assertTrue("val attempt = store.attempt(); val seed = store.lastSeedText()" in host, "named on the tap, not on the IO thread")
    }
}
