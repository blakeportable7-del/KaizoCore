package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Play tab with nothing to open (2026-09-30, UX audit P1): each case says what happened and has a button to the
 * next step, and it is drawn outside PlayScreen, which is at ART's verifier limit.
 */
class PlayNothingTest {
    private fun src(name: String) = File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")

    @Test
    fun `Play hands its empty screen to PlayNothing, and the shell gives it somewhere to go`() {
        val play = src("PlayScreen.kt")
        assertTrue("PlayNothing(modifier, session.isRun, session.title, failure)" in play)
        for (old in listOf("No run yet.", "That ROM is gone.", "Randomize one in Kaizo IronMON on Home", "the ROMs tab"))
            assertFalse(old in play, "PlayScreen still says \"$old\"")
        val shell = src("MainActivity.kt")
        assertTrue("LocalShellNav provides ShellNav(openMyGames = { nav = nav.openMyGames() }, openKaizo = { nav = nav.open(HomeMode.KAIZO) })" in shell)
        val nothing = src("PlayNothing.kt")
        assertTrue(nothing.contains("actionLabel = PlayNothingCopy.PICK_A_GAME, onAction = nav?.openMyGames"), "the game list is one tap away")
        assertTrue(nothing.contains("PlayNothingCopy.START_A_RUN, onClick = it.openKaizo"), "and so is a run")
    }

    @Test
    fun `the words are plain and have no dashes`() {
        val all = listOf(PlayNothingCopy.NOTHING, PlayNothingCopy.NOTHING_LINE, PlayNothingCopy.PICK_A_GAME, PlayNothingCopy.START_A_RUN,
            PlayNothingCopy.GONE, PlayNothingCopy.goneLine("Pokémon Emerald (U)"), PlayNothingCopy.FAILED, PlayNothingCopy.BACK_TO_KAIZO)
        for (s in all) assertFalse('—' in s || '–' in s, s)
        assertFalse("ROM" in PlayNothingCopy.goneLine("x") || "ROM" in PlayNothingCopy.GONE, "a player's word is game")
    }
}
