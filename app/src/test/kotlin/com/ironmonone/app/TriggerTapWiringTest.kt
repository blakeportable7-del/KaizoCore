package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the Gen 3 tracker's per-frame trigger tap is wired (Blake, 2026-10-04: abilities missed at 8x, caught at 4x).
 * tracker-gba's AbilityRevealSpeedTest proves what the tracker does with what the tap catches, frame by frame on the
 * real ROMs; this holds the links a unit test cannot run: the play screen arms the core's tap for the tracker it makes,
 * the core looks after every emulated frame (fast forward included) at no cost while the tap is off, lets go of it when
 * the game is unloaded, and the JNI names match on both sides.
 */
class TriggerTapWiringTest {
    private fun src(path: String) = File(path).readText().replace("\r\n", "\n")
    private val cpp = "../libretrodroid/src/main/cpp"
    private fun body(source: String, head: String) = source.substringAfter(head).substringBefore("\n}\n")

    @Test
    fun `the play screen's Gen 3 tracker gets the core's tap`() {
        val play = src("src/main/kotlin/com/ironmonone/app/PlayScreen.kt")
        assertTrue("else RetroTriggerTap.attach(com.ironmonone.tracker.GbaTracker(\n" in play, "the tracker is made with the tap")
        assertEquals(1, Regex("""GbaTracker\(""").findAll(play).count(), "one Gen 3 tracker in the play screen, and it has the tap")
        val tap = src("src/main/kotlin/com/ironmonone/app/RetroTriggerTap.kt")
        assertTrue("token = retro.armTriggerTap(watch, targets, rangeAddresses, rangeLengths)" in tap)
        assertTrue("override fun drain(): ByteArray = retro.drainTriggerTap()" in tap)
        // A disarm lets go of its own arming only (the core's token), never a newer tracker's.
        assertTrue("override fun disarm() { token.takeIf { it != 0L }?.let { retro.disarmTriggerTap(it) }; token = 0L }" in tap)
    }

    @Test
    fun `the core looks after every emulated frame and forgets the tap when the game goes`() {
        val core = src("$cpp/libretrodroid.cpp")
        // step(): every frame of fast forward and catch-up, as RetroAchievements; stepBot(): the test bot's frames.
        assertTrue(
            "            core->retro_run();\n            if (gameLoaded) Cheevos::getInstance().doFrame();\n" +
                "            // KaizoCore patch (2026-10-04): every emulated frame, fast forward included (triggertap.h).\n" +
                "            if (gameLoaded) tapFrame();\n" in core, "step()")
        assertTrue("        Cheevos::getInstance().doFrame();\n        tapFrame();   // KaizoCore patch (2026-10-04)" in core, "stepBot()")
        // Off: one test and out, before the core is asked for anything.
        assertTrue("if (!tap.isArmed() || core == nullptr || framesRun < 120) return;" in body(core, "void LibretroDroid::tapFrame() {"), "tapFrame()")
        val destroy = core.substringAfter("void LibretroDroid::destroy() {").substringBefore("void LibretroDroid::resume()")
        assertTrue("    TriggerTap::getInstance().disarm(0);" in destroy, "destroy()")
        // arm, disarm and drain take coreLock, which the frames hold: the tap has no lock of its own.
        for (f in listOf("uint32_t LibretroDroid::armTriggerTap(", "void LibretroDroid::disarmTriggerTap(", "std::vector<unsigned char> LibretroDroid::drainTriggerTap(")) {
            assertTrue("    std::lock_guard<std::mutex> lock(coreLock);" in body(core, f), f)
        }
        val doFrame = body(src("$cpp/triggertap.cpp"), "void TriggerTap::doFrame(")
        // No allocation, no lock, no JNI in a frame; a read only once the core's buffer is known to reach every armed offset.
        for (no in listOf("new ", "malloc", "vector", "push_back", "mutex", "lock", "JNIEnv")) assertTrue(no !in doFrame, "doFrame has $no")
        val bounds = doFrame.indexOf("ramSize < ramNeeded")
        assertTrue(bounds >= 0 && bounds < doFrame.indexOf("ram + watchOffset"), "bounds before the read")
        assertTrue("triggertap.cpp" in src("$cpp/CMakeLists.txt"), "built")
    }

    @Test
    fun `the JNI names match the Java natives`() {
        val java = src("../libretrodroid/src/main/java/com/swordfish/libretrodroid/LibretroDroid.java")
        val jni = src("$cpp/libretrodroidjni.cpp")
        for ((native, c) in listOf(
            "public static native long armTriggerTap(long watch, long[] targets, long[] rangeAddresses, int[] rangeLengths);" to
                "JNIEXPORT jlong JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_armTriggerTap(",
            "public static native void disarmTriggerTap(long token);" to "JNIEXPORT void JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_disarmTriggerTap(",
            "public static native byte[] drainTriggerTap();" to "JNIEXPORT jbyteArray JNICALL Java_com_swordfish_libretrodroid_LibretroDroid_drainTriggerTap(",
        )) {
            assertTrue(native in java, native)
            assertTrue(c in jni, c)
        }
    }
}
