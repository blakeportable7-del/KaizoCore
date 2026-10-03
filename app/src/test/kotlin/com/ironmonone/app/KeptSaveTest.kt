package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A run that began from the last run's in-game save with its team goes on the run's record (rc33 audit P1 #42, #43).
 * Nothing wrote KEPT_SAVE after the save stopped being set aside, so such a run counted as clean.
 */
class KeptSaveTest {
    private val dir = Files.createTempDirectory("kept").toFile()
    @BeforeTest fun clean() = KeptSave.reset()
    @AfterTest fun cleanup() { KeptSave.reset(); dir.deleteRecursively() }

    @Test
    fun `Continue with the last run's team is seen, New Game is not`() {
        // Attempt 5: the starter, then a catch.
        assertFalse(KeptSave.seen(dir, "firered", 5, listOf(0x1111L)))
        assertFalse(KeptSave.seen(dir, "firered", 5, listOf(0x1111L, 0x2222L)))
        // Attempt 6: the title screen and the intro show no party, and the last run's ids wait.
        assertFalse(KeptSave.seen(dir, "firered", 6, emptyList()))
        // New Game: the first party is a starter the last run never had.
        assertFalse(KeptSave.seen(dir, "firered", 6, listOf(0x3333L)))
        // Attempt 7, Continue on attempt 6's save: its first party is attempt 6's team. Seen once.
        assertTrue(KeptSave.seen(dir, "firered", 7, listOf(0x3333L, 0x4444L)))
        assertFalse(KeptSave.seen(dir, "firered", 7, listOf(0x3333L, 0x4444L)), "once")
    }

    @Test
    fun `the ids outlive the app, and each game keeps its own`() {
        assertFalse(KeptSave.seen(dir, "firered", 5, listOf(0x1111L)))
        assertFalse(KeptSave.seen(dir, "emerald", 2, listOf(0x9999L)))
        KeptSave.reset()
        assertTrue(KeptSave.seen(dir, "firered", 6, listOf(0x1111L)), "read back from the file")
        KeptSave.reset()
        assertFalse(KeptSave.seen(dir, "emerald", 3, listOf(0x1111L)), "FireRed's team is not Emerald's")
        DiskWriter.drain()   // written on the writer's thread (rc32 audit P2 #65)
        assertEquals("attempt 6", KeptSave.file(dir, "firered").readLines().first())
    }

    @Test
    fun `the first run ever, or the first after an update, is never called kept`() {
        assertFalse(KeptSave.seen(dir, "platinum", 1, listOf(0x5555L)))
        assertFalse(KeptSave.seen(dir, "platinum", 1, listOf(0x5555L)))
    }

    @Test
    fun `every tracker's poll feeds it, and the Run tab says what the new seed opens with`() {
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertEquals(1, play.split("KeptSave.observe(store, session, ndsState, runNow.attempt)").size - 1, "the DS poll")
        assertEquals(2, play.split("KeptSave.observe(store, session, trackerState, runNow.attempt)").size - 1, "the Game Boy and GBA polls")
        val src = File("src/main/kotlin/com/ironmonone/app/KeptSave.kt").readText()
        assertTrue("store.runEvents(session)?.add(RunEvents.Kind.KEPT_SAVE" in src)
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText()
        assertTrue("Text(NewRunCopy.save(savePlan)" in run)
        assertTrue("RunSaves.planForNewRun(context.filesDir, k, store.currentRunFor(k))" in run)
    }
}
