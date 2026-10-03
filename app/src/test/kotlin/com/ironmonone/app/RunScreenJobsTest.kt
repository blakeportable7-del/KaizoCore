package com.ironmonone.app

import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Kaizo IronMON screen's file jobs: the export and the patch jobs' words. */
class RunScreenJobsTest {
    private val src = File("src/main/kotlin/com/ironmonone/app")
    private fun read(name: String) = File(src, name).readText().replace("\r\n", "\n")

    /** rc32 audit P2 #79: the export read the whole run into one array, and a 512 MB Black 2 run never fit. */
    @Test
    fun `the export copies the run a megabyte at a time`() {
        val run = File(Files.createTempDirectory("export").toFile(), "current.nds")
        val bytes = ByteArray(3 * RunExport.CHUNK + 17) { (it * 31 + it / 7).toByte() }
        run.writeBytes(bytes)
        var largest = 0
        val got = java.io.ByteArrayOutputStream()
        val out = object : OutputStream() {
            override fun write(b: Int) { largest = maxOf(largest, 1); got.write(b) }
            override fun write(b: ByteArray, off: Int, len: Int) { largest = maxOf(largest, len); got.write(b, off, len) }
        }
        RunExport.copy(run, out)
        assertContentEquals(bytes, got.toByteArray(), "the whole run, as it is")
        assertTrue(largest <= 1 shl 20, "no single write past a megabyte: $largest")
        val screen = read("RunScreen.kt")
        assertFalse("currentRun.readBytes()" in screen, "the screen no longer reads the run into memory")
        assertTrue("context.contentResolver.openOutputStream(uri)!!.use { RunExport.copy(store.currentRun, it) }" in screen)
    }

    /**
     * rc32 audit P3 #62: making the Nat. Dex version said "version.. Try again." and dropped PrepRun's own worded
     * refusals, such as a patch this build does not carry. A worded failure is said as it is now, as Library says it.
     */
    @Test
    fun `a patch job's failure is said once, in its own words when it has them`() {
        val what = "Could not make the Nat. Dex version"
        assertEquals(NOT_IN_THIS_BUILD, PresetStrings.plain(PrepFailure(NOT_IN_THIS_BUILD), what))
        assertEquals(NEED_NATDEX_PATCH, PresetStrings.plain(NeedPatch(), what))
        assertEquals("Could not make the Nat. Dex version. Try again.", PresetStrings.plain(java.io.IOException("broken pipe"), what))
        assertEquals("Could not make the Nat. Dex version: the phone is out of storage.",
            PresetStrings.plain(java.io.IOException("write failed: ENOSPC (No space left on device)"), what))
        // The growth patch's refusals are worded failures too, not plain exceptions that read "Try again".
        val growth = read("GrowthPatch.kt")
        assertFalse("IllegalStateException(" in growth)
        assertEquals(4, Regex("throw PrepFailure\\(").findAll(growth).count())
        assertTrue("GrowthPatch.made(GrowthPatch.make(context, store, rom, base)) to false" in read("RunScreen.kt"))
    }

    @Test
    fun `no job's failure line ends in a full stop, so the status never doubles it`() {
        val calls = Regex("RunJob\\.run\\(RunPhase\\.[A-Z_]+, \"([^\"]*)\"\\)").findAll(
            src.listFiles()!!.filter { it.extension == "kt" }.joinToString("\n") { it.readText() },
        ).map { it.groupValues[1] }.toList()
        assertTrue(calls.size >= 3, "found the jobs: $calls")
        for (what in calls + GrowthPatch.FAILED) assertFalse(what.endsWith("."), "\"$what\" ends in a full stop")
    }
}
