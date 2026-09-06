package com.ironmonone.app

import com.ironmonone.core.Platform
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreOptionsTest {

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
}
