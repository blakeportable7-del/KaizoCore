package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreOptionsTest {

    /** Whether [needle] occurs in [hay]: the shipped core's option strings are plain C strings in its .so. */
    private fun contains(hay: ByteArray, needle: ByteArray): Boolean {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return true
        }
        return false
    }

    @Test
    fun `DS sound is 16-bit and cubic unless the player picks the DS's own, in words the shipped core knows`() {
        val bits = CoreOptions.NDS.first { it.key == "melonds_audio_bitrate" }
        val interp = CoreOptions.NDS.first { it.key == "melonds_audio_interpolation" }
        assertEquals("16-bit", bits.default, "10-bit is the grain Blake heard as crackling (2026-10-02)")
        assertEquals("Cubic", interp.default)
        assertTrue("10-bit" in bits.values && "None" in interp.values, "the DS's own sound stays one tap away")
        // Measured on the stream tap: a bit depth picked mid-game waits for the next boot, the interpolation does not.
        assertTrue(bits.restart, "the bit depth says it applies on the next boot")
        assertTrue(!interp.restart, "the interpolation changes at once")
        assertEquals("16-bit", CoreOptionStore(Files.createTempDirectory("ds").toFile()).effective(Platform.NDS)["melonds_audio_bitrate"],
            "a player with no stored value gets it, as every player who never opened the settings does")
        // Each key and value, NUL-terminated, in both shipped builds of the core: a word it does not know is ignored
        // and the core falls back to its own default, which is the 10-bit sound.
        for (abi in listOf("arm64-v8a", "x86_64")) {
            val so = File("src/main/jniLibs/$abi/libmelonds_libretro_android.so").readBytes()
            for (s in listOf(bits.key, interp.key) + bits.values + interp.values)
                assertTrue(contains(so, (s + "\u0000").toByteArray()), "$abi core lacks $s")
        }
    }

    @Test
    fun `every console's catalogue is well-formed and keys are unique`() {
        for (p in Platform.entries) {
            val opts = CoreOptions.forPlatform(p)
            assertTrue(opts.isNotEmpty(), p.name)
            assertEquals(opts.size, opts.map { it.key }.toSet().size, "$p has a duplicate key")
            for (o in opts) {
                assertTrue(o.values.isNotEmpty() && o.default in o.values, o.key)
                assertEquals(o.values[(o.values.indexOf(o.default) + 1) % o.values.size], o.next(o.default))
                assertEquals(o.values[0], o.next("not-a-value"), "unknown current wraps to the first value")
            }
            assertTrue(opts.any { it.key == CoreOptions.FILTER_KEY }, "$p has the video filter row")
        }
    }

    @Test
    fun `only changed values are stored, defaults are dropped, unknown keys are ignored`() {
        val dir = Files.createTempDirectory("opts").toFile()
        val s = CoreOptionStore(dir)
        assertTrue(s.load(Platform.GBA).isEmpty())
        s.set(Platform.GBA, "mgba_color_correction", "GBA")
        s.set(Platform.GBA, "mgba_frameskip", "disabled")   // the default: not stored
        assertEquals(mapOf("mgba_color_correction" to "GBA"), s.load(Platform.GBA))
        assertEquals("GBA", s.effective(Platform.GBA)["mgba_color_correction"])
        assertEquals("disabled", s.effective(Platform.GBA)["mgba_frameskip"])
        File(dir, "gba.properties").appendText("\nmystery=1\nmgba_frameskip=bogus\n")
        assertEquals(mapOf("mgba_color_correction" to "GBA"), s.load(Platform.GBA), "junk and bad values dropped")
        s.set(Platform.GBA, "mgba_color_correction", "OFF")
        assertTrue(s.load(Platform.GBA).isEmpty() && !File(dir, "gba.properties").exists(), "back to default removes the file")
        s.set(Platform.NDS, "melonds_mic_input", "blow")
        assertTrue(s.load(Platform.GBA).isEmpty(), "per console")
        s.reset(Platform.NDS); assertTrue(s.load(Platform.NDS).isEmpty())
    }

    @Test
    fun `number scales step through their allowed values and labels read as words`() {
        val gba = CoreOptions.GBA.associateBy { it.key }
        val range = gba.getValue("mgba_audio_low_pass_range")
        assertEquals((5..95 step 5).toList(), range.numbers)
        assertEquals("65", range.snap(61, 60), "+ on a scale in fives moves a whole step")
        assertEquals("55", range.snap(59, 60))
        assertEquals("60", range.snap(62, null), "a typed number goes to the nearest allowed value")
        val solar = gba.getValue("mgba_solar_sensor_level")
        assertEquals((0..10).toList(), solar.numbers)
        assertEquals(listOf("sensor"), solar.extras)
        assertEquals(null, gba.getValue("mgba_frameskip").numbers)
        assertEquals(null, CoreOptions.NDS.first { it.key == "melonds_hybrid_ratio" }.numbers, "two values are chips, not a stepper")
        assertEquals("Off", CoreOptions.display("OFF")); assertEquals("On", CoreOptions.display("yes"))
        assertEquals("Mix (smart)", CoreOptions.display("mix_smart"))
        assertEquals("Don't remove", CoreOptions.display("Don't Remove"))
        assertEquals("GB DMG", CoreOptions.display("GB - DMG"))
        assertEquals("GBC dark blue", CoreOptions.display("GBC - Dark Blue"))
        assertTrue("custom" !in CoreOptions.GBC.first { it.key == "gambatte_gb_colorization" }.values, "no way to import a custom palette")
        assertEquals(CoreOptions.DSI_GROUP, CoreOptions.NDS.first { it.key == "melonds_console_mode" }.group,
            "the console row belongs to the DSi section, which checks the files first")
    }
}
