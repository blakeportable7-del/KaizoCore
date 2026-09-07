package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The reference's + and - beside a stat label, from the game's nature number. */
class NatureMarkTest {
    @Test
    fun `Adamant raises ATK and lowers SPA, Hardy marks nothing, no nature marks nothing`() {
        // Adamant = 3: ATK up (3 / 5 = 0 -> ATK), SPA down (3 % 5 = 3 -> SPA)
        assertEquals('+', natureMark(3, "ATK")); assertEquals('-', natureMark(3, "SPA"))
        assertNull(natureMark(3, "DEF")); assertNull(natureMark(3, "HP"))
        // Hardy = 0: ATK up and ATK down, neutral
        assertNull(natureMark(0, "ATK"))
        // Timid = 10: SPE up, ATK down
        assertEquals('+', natureMark(10, "SPE")); assertEquals('-', natureMark(10, "ATK"))
        assertNull(natureMark(null, "ATK")); assertNull(natureMark(25, "ATK"))
    }
}
