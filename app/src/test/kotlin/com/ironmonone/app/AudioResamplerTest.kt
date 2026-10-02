package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Blake, 2026-10-01: "the audio from the app is a bit crackling ... it sounds like it has some static to it". mGBA sends
 * 65,536 Hz and CubicResampler folded everything above 24 kHz back into the audible band. The numbers are measured by
 * tools/audio (resampler_harness.cpp, pcm_dump.cpp); this pins Audio to the resampler that filters first.
 */
class AudioResamplerTest {
    private val cpp = File("../libretrodroid/src/main/cpp")

    @Test
    fun `the audio path resamples through the band-limited resampler`() {
        val audio = File(cpp, "audio.h").readText()
        assertTrue("WindowedSincResampler resampler;" in audio)
        assertTrue("CubicResampler resampler;" !in audio)
        assertTrue("resamplers/windowedsincresampler.cpp" in File(cpp, "CMakeLists.txt").readText())
    }

    @Test
    fun `its cutoff sits under the lower Nyquist`() {
        val src = File(cpp, "resamplers/windowedsincresampler.cpp").readText()
        assertTrue("const double fc = 0.5 * ALPHA / std::max(1.0, step);" in src)
        val h = File(cpp, "resamplers/windowedsincresampler.h").readText()
        assertTrue("static constexpr double ALPHA = 0.92;" in h)
    }
}
