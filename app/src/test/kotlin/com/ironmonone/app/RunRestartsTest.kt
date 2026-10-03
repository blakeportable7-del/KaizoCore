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
    fun `the restart button writes it, from outside the Play screen`() {
        val side = File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText()
        assertTrue("onRestart(); RunRestarts.log(restartFiles)" in side)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("onRestart = { RetroAchievements.restartGame(retro) }" in play, "the game and the achievement client restart together (rc33 audit P1 #38)")
    }
}
