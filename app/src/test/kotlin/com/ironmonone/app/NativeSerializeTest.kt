package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * rc33 audit P0-1: the JNI wrappers for serializeState and serializeSRAM copied the core's new[] buffer into a Java
 * array and never freed it, so every save state and every rewind snapshot leaked its size (15 MB in 90 seconds on
 * Crystal at 1x on the emulator, 2026-10-01; flat after the fix). No JVM test can call into the native library, so
 * this pins the fix in the source: each wrapper hands the buffer to a unique_ptr before anything can return.
 */
class NativeSerializeTest {
    private val jni = File("../libretrodroid/src/main/cpp/libretrodroidjni.cpp").readText().replace("\r\n", "\n")
    private val core = File("../libretrodroid/src/main/cpp/libretrodroid.cpp").readText().replace("\r\n", "\n")

    private fun body(name: String): String {
        val start = jni.indexOf("Java_com_swordfish_libretrodroid_LibretroDroid_$name(")
        assertTrue(start >= 0, "$name's JNI wrapper")
        val end = jni.indexOf("\n}\n", start)
        return jni.substring(start, end)
    }

    @Test
    fun `both serialize wrappers free the copy the core made`() {
        for (name in listOf("serializeState", "serializeSRAM")) {
            val b = body(name)
            val call = b.indexOf("LibretroDroid::getInstance().$name()")
            val owned = b.indexOf("std::unique_ptr<int8_t[]> owned(data);")
            val array = b.indexOf("env->NewByteArray(size)")
            assertTrue(call >= 0 && owned > call && array > owned, "$name: the buffer is owned before the Java array is made")
            assertTrue("owned.get()" in b, "$name copies from the owned buffer")
            assertTrue("result != nullptr" in b, "$name does not copy into a failed allocation")
        }
    }

    /**
     * rc33 audit P1: at 4x and up one call's step can pass the end of the resampler's buffer; dropping every frame
     * left it empty, and the next underrun read the "last frame" at size - 2, outside it, on every audio callback.
     */
    @Test
    fun `the resampler never empties its buffer and never reads a frame it does not have`() {
        val c = File("../libretrodroid/src/main/cpp/resamplers/cubicresampler.cpp").readText().replace("\r\n", "\n")
        assertTrue("if (pending.size() < 2) { pending.assign(2, 0.0f); }" in c)
        val hold = c.indexOf("if (pending.size() < 2) { pending.assign(2, 0.0f); }")
        assertTrue(hold < c.indexOf("const float l = pending[pending.size() - 2]"), "silence first, before the hold reads the last frame")
        assertTrue("std::min(static_cast<int32_t>(std::floor(position)) - 1, frames - 1)" in c, "at least one frame stays")
    }

    /** rc33 audit P1: RetroAchievements mapped the game's memory only after rc_client had checked every address. */
    @Test
    fun `achievements see the game's memory while they load`() {
        val c = File("../libretrodroid/src/main/cpp/cheevos.cpp").readText().replace("\r\n", "\n")
        val load = c.substring(c.indexOf("void Cheevos::loadGame("), c.indexOf("void Cheevos::unloadGame("))
        assertTrue(load.indexOf("initMemory(consoleId);") in 0 until load.indexOf("rc_client_begin_identify_and_load_game("))
        assertTrue("if (!self.regionsReady || self.regionsConsole != g->console_id) self.initMemory(g->console_id);" in c)
    }

    @Test
    fun `the core still hands over a new array buffer, which is what the wrappers free`() {
        // If the core ever returned a pointer into its own memory, freeing it would be a crash, not a fix.
        assertTrue("auto data = new int8_t[size];" in core, "serializeState allocates its copy")
        assertTrue("auto* data = new int8_t[size];" in core, "serializeSRAM allocates its copy")
        assertTrue(Regex("return \\{ new int8_t\\[0\\], 0 \\};").findAll(core).count() == 2, "the empty returns are allocated too")
    }
}
