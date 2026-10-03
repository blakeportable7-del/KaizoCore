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

    /**
     * rc32 audit P3 #47: every strength was a 2 s one-shot, and the core sends only changes, so a held rumble stopped
     * after two seconds; leaving Play mid-buzz left it running.
     */
    @Test
    fun `a strength is held until cancelled, and Play stops it on pause and on leaving`() {
        val b = PhoneHardware.buzz(200, hasAmplitudeControl = true)
        assertEquals(listOf(1000L), b.timings.toList()); assertEquals(listOf(200), b.amplitudes.toList())
        assertEquals(0, b.repeat, "repeats from the start until cancelled")
        assertEquals(listOf(-1), PhoneHardware.buzz(200, hasAmplitudeControl = false).amplitudes.toList(), "the motor's own strength")
        val src = java.io.File("src/main/kotlin/com/ironmonone/app/PhoneHardware.kt").readText().replace("\r\n", "\n")
        assertTrue("VibrationEffect.createWaveform(b.timings, b.amplitudes, b.repeat)" in src)
        assertTrue(!src.contains("createOneShot(2000"), "no two-second one-shot")
        val follow = src.substringAfter("suspend fun follow(").substringBefore("\n    }\n")
        assertTrue("} finally {\n            lifecycle.removeObserver(obs)\n            runCatching { vib.cancel() }" in follow, "leaving ends the buzz")
        assertTrue("Lifecycle.Event.ON_PAUSE) runCatching { vib.cancel() }" in follow, "a pause stops it")
        val play = java.io.File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("PhoneHardware.follow(vib, r.getRumbleEvents(), lifecycleOwner.lifecycle)" in play)
    }

    @Test
    fun `accelerometer is scaled to g for the core`() {
        assertTrue(kotlin.math.abs(PhoneHardware.normaliseAccel(PhoneHardware.G) - 1f) < 1e-6f)
        assertEquals(0f, PhoneHardware.normaliseAccel(0f))
    }
}
