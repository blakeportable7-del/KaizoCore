package com.ironmonone.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DsExtrasTest {
    @Test
    fun `the timer counts, pauses and stops`() {
        val t = RunTimer(0)
        assertEquals(65L, t.seconds(65_000))
        t.toggle(70_000); assertEquals(70L, t.seconds(90_000))
        t.toggle(100_000); assertEquals(80L, t.seconds(110_000))
        t.stop(120_000); assertEquals(90L, t.seconds(999_000))
        assertEquals("01:01:05", RunTimer.hms(3665))
    }

    @Test
    fun `milestones score as the reference lists them`() {
        val f = File.createTempFile("tourney", ".tsv"); f.delete(); f.deleteOnExit()
        val t = TourneyTracker(f)
        assertEquals(36, TourneyTracker.MILESTONES.size)
        assertEquals(listOf("Beat Rival 1"), t.update("seed1", setOf(496)).map { it.name })
        // Sprout Tower waits until the player has left its map set.
        assertTrue(t.update("seed1", setOf(496, 290), mapId = 155).isEmpty())
        assertEquals(listOf("Beat Sprout Tower"), t.update("seed1", setOf(496, 290), mapId = 3).map { it.name })
        assertEquals(2, t.points(t.scoreFor("seed1")))
        // Chuck, Jasmine and Pryce complete Gyms 5/6/7 as well, in one pass.
        val names = t.update("seed1", setOf(496, 34, 33, 32)).map { it.name }
        assertEquals(listOf("Beat Chuck", "Beat Jasmine", "Beat Pryce", "Beat Gyms 5/6/7"), names)
        assertEquals(6, t.points(t.scoreFor("seed1")))
        assertTrue(t.update("seed1", setOf(496, 34, 33, 32)).isEmpty())
        t.addBonus(t.scoreFor("seed1"), 3)
        assertEquals(11, t.points(t.scoreFor("seed1")))
        t.update("seed2", setOf(20))
        assertEquals(12, TourneyTracker(f).cumulative())
        assertEquals("Seed #1 - Last milestone: Beat Gyms 5/6/7.", TourneyTracker(f).lines()[0])
        assertEquals("Bonuses: Evo Bonus (Lv. 30+)", TourneyTracker(f).lines()[1])
    }

    /**
     * rc32 audit P2 #48: the timer stopped for good at a loss, so Retry left it frozen, and it started at 00:00 on every
     * visit to Play; the past run's seconds were the time since Play opened, not the run's.
     */
    @Test
    fun `the timer holds while the run is over, runs again after a Retry, and starts from the run's own time`() {
        val t = RunTimer(0)
        t.stop(60_000); kotlin.test.assertEquals(60L, t.seconds(90_000))
        t.toggle(91_000); kotlin.test.assertEquals(60L, t.seconds(95_000), "a stopped timer ignores a tap")
        t.resume(100_000); kotlin.test.assertEquals(70L, t.seconds(110_000), "Retry: counting goes on from where it stopped")
        t.toggle(120_000); t.stop(130_000); t.resume(140_000)
        kotlin.test.assertTrue(t.paused, "a pause stays a pause"); kotlin.test.assertEquals(80L, t.seconds(150_000))
        val dir = java.nio.file.Files.createTempDirectory("timer").toFile()
        RunClock.load(File(dir, "run-clock.txt"))
        val kind = com.ironmonone.core.RomKind.HEARTGOLD_U
        RunClock.observe(RunClock.key(kind.id, 7), 0); for (s in 1..600) RunClock.observe(RunClock.key(kind.id, 7), s * 1000L)
        val run = GameSession.forRun(File(dir, "current.nds"), kind)
        try {
            kotlin.test.assertEquals(600L, RunTimer.forRun(run, 7, now = 1_000_000).seconds(1_000_000), "the run's 600 played seconds, not 0")
            kotlin.test.assertEquals(0L, RunTimer.forRun(run.copy(isRun = false, id = "lib-1"), 7, now = 1_000_000).seconds(1_000_000), "a library game starts at 0")
        } finally { RunClock.load(File(dir, "none.txt")) }   // the clock is app-wide: nothing left for other tests
        val latch = File("src/main/kotlin/com/ironmonone/app/GameOverLatch.kt").readText()
        kotlin.test.assertTrue("val seconds = session.kind?.takeIf { session.isRun }?.let { RunClock.of(RunClock.key(it.id, attempt)) } ?: timer.seconds().toInt()" in latch)
        kotlin.test.assertTrue("armed -> timer.resume()" in latch)
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        kotlin.test.assertFalse("runStartedAt" in play, "nothing counts from when Play opened")
    }

    /**
     * rc32 audit P2 #50: a library HeartGold or an HGSS Nuzlocke scored milestones under the last run's seed, and the
     * whiteout out of Sprout Tower after a lost run still awarded the tower.
     */
    @Test
    fun `only the Kaizo IronMON run in play scores, and only until it ends`() {
        val f = File.createTempFile("tourney", ".tsv"); f.delete(); f.deleteOnExit()
        val t = TourneyTracker(f)
        val was = TrackerOptions.tourneyTracker
        TrackerOptions.tourneyTracker = true
        try {
            val state = com.ironmonone.tracker.nds.NdsTrackerState(0, emptyList(), located = true, badgeSet = "HGSS", mapId = 3)
            val library = GameOverLatch(GameOverFamily.DS).apply { applies = false }
            kotlin.test.assertEquals(null, t.onRead(state, setOf(20), library, "lastrunseed"))
            kotlin.test.assertTrue(t.scores.isEmpty(), "no score made for the last run's seed")
            val ended = GameOverLatch(GameOverFamily.DS).apply { onRead(com.ironmonone.tracker.RunOutcome.LOST, null) }
            kotlin.test.assertEquals(null, t.onRead(state, setOf(20), ended, "seed1"))
            kotlin.test.assertTrue(t.scores.isEmpty(), "a run that has ended scores nothing")
            val run = GameOverLatch(GameOverFamily.DS)
            kotlin.test.assertEquals("Milestone: Beat Falkner. New total: 1 points", t.onRead(state, setOf(20), run, "seed1"))
            kotlin.test.assertEquals(null, t.onRead(state.copy(badgeSet = "DPPT"), setOf(20, 21), run, "seed1"), "HeartGold and SoulSilver only")
        } finally { TrackerOptions.tourneyTracker = was }
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        kotlin.test.assertTrue("tourney.onRead(ndsState, ndsTrackerRef?.defeatedTrainers, gameOverLatch, runNow.seed.ifEmpty { session.id })?.let { status = it }" in play)
    }

    @Test
    fun `a tracked species shows only the abilities this run has seen`() {
        assertEquals("---", trackedAbilityLine(emptyList()))
        assertEquals("Levitate", trackedAbilityLine(listOf("Levitate")))
        assertEquals("Levitate / Swift Swim", trackedAbilityLine(listOf("Levitate", "Swift Swim")))
        val src = File("src/main/kotlin/com/ironmonone/app/DsExtras.kt").readText()
        assertTrue("the randomizer's own abilities are not read there", !src.contains("info.ability1") && !src.contains("info.ability2"))
    }
}
