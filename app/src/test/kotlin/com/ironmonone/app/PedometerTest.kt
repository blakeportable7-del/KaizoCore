package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
