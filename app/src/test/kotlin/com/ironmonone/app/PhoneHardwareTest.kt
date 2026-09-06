package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhoneHardwareTest {
    @Test
    fun `rumble amplitude is the louder motor, clamped, zero stops`() {
        assertEquals(0, PhoneHardware.amplitude(0f, 0f))
        assertEquals(255, PhoneHardware.amplitude(0.2f, 1f))
        assertEquals(127, PhoneHardware.amplitude(0.5f, 0.1f))
        assertEquals(255, PhoneHardware.amplitude(3f, 0f), "over-range clamps")
        assertEquals(0, PhoneHardware.amplitude(-1f, -1f))
    }

    @Test
    fun `accelerometer is scaled to g for the core`() {
        assertTrue(kotlin.math.abs(PhoneHardware.normaliseAccel(PhoneHardware.G) - 1f) < 1e-6f)
        assertEquals(0f, PhoneHardware.normaliseAccel(0f))
    }
}
