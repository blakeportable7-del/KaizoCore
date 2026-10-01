package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Safari Zone record (Tracker.TrackSafariEncounter): kept with the run and listed the way the
 * reference's !pivots lists it, highest level first. It used to live only in the tracker's memory
 * with nothing on screen reading it (Blake, 2026-09-29: show it somewhere).
 */
class SafariRecordTest {
    private val dir: File = Files.createTempDirectory("safari").toFile()
    private fun marks() = StatMarks(File(dir, "marks.txt"))

    @Test fun `highest level kept, listed highest first, saved with the run`() {
        val m = marks()
        assertTrue(m.seeSafari(147, 32, 22))
        assertTrue(m.seeSafari(147, 111, 25))
        assertTrue(m.seeSafari(147, 32, 24))
        assertFalse(m.seeSafari(147, 32, 20), "a lower level does not replace a higher one")
        assertEquals(listOf(111 to 25, 32 to 24), m.safariSeen(147))
        // Ties go by species, as EventData.getPivots' safariSort does.
        m.seeSafari(148, 46, 23); m.seeSafari(148, 29, 23)
        assertEquals(listOf(29 to 23, 46 to 23), m.safariSeen(148))
        assertEquals(listOf(111 to 25, 32 to 24), marks().safariSeen(147))
        m.clear()
        assertTrue(marks().safariSeen(147).isEmpty())
    }

    @Test fun `a staged Demo battle writes nothing`() {
        val m = marks()
        m.seeSafari(147, 32, 22, save = false)
        assertEquals(listOf(32 to 22), m.safariSeen(147))
        assertTrue(marks().safariSeen(147).isEmpty())
    }
}
