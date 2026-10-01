package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** SafeWrite: a file the player cares about is written whole or not at all. */
class SafeWriteTest {
    private val dir: File = Files.createTempDirectory("safewrite").toFile()

    @Test fun `a write replaces the file whole and leaves no temp behind`() {
        val f = File(dir, "attempts/emerald-u.txt")
        assertTrue(SafeWrite.text(f, "12"))
        assertTrue(SafeWrite.text(f, "13"))
        assertEquals("13", f.readText())
        assertFalse(File(f.parentFile, "emerald-u.txt.tmp").exists())
    }

    @Test fun `a write that cannot happen leaves the old file as it was`() {
        val f = File(dir, "pc-heals.txt").apply { writeText("5=9\n") }
        val blocked = File(dir, "pc-heals.txt.tmp").apply { mkdirs() }   // a directory where the temp file must go
        assertFalse(SafeWrite.text(f, "5=10\n"))
        assertEquals("5=9\n", f.readText())
        blocked.delete()
    }

    @Test fun `the player's counters write through it`() {
        val src = File("src/main/kotlin/com/ironmonone/app")
        assertTrue("SafeWrite.text(f, seconds" in File(src, "RunClock.kt").readText(), "run clock")
        assertTrue("SafeWrite.text(f, counts" in File(src, "PcHeals.kt").readText(), "heal counts")
        assertTrue("SafeWrite.text(attemptFile(romId)" in File(src, "PrepStore.kt").readText(), "attempts")
        assertTrue("SafeWrite.text(file, runs" in File(src, "RunHistory.kt").readText(), "run history")
        assertTrue("SafeWrite.text(dsTrackedFile" in File(src, "StatMarks.kt").readText(), "the DS Pokecenter count")
    }
}
