package com.ironmonone.app

import com.ironmonone.tracker.RunOutcome
import com.ironmonone.tracker.nds.NdsRunOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The game-over popup's latch, against each reference's own rule. The first
 * test is Blake's Black 2 report: the old popup was drawn from the live
 * outcome, so the heal after a whiteout closed it on its own.
 */
class GameOverLatchTest {
    @Test
    fun `a whiteout heal no longer takes the popup away`() {
        val l = GameOverLatch(GameOverFamily.DS)
        assertTrue(l.onRead(RunOutcome.LOST, NdsRunOver.SHEDINJA))
        assertTrue(l.open)
        // The next reads: whited out, the Pokemon Center healed the party.
        l.onRead(null, null)
        l.onRead(null, null)
        assertTrue(l.open, "only the X, Continue or New game closes it")
        assertEquals(RunOutcome.LOST, l.outcome)
        assertEquals(NdsRunOver.SHEDINJA, l.dsCause, "the message keeps the cause the run ended on")
    }

    /**
     * rc32 audit P3 #27: the popup drew its team from the live read, so the Pokemon Center's heal after a whiteout turned
     * the fallen team healthy under the open popup. The latch keeps the team of the read that fired it.
     */
    @Test
    fun `the team on the popup is the one the run ended with, not the healed one`() {
        val l = GameOverLatch(GameOverFamily.GEN3)
        val fallen = listOf(GameOverMon(277, "TREECKO", 12, fainted = true), GameOverMon(288, "ZIGZAGOON", 9, fainted = true))
        l.teamNow = { fallen }
        assertTrue(l.onRead(RunOutcome.LOST, null))
        assertEquals(fallen, l.team)
        // Whited out: the next reads are the healed party.
        l.teamNow = { fallen.map { it.copy(fainted = false) } }
        l.onRead(null, null)
        l.onRead(RunOutcome.LOST, null)
        assertTrue(l.team.all { it.fainted }, "still the team as the run ended")
        // Retry undoes the loss and a new run starts over: neither keeps the old team.
        l.retried()
        assertTrue(l.team.isEmpty())
        l.teamNow = { fallen }
        l.onRead(null, null)
        assertTrue(l.onRead(RunOutcome.LOST, null))
        assertEquals(fallen, l.team)
        l.reset()
        assertTrue(l.team.isEmpty())
        // GameOverHost draws the latch's team and keeps the read's team current only while the latch waits.
        val host = java.io.File("src/main/kotlin/com/ironmonone/app/GameOverHost.kt").readText().replace("\r\n", "\n")
        assertTrue("val team: List<GameOverMon> = latch.team" in host)
        assertTrue("SideEffect { if (latch.armed) latch.teamNow = { gameOverTeam(ndsState, trackerState) } }" in host)
        assertTrue(host.indexOf("latch.teamNow =") < host.indexOf("if (!latch.open || hidden) return"), "kept before the early return, every time")
    }

    @Test
    fun `the DS run-over fires once per run, as tracker setRunOver does`() {
        val l = GameOverLatch(GameOverFamily.DS)
        assertTrue(l.onRead(RunOutcome.LOST, NdsRunOver.STANDARD))
        l.close()
        assertFalse(l.open)
        l.onBattleBegan()
        assertFalse(l.onRead(RunOutcome.LOST, NdsRunOver.STANDARD), "hasRunEnded holds for the whole run")
        assertFalse(l.open)
        l.reset()
        assertTrue(l.onRead(RunOutcome.LOST, NdsRunOver.STANDARD), "a new seed starts the tracker data over")
    }

    @Test
    fun `Gen 3 re-arms when the next battle begins, as isDisplayed does`() {
        val l = GameOverLatch(GameOverFamily.GEN3)
        assertTrue(l.onRead(RunOutcome.LOST, null))
        l.close()
        assertFalse(l.onRead(RunOutcome.LOST, null), "no second popup for the same battle")
        l.onRead(null, null)
        assertFalse(l.onRead(RunOutcome.LOST, null), "an outcome coming back on its own does not re-open it")
        l.onBattleBegan()
        assertTrue(l.onRead(RunOutcome.LOST, null), "Battle.beginNewBattle cleared isDisplayed")
        assertTrue(l.open)
    }

    @Test
    fun `retry closes the popup and lets the replayed battle end the run again`() {
        for (family in GameOverFamily.values()) {
            val l = GameOverLatch(family)
            assertTrue(l.onRead(RunOutcome.LOST, null))
            l.retried()
            assertFalse(l.open)
            assertEquals(null, l.outcome, "$family: the loss is undone, so nothing reads the run as over (the stream's view)")
            // The restore lands a frame or two after the tap: those reads still
            // show the lost battle and must not bring the popup back.
            assertFalse(l.onRead(RunOutcome.LOST, null), "$family: a stale read after Retry reopened the popup")
            assertFalse(l.onRead(RunOutcome.LOST, null), "$family: a stale read after Retry reopened the popup")
            assertFalse(l.open)
            // Then the restored battle reads as live, and losing it again ends the run again.
            assertFalse(l.onRead(null, null))
            assertTrue(l.onRead(RunOutcome.LOST, null), "$family: the replayed battle can end the run")
            assertTrue(l.open)
        }
    }

    @Test
    fun `it fires exactly once per ending, so the run is logged once`() {
        val l = GameOverLatch(GameOverFamily.DS)
        val fired = listOf(RunOutcome.LOST, RunOutcome.LOST, null, RunOutcome.LOST, RunOutcome.WON).count { l.onRead(it, null) }
        assertEquals(1, fired)
    }

    /**
     * The Game Boy references have no "Game is considered over when" option and
     * use the lead; the player still chooses (Blake, 2026-09-29: full control of
     * the mode), so the gear offers the condition on every game and the poller
     * hands the choice to both Game Boy trackers.
     */
    @Test
    fun `the gear offers the loss condition on every game, Game Boy included`() {
        fun src(name: String) = java.io.File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        val lines = src("TrackerGearDialog.kt").lines()
        val i = lines.indexOfFirst { "LossCondition.entries.forEach" in it }
        fun indent(s: String) = s.takeWhile { it == ' ' }.length
        val opener = lines.subList(0, i).last { it.isNotBlank() && indent(it) < indent(lines[i]) }
        assertFalse("gameBoy" in opener, "the choice is gated: $opener")
        assertTrue(Regex("gbc\\?\\.lossCondition = TrackerOptions\\.lossCondition").containsMatchIn(src("PlayScreen.kt")))
        assertTrue(Regex("gb1\\?\\.lossCondition = TrackerOptions\\.lossCondition").containsMatchIn(src("PlayScreen.kt")))
    }

    /**
     * rc32 audit P3 #72: the PC tracker's Continue playing goes back to the tracker screen (GameOverScreen.lua:75-90).
     * The panels drew their GAME OVER card from the live read, which says LOST until the battle ends (Gen 3) or the party
     * is healed (Game Boy), so after Continue the card stood where the battle and the party belong.
     */
    @Test
    fun `Continue playing gives the panel its battle back, until the next ending`() {
        for (family in GameOverFamily.values()) {
            val l = GameOverLatch(family)
            assertTrue(GameOverCard.shows(true), "$family: a new latch, a clean card")
            assertTrue(l.onRead(RunOutcome.LOST, null))
            assertTrue(GameOverCard.shows(true), "$family: the card stands while the popup is up, or hidden in the clean view")
            l.close()
            assertFalse(GameOverCard.shows(true), "$family: dismissed, though the live read still says LOST")
            l.retried()
            assertTrue(GameOverCard.shows(true), "$family: Retry undoes it")
            l.close(); l.reset()
            assertTrue(GameOverCard.shows(true), "$family: a new run")
            assertFalse(GameOverCard.shows(false), "$family: and never without an ending")
        }
        // Gen 3 fires again when the next battle ends the run, and the card comes back with it.
        val g = GameOverLatch(GameOverFamily.GEN3)
        g.onRead(RunOutcome.LOST, null); g.close()
        assertFalse(GameOverCard.shows(true))
        g.onBattleBegan()
        assertTrue(g.onRead(RunOutcome.LOST, null))
        assertTrue(GameOverCard.shows(true))
        // Another game's latch is another game's: its dismissal does not carry over.
        g.close()
        GameOverLatch(GameOverFamily.DS)
        assertTrue(GameOverCard.shows(true))
        // Both panels ask through ironmonGameOverCard, which asks the card.
        fun src(name: String) = java.io.File("src/main/kotlin/com/ironmonone/app/$name").readText().replace("\r\n", "\n")
        assertTrue("return GameOverCard.shows(kaizo)" in src("PlayRules.kt"))
    }

    /** rc32 audit P3 #108: the run's "Game is considered over when" reaches the Gen 3 tracker on every poll. */
    @Test
    fun `the Gen 3 tracker is handed the run's loss condition`() {
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText().replace("\r\n", "\n")
        val gba = play.substringAfter("tracker?.let { t ->").substringBefore("val fresh = ")
        assertTrue("t.lossCondition = TrackerOptions.lossCondition" in gba, "before each read")
    }

    @Test
    fun `the families follow the platforms`() {
        assertEquals(GameOverFamily.DS, gameOverFamily(com.ironmonone.core.Platform.NDS))
        assertEquals(GameOverFamily.GEN12, gameOverFamily(com.ironmonone.core.Platform.GBC))
        assertEquals(GameOverFamily.GEN3, gameOverFamily(com.ironmonone.core.Platform.GBA))
    }
}
