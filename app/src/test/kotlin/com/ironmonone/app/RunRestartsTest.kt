package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** File > Restart on a run goes on its record (2026-09-30, IronMON rules check R11); in a library game there is none. */
class RunRestartsTest {
    @Test
    fun `a restart in a run is logged, a restart in a library game is not`() {
        val filesDir = Files.createTempDirectory("restart").toFile()
        val store = PrepStore(filesDir)
        store.library.selectRun()
        RunRestarts.log(filesDir, at = 5L)
        val log = RunEvents(File(filesDir, "prep/integrity.txt")).entries()
        assertEquals(1, log.size, "the run's log")
        assertEquals(RunEvents.Kind.RESET, log[0].kind)
        assertEquals(5L, log[0].at)
        assertEquals(0, rewinds(log), "said on the card as a restart, not counted as a state load")
    }

    @Test
    fun `a restart counts no attempt, and the run page's number is the run in play's`() {
        val filesDir = Files.createTempDirectory("restart").toFile()
        val store = PrepStore(filesDir)
        repeat(3) { store.bumpAttempt("black2-u", "B2W2 Kaizo.rnqs") }
        repeat(4) { store.bumpAttempt("platinum-u", "DPPt Kaizo.rnqs") }
        val before = listOf(store.gameAttempts("black2-u"), store.gameAttempts("platinum-u"), store.attemptOf("black2-u", "B2W2 Kaizo.rnqs"))
        RunRestarts.log(filesDir, at = 5L)              // a restart, the run's or a Library game's
        assertEquals(before, listOf(store.gameAttempts("black2-u"), store.gameAttempts("platinum-u"), store.attemptOf("black2-u", "B2W2 Kaizo.rnqs")))
        // QA rc34.1: Black 2's run in play is attempt 3 while the page has another game picked, one with 4 so far.
        assertEquals(3, RunHeader.attempt(inPlay = 3, picked = store.attemptOf("platinum-u", "DPPt Kaizo.rnqs")))
        assertEquals(4, RunHeader.attempt(inPlay = null, picked = 4), "no run in play: the picked game's count")
        val run = File("src/main/kotlin/com/ironmonone/app/RunScreen.kt").readText()
        assertTrue("Attempt \${RunHeader.attempt(inPlay?.attempt, store.attemptOf(rom.id, selectedSettings?.name))}" in run, "the header asks RunHeader")
    }

    @Test
    fun `the restart button writes it, from outside the Play screen`() {
        val side = File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText()
        assertTrue("onRestart(); RunRestarts.log(restartFiles)" in side)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("onRestart = { RetroAchievements.restartGame(retro) }" in play, "the game and the achievement client restart together (rc33 audit P1 #38)")
    }
}
