package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * rc33 audit P0-5: a run installed while Play was open. A randomize keeps going when the player leaves its screen,
 * and Play then booted the run about to be replaced; its battery save and auto-save landed on the new run's files
 * under the new run's stamp (Crash resume then loaded the old seed's memory into the new ROM), and on a DS game the
 * install overwrote the ROM the core held open. Play now waits while a run is being made and starts the new one.
 */
class PlayWaitsForNewRunTest {
    @Test
    fun `only the making of a run holds Play, not an import, a patch or an export`() {
        for (p in listOf(RunPhase.ROTATING, RunPhase.RANDOMIZING, RunPhase.FINISHING)) {
            assertTrue(RunJob.isInstalling(true, p), "$p")
            assertFalse(RunJob.isInstalling(false, p), "$p, not busy")
        }
        for (p in listOf(RunPhase.PATCHING, RunPhase.IMPORTING, RunPhase.EXPORTING)) assertFalse(RunJob.isInstalling(true, p), "$p")
    }

    @Test
    fun `the shell draws the wait in place of Play while one is being made`() {
        val main = File("src/main/kotlin/com/ironmonone/app/MainActivity.kt").readText().replace("\r\n", "\n")
        assertTrue("Tab.PLAY -> if (RunJob.installing) PlayRunBeingMade(Modifier.fillMaxSize()) else" in main)
        // The randomize runs in those phases, so the wait covers it from its first moment to its last.
        val job = File("src/main/kotlin/com/ironmonone/app/RunJob.kt").readText().replace("\r\n", "\n")
        assertTrue("busy = true; status = null; phase = RunPhase.RANDOMIZING" in job)
        assertTrue("main { phase = RunPhase.FINISHING }" in job)
    }

    @Test
    fun `the wait says what is happening in plain words`() {
        for (t in listOf(PlayNothingCopy.MAKING, PlayNothingCopy.MAKING_LINE)) {
            assertFalse('\u2014' in t, t)
            assertTrue(t.isNotBlank())
        }
    }
}
