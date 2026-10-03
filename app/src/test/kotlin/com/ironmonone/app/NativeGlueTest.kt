package com.ironmonone.app

import com.ironmonone.core.Platform
import com.swordfish.libretrodroid.StylusTouch
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The native emulator glue's fixes from the rc32 audit's P2 and P3 lists (2026-10-02). No JVM test can call into the
 * native library, so the C++ is pinned in its source; what moved into Kotlin is driven for real.
 */
class NativeGlueTest {
    private val dir = Files.createTempDirectory("glue").toFile()
    // SramGuard reports through SaveTrouble, whose once-a-minute memory is shared with SaveTroubleTest.
    @AfterTest fun cleanup() { dir.deleteRecursively(); SaveTrouble.forget() }

    private fun src(rel: String) = File("../libretrodroid/src/main/$rel").readText().replace("\r\n", "\n")
    private val core = src("cpp/libretrodroid.cpp")
    private val env = src("cpp/environment.cpp")
    private val jni = src("cpp/libretrodroidjni.cpp")
    private val audio = src("cpp/audio.cpp")
    private val view = src("java/com/swordfish/libretrodroid/GLRetroView.kt")

    /** The body of a C++ function from its signature line to the closing brace at the start of a line. */
    private fun body(text: String, signature: String): String {
        val start = text.indexOf(signature)
        assertTrue(start >= 0, "no $signature")
        return text.substring(start, text.indexOf("\n}\n", start))
    }

    @Test
    fun `a memory read or write holds the core lock throughout, so destroy cannot free what it copies (P2 122)`() {
        for (name in listOf("readMemory", "writeMemory")) {
            val b = body(core, "size_t LibretroDroid::$name(")
            val first = b.indexOf("std::lock_guard<std::mutex> lock(coreLock);")
            assertTrue(first in 0 until b.indexOf("Environment::getInstance()"), "$name locks before the descriptor read")
            assertEquals(1, b.split("std::lock_guard<std::mutex>").size - 1, "$name takes the lock once")
            assertTrue("rangeInside(address, length, systemRamBase, ramSize)" in b, "$name's range test cannot wrap (P3 93)")
        }
        assertTrue("std::lock_guard<std::mutex> lock(memoryDescriptorsLock);" in body(env, "void Environment::setMemoryMaps("))
        assertTrue("std::lock_guard<std::mutex> lock(memoryDescriptorsLock);" in body(env, "void Environment::deinitialize("))
        assertEquals(2, env.split("libretrodroid::rangeInside(address, length, start, len)").size - 1, "both descriptor range tests")
        val range = src("cpp/memrange.h")
        assertTrue("address >= start && length <= size && address - start <= size - length" in range, "written as differences")
        assertTrue("if (address < 0 || length <= 0 || length > 0x100000) return env->NewByteArray(0);" in jni, "no negative read address")
        assertTrue("if (address < 0 || length <= 0 || length > 0x1000) return 0;" in jni, "no negative write address")
    }

    @Test
    fun `the pacing sleep runs with the core lock let go, and achievements see every frame (P2 124, 125)`() {
        val step = body(core, "void LibretroDroid::step() {")
        assertTrue("std::unique_lock<std::mutex> lock(coreLock);" in step)
        assertFalse("fpsSync->wait()" in step, "no sleep under the lock")
        val unlock = step.indexOf("lock.unlock();")
        assertTrue(unlock in 0 until step.indexOf("std::this_thread::sleep_until(wakeAt);"), "let go, then sleep")
        assertTrue(step.indexOf("fpsSync->sleepTarget(wakeAt)") in 0 until unlock, "fpsSync is read under the lock")
        val loop = step.substring(step.indexOf("for (size_t i = 0; i < frames * frameSpeed; i++) {"))
        assertTrue(loop.indexOf("Cheevos::getInstance().doFrame();") < loop.indexOf("}"), "inside the frame loop")
        assertTrue(step.indexOf("if (audio) audio->serviceRebuild();") in 0 until step.indexOf("core->retro_run();"), "the sound device is reopened between frames (P3 92)")
    }

    @Test
    fun `a boot starts from the options the app sends (P2 121)`() {
        val de = body(env, "void Environment::deinitialize(")
        for (line in listOf("variables.clear();", "dirtyVariables = false;", "controllers.clear();", "sensorMask = 0;")) assertTrue(line in de, line)
        // The app sends the DSi pair with its plain-DS values when DSi mode is off, instead of leaving it out.
        val off = CoreOptions.coreVariables(Platform.NDS, CoreOptions.NDS.associate { it.key to it.default }).toMap()
        assertEquals("DS", off["melonds_console_mode"]); assertEquals("disabled", off["melonds_dsi_sdcard"])
        val was = CoreOptions.NDS.associate { it.key to it.default } + DsiMode.ON
        val on = CoreOptions.coreVariables(Platform.NDS, was).toMap()
        assertEquals("DSi", on["melonds_console_mode"]); assertEquals("enabled", on["melonds_dsi_sdcard"])
        assertTrue(CoreOptions.APP_KEYS.none { it in on }, "the app's own keys never reach the core")
        assertTrue(CoreOptions.coreVariables(Platform.GBA, mapOf("mgba_skip_bios" to "ON")).toMap() == mapOf("mgba_skip_bios" to "ON"))
        val play = File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText()
        assertTrue("CoreOptions.coreVariables(platform, coreValues).map" in play)
    }

    @Test
    fun `sound does not wind up its drift correction while the game is silent (P2 120)`() {
        val f = body(audio, "double Audio::computeDynamicBufferConversionFactor(")
        assertTrue("errorIntegral = std::clamp(errorIntegral + errorMeasure * dt, -maxi / ki, maxi / ki);" in f, "anti-windup")
        assertTrue("if (coreWriting) {" in f, "held while the core writes no sound")
        assertTrue("lastWriteNs.store(" in body(audio, "void Audio::write("))
        val err = body(audio, "void Audio::onErrorAfterClose(")
        assertTrue("rebuildPending = true;" in err && "initializeStream()" !in err, "no rebuild on Oboe's thread (P3 92)")
    }

    @Test
    fun `the rest of the native fixes are in place`() {
        assertFalse("LOGI(\"IM1" in core, "no log line per key press (P3 94)")
        val vfs = body(core, "void LibretroDroid::loadGameFromVirtualFiles(")
        assertTrue(vfs.indexOf("gameLoaded = true;") > vfs.indexOf("afterGameLoad();"), "a virtual-file game is loaded (P3 96)")
        val ser = body(core, "std::pair<int8_t*, size_t> LibretroDroid::serializeState(")
        assertTrue("if (!core->retro_serialize(data, size)) {" in ser && "delete[] data;" in ser, "a refused save state is empty (P3 99)")
        assertTrue("return loaded ? JNI_TRUE : JNI_FALSE;" in body(jni, "Java_com_swordfish_libretrodroid_LibretroDroid_unserializeSRAM("), "P3 100")
        assertTrue("std::unique_ptr<Renderer> renderer;" in src("cpp/video.h"), "P3 101")
        assertFalse("renderer = new " in src("cpp/video.cpp"))
        // P2 123: the refresh rate is followed.
        assertTrue("void LibretroDroid::setScreenRefreshRate(float refreshRate) {" in core)
        assertTrue("Java_com_swordfish_libretrodroid_LibretroDroid_setScreenRefreshRate(" in jni)
        assertTrue("registerDisplayListener(displayListener, null)" in view && "unregisterDisplayListener(displayListener)" in view)
        assertTrue("display?.refreshRate?.let { LibretroDroid.setScreenRefreshRate(it) }" in view)
        // P3 102: FrameRendered once a surface.
        assertTrue("if (!frameRenderedSent) {" in view && "frameRenderedSent = false" in view)
        assertTrue("sramLoadRefused = !LibretroDroid.unserializeSRAM(data.saveRAMState)" in view)
    }

    @Test
    fun `the DS stylus lifts on a cancelled touch and follows one finger (P2 126)`() {
        assertEquals(StylusTouch.Act.PRESS, StylusTouch.act(android.view.MotionEvent.ACTION_DOWN, false))
        assertEquals(StylusTouch.Act.PRESS, StylusTouch.act(android.view.MotionEvent.ACTION_MOVE, false))
        assertEquals(StylusTouch.Act.LIFT, StylusTouch.act(android.view.MotionEvent.ACTION_UP, false))
        assertEquals(StylusTouch.Act.LIFT, StylusTouch.act(android.view.MotionEvent.ACTION_CANCEL, false), "a back swipe the system took")
        assertEquals(StylusTouch.Act.LIFT, StylusTouch.act(android.view.MotionEvent.ACTION_POINTER_UP, true), "its own finger left")
        assertEquals(StylusTouch.Act.NONE, StylusTouch.act(android.view.MotionEvent.ACTION_POINTER_UP, false), "another finger left")
        assertTrue("StylusTouch.act(action, trackedUp)" in view)
    }

    @Test
    fun `a save the core refused is kept beside it and the player is told (P3 100)`() {
        val srm = File(dir, "firered.srm").apply { writeBytes(ByteArray(0x20000) { (it % 251).toByte() }) }
        assertTrue(SramGuard.keep(srm))
        assertTrue(File(dir, "firered.srm.refused").readBytes().contentEquals(srm.readBytes()))
        assertFalse(SramGuard.keep(File(dir, "missing.srm")), "nothing to keep")
        assertTrue("SramGuard.check(r, store.sramFile(session))" in File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText())
    }
}
