package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Program.Pedometer and the PedometerReset button. */
class PedometerTest {
    @Test
    fun `reset counts from now, and Total goes back to the game's count`() {
        Pedometer.lastResetCount = 0
        assertEquals("Reset", Pedometer.buttonLabel(500))
        Pedometer.press(500)
        assertEquals(0, Pedometer.current(500))
        assertEquals("Total", Pedometer.buttonLabel(500))
        assertEquals(20, Pedometer.current(520))
        Pedometer.press(500)
        assertEquals(500, Pedometer.current(500))
        Pedometer.lastResetCount = 0
    }

    /** rc32 audit P3 #46: a Reset and a goal lived for the whole process, so the next run read "Steps: 0" for thousands of steps. */
    @Test
    fun `the reset and the goal start over with another run or game, and stay for the same one`() {
        Pedometer.follow("run a")
        Pedometer.press(5000)
        Pedometer.goalSteps = 900
        Pedometer.follow("run a")
        assertEquals(0, Pedometer.current(5000), "the same run keeps the player's own Reset")
        assertEquals(900, Pedometer.goalSteps)
        Pedometer.follow("run b")
        assertEquals(120, Pedometer.current(120), "a new run counts the new game's steps")
        assertEquals(0, Pedometer.goalSteps, "and has no goal")
        assertEquals("Reset", Pedometer.buttonLabel(120))
        // What Play hands it: the run's notes, attempt and seed, in the side screens' host.
        val side = java.io.File("src/main/kotlin/com/ironmonone/app/SideScreens.kt").readText()
        assertTrue("SideEffect { Pedometer.follow(Triple(statMarks, attempt, currentSeed)) }" in side)
    }
}
