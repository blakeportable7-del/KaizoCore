package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** Play time per attempt, counted between tracker reads (RunClock). */
class RunClockTest {
    private val dir: File = Files.createTempDirectory("clock").toFile()
    private val f = File(dir, "run-clock.txt")

    @Test fun `reads a second apart add up, gaps and pauses do not count`() {
        RunClock.load(f)
        RunClock.observe("emerald-u#5", 1_000)               // first read: the mark only
        for (t in 2_000L..11_000L step 1_000) RunClock.observe("emerald-u#5", t)
        assertEquals(10, RunClock.of("emerald-u#5"))
        RunClock.observe("emerald-u#5", 60_000)              // paused for 49 s: no reads, so no play
        assertEquals(10, RunClock.of("emerald-u#5"))
        for (t in 60_700L..62_800L step 700) RunClock.observe("emerald-u#5", t)   // 700 ms steps carry the remainder
        assertEquals(12, RunClock.of("emerald-u#5"))
    }

    @Test fun `each run keeps its own time, saved and read back`() {
        RunClock.load(f)
        RunClock.observe("emerald-u#1", 0); RunClock.observe("emerald-u#1", 2_000)
        RunClock.observe("heartgold-u#1", 5_000); RunClock.observe("heartgold-u#1", 8_000)
        RunClock.save()
        RunClock.load(f)
        assertEquals(2, RunClock.of("emerald-u#1"))
        assertEquals(3, RunClock.of("heartgold-u#1"), "attempt 1 of another game is another run")
        assertEquals(0, RunClock.of(RunClock.key("emerald-u", 3)))
    }
}
