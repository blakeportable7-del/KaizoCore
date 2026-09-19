package com.ironmonone.app

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Hide stats until summary shown": Tracker.Data.hasCheckedSummary, per attempt. */
class SummaryChecksTest {
    private val dir = Files.createTempDirectory("sum").toFile()
    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test
    fun `an attempt stays checked once a summary is seen, and a new attempt starts hidden`() {
        val f = java.io.File(dir, "summary-checked.txt")
        SummaryChecks.load(f)
        assertFalse(SummaryChecks.checked(12))
        SummaryChecks.mark(12)
        assertTrue(SummaryChecks.checked(12))
        SummaryChecks.load(f)                       // a restart
        assertTrue(SummaryChecks.checked(12))
        assertFalse(SummaryChecks.checked(13), "a new run hides again")
    }
}
