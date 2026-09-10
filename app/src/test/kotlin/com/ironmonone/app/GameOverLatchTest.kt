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
            assertTrue(l.onRead(RunOutcome.LOST, null), "$family: loadTempSaveState re-arms it")
        }
    }

    @Test
    fun `it fires exactly once per ending, so the run is logged once`() {
        val l = GameOverLatch(GameOverFamily.DS)
        val fired = listOf(RunOutcome.LOST, RunOutcome.LOST, null, RunOutcome.LOST, RunOutcome.WON).count { l.onRead(it, null) }
        assertEquals(1, fired)
    }

    @Test
    fun `the families follow the platforms`() {
        assertEquals(GameOverFamily.DS, gameOverFamily(com.ironmonone.core.Platform.NDS))
        assertEquals(GameOverFamily.GEN12, gameOverFamily(com.ironmonone.core.Platform.GBC))
        assertEquals(GameOverFamily.GEN3, gameOverFamily(com.ironmonone.core.Platform.GBA))
    }
}
