package com.ironmonone.app

import com.ironmonone.tracker.nds.Gen4
import com.ironmonone.tracker.nds.NdsRunOver
import com.ironmonone.tracker.nds.NdsTrackedMon
import com.ironmonone.tracker.nds.NdsTrackerState
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Program.onRunEnded on the DS: the run it logs, and the latch the tracker
 * reads as tracker.hasRunEnded().
 */
class NdsRunEndTest {
    private fun tm(species: Int, name: String, level: Int, hp: Int) = NdsTrackedMon(
        mon = Gen4.decodeParty(Gen4.encodeParty(species.toLong() * 7 + 1, species, level, hp, 60, listOf(33, 0, 0, 0)))!!,
        speciesName = name, info = null, abilityName = "-", itemName = "-", moves = emptyList(),
    )

    @Test
    fun `a win is logged with the last battle's Pokemon, as playerPokemon and enemyPokemon hold them`() {
        // The read the champion fight ended on: out of battle, the last battle's two kept.
        val state = NdsTrackerState(
            partyCount = 2, party = listOf(tm(398, "Staraptor", 58, 60), tm(392, "Infernape", 60, 60)), located = true,
            runOver = NdsRunOver.WON, progress = 2,
            lastBattlePlayer = tm(392, "Infernape", 60, 12), lastBattleEnemy = tm(445, "Garchomp", 66, 0),
        )
        val run = PastRun.fromDs(state, won = true, seconds = 100)!!
        assertEquals(PastRun.WON, run.progress)
        assertEquals("Infernape", run.fainted.name)
        assertEquals("Garchomp", run.enemy.name, "SeedLogger logs the champion's last Pokemon")
    }

    @Test
    fun `the DS tracker reads the game-over latch as hasRunEnded`() {
        // Wiring proof: fails if the poller stops telling the tracker the run has ended.
        val src = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("t.runEnded = !gameOverLatch.armed" in src, "the DS tracker no longer knows the run has ended")
    }
}
