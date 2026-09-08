package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** The reference seeds the calculator from the lead's damaging moves, minus fixed-damage moves and Hidden Power. */
class CoverageCalcTest {
    @Test
    fun `status, fixed-damage and Hidden Power moves are not coverage`() {
        val moves = listOf(
            Triple(33, "PHY", "NORMAL"),     // Tackle
            Triple(69, "PHY", "FIGHTING"),   // Seismic Toss: fixed damage, out
            Triple(237, "SPE", "NORMAL"),    // Hidden Power: out
            Triple(45, "STA", "NORMAL"),     // Growl: status, out
            Triple(52, "SPE", "FIRE"),       // Ember
            Triple(53, "SPE", "FIRE"),       // Flamethrower: same type, once
        )
        assertEquals(listOf("NORMAL", "FIRE"), CoverageCalc.seedTypes(moves))
        assertEquals(6, CoverageCalc.seedTypes((1..8).map { Triple(it + 300, "PHY", "T$it") }).size)
    }
}
