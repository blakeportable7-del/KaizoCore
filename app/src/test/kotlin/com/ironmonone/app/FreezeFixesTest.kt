package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 2026-09-30: a player's AYN Thor (two screens) sent an ANR, "Input dispatching timed out (Application does not
 * have a focused window)", and the report kept only the dump thread's frames. Three fixes, three guards:
 * the app's waits on the emulation thread are bounded, a density change (moving to the other screen) no longer
 * rebuilds the activity, and a freeze's report keeps the stuck threads.
 */
class FreezeFixesTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "libretrodroid").isDirectory && File(it, "app").isDirectory }

    @Test
    fun waitsOnTheEmulationThreadAreBounded() {
        val view = File(root, "libretrodroid/src/main/java/com/swordfish/libretrodroid/GLRetroView.kt").readText()
        val body = view.substringAfter("private fun <T> runOnEmulationThread(").substringBefore("private fun buildShader")
        assertFalse("awaitUninterruptibly" in body, "an unbounded wait froze the app")
        assertTrue("latch.await(EMULATION_WAIT_MS" in body)
        assertTrue("return fallback" in body)
        val wait = Regex("EMULATION_WAIT_MS = ([0-9_]+)L").find(view)!!.groupValues[1].replace("_", "").toLong()
        val grace = Regex("EMULATION_GRACE_MS = ([0-9_]+)L").find(view)!!.groupValues[1].replace("_", "").toLong()
        assertTrue(wait + grace < 5_000, "under Android's 5 seconds for input")
        // Every caller says what a call that never ran gives back.
        assertEquals(0, Regex("""runOnEmulationThread\((true|useEmulationThread)\)""").findAll(view).count())
    }

    @Test
    fun aDensityChangeKeepsTheGameRunning() {
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        val changes = Regex("""android:configChanges="([^"]+)"""").find(manifest)!!.groupValues[1].split('|')
        assertTrue("density" in changes, "moving to a screen with another density rebuilt the activity mid-game")
        for (c in listOf("orientation", "screenSize", "smallestScreenSize", "screenLayout")) assertTrue(c in changes)
    }

    @Test
    fun aFreezeReportKeepsTheStuckThreads() {
        val trace = """
            ----- pid 4242 at 2026-09-30 15:21:36.000000000-0600 -----
            Cmd line: com.ironmonone.app

            DALVIK THREADS (40):
            "main" prio=5 tid=1 Waiting
              | group="main" sCount=1 ucsCount=0 flags=1 obj=0x72d6e6a8 self=0xb400007a
              | state=S schedstat=( 0 0 0 ) utm=900 stm=120 core=4 HZ=100
              at jdk.internal.misc.Unsafe.park(Native method)
              - waiting on an unknown object
              at java.util.concurrent.CountDownLatch.await(CountDownLatch.java:230)
              at com.swordfish.libretrodroid.GLRetroView.runOnEmulationThread(GLRetroView.kt:461)
              at com.swordfish.libretrodroid.GLRetroView.serializeState(GLRetroView.kt:193)
              at com.ironmonone.app.PlayScreenKt${'$'}PlayScreen${'$'}12.invokeSuspend(PlayScreen.kt:681)

            "Signal Catcher" daemon prio=10 tid=6 Runnable
              native: #00 pc 000000000055fdcc  /apex/com.android.art/lib64/libart.so (art::DumpNativeStack+152)

            "GLThread 1234" prio=5 tid=21 Native
              | state=S schedstat=( 0 0 0 ) utm=10 stm=1 core=2 HZ=100
              native: #00 pc 0000000000085d8c  /apex/com.android.runtime/lib64/bionic/libc.so (__ioctl+12)
              at android.opengl.EGL14.eglSwapBuffers(Native method)
              at android.opengl.GLSurfaceView${'$'}GLThread.guardedRun(GLSurfaceView.java:1363)
        """.trimIndent()
        val lines = CrashLog.anrThreads(trace)
        assertEquals("\"main\" prio=5 tid=1 Waiting", lines.first())
        assertTrue(lines.any { "GLRetroView.runOnEmulationThread" in it }, "the stuck main thread's frames")
        assertTrue(lines.any { it.startsWith("\"GLThread 1234\"") }, "and what the emulation thread was doing")
        assertTrue(lines.any { "eglSwapBuffers" in it })
        assertFalse(lines.any { "Signal Catcher" in it || "DumpNativeStack" in it }, "not the dump thread")
        assertFalse(lines.any { "group=" in it }, "only frames, waits and the state line")
    }

    @Test
    fun aTraceWithoutAMainThreadGivesNothing() {
        assertEquals(emptyList(), CrashLog.anrThreads("not a trace at all"))
    }
}
