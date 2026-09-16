package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** Program.ActiveRepel's rules: the duration a count implies, and the bar's colours. */
class RepelTest {
    @Test
    fun `the duration grows to fit the repel and resets when it ends`() {
        // A plain Repel never passes 100, so the duration stays there.
        assertEquals(100, Repel.duration(100, 100))
        assertEquals(100, Repel.duration(37, 100))
        // A Super Repel's first read is 200: the duration grows to it and stays while it counts down.
        assertEquals(200, Repel.duration(200, 100))
        assertEquals(200, Repel.duration(150, 200))
        assertEquals(200, Repel.duration(3, 200))
        // A Max Repel reads 250.
        assertEquals(250, Repel.duration(250, 100))
        assertEquals(250, Repel.duration(201, 100))
        assertEquals(250, Repel.duration(90, 250))
        // The repel ended: back to the default.
        assertEquals(100, Repel.duration(0, 250))
        // Anything past a Max Repel is not a repel; the duration is left alone.
        assertEquals(100, Repel.duration(251, 100))
    }

    @Test
    fun `the bar empties and changes colour at a half and a quarter`() {
        assertEquals(1f, Repel.fraction(100, 100))
        assertEquals(0.5f, Repel.fraction(100, 200))
        assertEquals(0f, Repel.fraction(0, 100))
        assertEquals(Pc.Positive, Repel.barColor(100, 100))
        assertEquals(Pc.Positive, Repel.barColor(51, 100))
        assertEquals(Pc.Gold, Repel.barColor(50, 100))
        assertEquals(Pc.Gold, Repel.barColor(26, 100))
        assertEquals(Pc.Negative, Repel.barColor(25, 100))
        assertEquals(Pc.Negative, Repel.barColor(1, 100))
    }

    @Test
    fun `the DS tracker's icon follows the repel that was used`() {
        assertEquals("Repel", Repel.dsIcon(100))
        assertEquals("SuperRepel", Repel.dsIcon(200))
        assertEquals("MaxRepel", Repel.dsIcon(250))
    }
}
