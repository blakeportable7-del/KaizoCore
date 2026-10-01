package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The way back to the IronMON run. Play opens the Library game last picked there, and until 2026-09-29 nothing
 * but a new run (which ends the one in play) pointed Play at the run again: found in the rc30 QA pass. The Run
 * tab offered "Back to attempt N" while Play was on a Library game and a run was waiting; checked on the
 * emulator that it opens the run and then goes away.
 *
 * Since 2026-09-30 (UX audit P1, P2) it is "Continue attempt N", offered whenever a run is on disk, and it is the
 * one filled button on the screen, so that starting the next attempt is the quiet one. With Play on the run
 * already there was no button, and the start button was red beside it. What stays conditional is the line saying
 * that Play is on a Library game.
 */
class BackToRunTest {
    private val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText().replace("\r\n", "\n")

    @Test fun `the Run tab points Play back at the run`() {
        assertTrue("Gen3Button(if (run.nuzlocke) RunCopy.CONTINUE_NUZLOCKE else RunCopy.continueRun(run.attempt), accent = true) {\n                store.library.selectRun()\n                onPlay()\n            }" in run)
        assertTrue("inPlay?.let { run ->" in run)
        assertTrue("val inPlay = runInPlay(store, refresh + jobGeneration)" in run)
    }

    @Test fun `whenever a run is there, and the line about a Library game only when Play is on one`() {
        val fn = run.substringAfter("private fun runInPlay(").substringBefore("\n}\n")
        assertTrue("if (!store.currentRunFor(kind).isFile) return@remember null" in fn, "no run: no button")
        assertTrue("store.library.selectedLibraryName()" in fn, "the Library game Play is on, when it is on one")
        assertTrue("run.libraryGame?.let { libraryGame ->" in run, "the line is only for that case")
        assertEquals(1, Regex("""selectRun\(\)""").findAll(run).count(), "one way back")
    }
}
