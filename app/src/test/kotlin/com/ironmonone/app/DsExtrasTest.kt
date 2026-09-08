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
}
