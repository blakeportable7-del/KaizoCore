package com.ironmonone.pc

import kotlin.test.Test
import kotlin.test.assertEquals

class WheelGateTest {
    private fun steps(g: WheelGate, events: List<Pair<Double, Long>>) = events.mapNotNull { (r, t) -> g.feed(r, t) }

    @Test
    fun `a plain notch is one page, down positive and up negative`() {
        val g = WheelGate()
        assertEquals(listOf(1, -1), steps(g, listOf(1.0 to 0L, -1.0 to 500L)))
    }

    @Test
    fun `a smooth wheel's small events make one page a notch, and zero rotation does nothing`() {
        val g = WheelGate()
        // One notch as ten tenths, with zero-rotation events between them: one page, never a page back.
        val notch = (0 until 10).flatMap { listOf(0.1 to it * 5L, 0.0 to it * 5L + 1) }
        assertEquals(listOf(1), steps(g, notch))
        assertEquals(emptyList(), steps(g, List(20) { 0.0 to 1000L + it }))
    }

    @Test
    fun `a fast spin turns at most one page every 150 ms`() {
        val g = WheelGate()
        // 30 notches in 300 ms: pages at 0, 150 and 300 only.
        assertEquals(listOf(1, 1, 1), steps(g, List(31) { 1.0 to it * 10L }))
    }

    @Test
    fun `a change of direction starts the notch over`() {
        val g = WheelGate()
        assertEquals(listOf(-1), steps(g, listOf(0.6 to 0L, -0.5 to 10L, -0.5 to 20L)))
    }
}
