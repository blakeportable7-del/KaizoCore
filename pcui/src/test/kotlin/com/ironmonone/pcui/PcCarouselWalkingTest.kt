package com.ironmonone.pcui

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * "Walking: seen/total" while walking, at any level (Blake, 2026-10-11: "as i am walking the tracker doesn't say walking
 * seen/total, it pops up when i enter a battle"). It used to show out of battle only with the lead at level 12 or under.
 */
class PcCarouselWalkingTest {
    private fun walking(level: Int) = PcCarousel.Facts(
        inBattle = false, viewingOwn = true, isWild = false, leadLevel = level, routeArea = "Walking", routeSeen = 3,
        routeTotal = 8, routeTrainers = 0, trainersDefeated = 0, trainersTotal = 0, lastAttack = null, lethal = false,
        battleDetail = null, totalSteps = 0, pedometerAllowed = false)

    /** The texts the strip shows over a minute of walking, a tick a second. */
    private fun shown(f: PcCarousel.Facts): Set<String> {
        val c = PcCarousel()
        return (0L..60_000L step 1000L).map { c.pick(it + 1, f) }.map { it.text }.toSet()
    }

    @Test
    fun `walking at level 30 shows the route line, taking turns`() {
        val texts = shown(walking(30))
        assertTrue("Walking: 3/8 Seen Pokémon" in texts, "$texts")
    }

    @Test
    fun `walking a map with trainers shows the trainers defeated, not only for five seconds after a battle`() {
        // Blake, 2026-10-11: "tracker isn't showing trainers battled either".
        val texts = shown(walking(30).copy(routeTrainers = 8, trainersDefeated = 2, trainersTotal = 8))
        assertTrue("Trainers defeated: 2/8" in texts, "$texts")
        assertTrue("Walking: 3/8 Seen Pokémon" in texts, "$texts")
    }

    @Test
    fun `a map with no wild Pokemon shows no route line`() {
        val texts = shown(walking(30).copy(routeArea = null, routeTotal = 0))
        assertTrue(texts.none { it.startsWith("Walking") }, "$texts")
    }
}
