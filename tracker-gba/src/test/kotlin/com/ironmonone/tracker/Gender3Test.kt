package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** MiscData.getMonGender. */
class Gender3Test {
    @Test
    fun `fixed ratios ignore the personality`() {
        assertEquals(Gender3.MALE, Gender3.of(0, 0x12345678))      // Tauros, Hitmons
        assertEquals(Gender3.FEMALE, Gender3.of(254, 0x123456FF))  // Chansey, Jynx
        assertNull(Gender3.of(255, 0x12345678))                     // Magnemite, legendaries
    }

    @Test
    fun `otherwise the personality's low byte against the ratio decides`() {
        // Ratio 31: one in eight female (starters).
        assertEquals(Gender3.FEMALE, Gender3.of(31, 0x2A6B4100 + 30))
        assertEquals(Gender3.MALE, Gender3.of(31, 0x2A6B4100 + 31))
        // Ratio 127: an even split. Only the LOW byte counts.
        assertEquals(Gender3.MALE, Gender3.of(127, 0xFFFFFF80))
        assertEquals(Gender3.FEMALE, Gender3.of(127, 0x0000017E))
    }
}
