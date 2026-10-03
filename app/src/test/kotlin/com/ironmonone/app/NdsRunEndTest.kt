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

    @Test
    fun `a loss is past the lab only when the run got past it`() {
        // rc33 audit P1 #25: "has a party" stood in for past the lab, and a run with no party is not logged at all, so
        // every loss was filed past the lab and the Past Lab count equalled Total runs.
        val atTheLab = NdsTrackerState(partyCount = 1, party = listOf(tm(387, "Turtwig", 5, 0)), located = true, progress = 0, badges = 0)
        kotlin.test.assertEquals(PastRun.NOWHERE, PastRun.fromDs(atTheLab, won = false, seconds = 60)!!.progress)
        kotlin.test.assertEquals(PastRun.PAST_LAB, PastRun.fromDs(atTheLab, won = false, seconds = 60, progressSoFar = 1)!!.progress, "the run's own progress")
        kotlin.test.assertEquals(PastRun.PAST_LAB, PastRun.fromDs(atTheLab.copy(badges = 1), won = false, seconds = 60)!!.progress, "a badge is past it")
        // The run keeps the furthest it got, across visits to Play.
        val dir = java.nio.file.Files.createTempDirectory("dsprogress").toFile()
        val marks = StatMarks(File(dir, "marks.txt"))
        kotlin.test.assertEquals(0, marks.dsProgress())
        marks.noteDsProgress(1); marks.noteDsProgress(0)
        kotlin.test.assertEquals(1, StatMarks(File(dir, "marks.txt")).dsProgress(), "kept, and never lowered")
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("ndsState?.let { statMarks.noteDsProgress(it.progress) }" in play && "pastRunStore) { statMarks.dsProgress() }" in play)
        val latch = File("src/main/kotlin/com/ironmonone/app/GameOverLatch.kt").readText()
        assertTrue("PastRun.fromDs(nds, won, seconds, progress(), attempt, store.lastSeedText())" in latch)
        assertTrue("?.let { pastRuns.logEnd(it, store.runEvents(session)?.entries()) }" in latch)
    }

    /**
     * rc32 audit P2 #41: the DS latch was made fresh, armed, for every visit to Play, so after Continue playing and a
     * new visit the next faint logged the run again and the champion logged a win for a run already lost. P2 #48: the
     * past run's seconds were the time since Play opened, not the run's.
     */
    @Test
    fun `a DS run whose end is on record starts with its latch fired, until a Retry reopens it`() {
        val dir = java.nio.file.Files.createTempDirectory("dslatch").toFile()
        val store = PrepStore(dir)
        val kind = com.ironmonone.core.RomKind.PLATINUM_U
        File(dir, "prep/lastrun.txt").writeText("${kind.id}\nKaizo.rnqs\n")
        File(dir, "prep/lastseed.txt").writeText("00000000000000aa")
        val run = GameSession.forRun(store.currentRunFor(kind), kind)
        assertTrue(GameOverLatch.forPlay(com.ironmonone.core.Platform.NDS, store, run).armed, "nothing on record: armed")
        val lost = runRecordAtEnd(attempt = store.attempt(kind.id), seed = "00000000000000aa", ruleset = "Kaizo.rnqs", started = 1, playSeconds = 600,
            won = false, gba = null, nds = null, trainerName = null)
        RunHistory(store.runHistoryFile(kind)).record(lost)
        val latch = GameOverLatch.forPlay(com.ironmonone.core.Platform.NDS, store, run)
        kotlin.test.assertFalse(latch.armed, "the run ended on an earlier visit")
        kotlin.test.assertFalse(latch.onRead(com.ironmonone.tracker.RunOutcome.WON, NdsRunOver.WON), "the champion opens nothing and logs nothing")
        assertTrue(GameOverLatch.forPlay(com.ironmonone.core.Platform.GBA, store, run).armed, "Gen 3 re-arms per battle, as before")
        store.runEvents(run)!!.add(RunEvents.Kind.RETRY, "battle start", at = lost.ended + 1)
        assertTrue(GameOverLatch.forPlay(com.ironmonone.core.Platform.NDS, store, run).armed, "Retry reopened it")
        // A library game has no run to remember.
        val lib = GameSession(File(dir, "plat.nds"), com.ironmonone.core.Platform.NDS, kind, "Platinum", "lib-1", isRun = false)
        assertTrue(GameOverLatch.forPlay(com.ironmonone.core.Platform.NDS, store, lib).armed)
    }
}
