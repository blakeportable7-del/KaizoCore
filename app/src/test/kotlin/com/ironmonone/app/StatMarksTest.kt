package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * StatMarks reads three files at construction. That constructor crashed with a
 * NullPointerException the moment route sightings were added, because Kotlin
 * runs initializers in DECLARATION order and the new backing map was declared
 * BELOW the init block that loads it - so init called loadRoutes() while the
 * map was still null, and every Play tab died on open.
 *
 * These tests construct it for real, which is all it took to catch that.
 */
class StatMarksTest {

    private val dir: File = Files.createTempDirectory("statmarks").toFile()
    private fun marks() = StatMarks(File(dir, "marks.txt"))

    @AfterTest fun cleanup() { dir.deleteRecursively() }

    @Test
    fun `constructing it on an empty directory does not throw`() {
        val m = marks()
        assertEquals(0, m.seenOnRoute(17).size)
        assertEquals("", m.noteFor(1))
    }

    @Test
    fun `route sightings persist and reload`() {
        val m = marks()
        assertTrue(m.seeOnRoute(17, 261))
        assertFalse(m.seeOnRoute(17, 261), "the same species twice is not new")
        assertTrue(m.seeOnRoute(17, 263))
        assertTrue(m.seeOnRoute(18, 265))

        // A fresh instance reads what the last one wrote.
        val again = marks()
        assertEquals(setOf(261, 263), again.seenOnRoute(17))
        assertEquals(setOf(265), again.seenOnRoute(18))
    }

    /**
     * The whole point of tapping a stat box.
     *
     * cycle() writes on every tap, but nothing asserted the file could be READ
     * BACK - and a mark that survives only until the Play tab is rebuilt is
     * indistinguishable from one that was never stored, right up until the
     * moment you need it.
     */
    @Test
    fun `stat marks persist and reload`() {
        val a = marks()
        // Cycle to a definite state rather than assuming what one tap means.
        val hp = a.cycle(793, 0)
        val spe = a.cycle(793, 5)
        a.cycle(793, 5)
        val speTwice = a.of(793)[5]
        assertTrue(hp != 0, "one tap left the mark at its default")
        assertTrue(speTwice != spe, "a second tap did not advance the mark")

        // A FRESH instance on the same file: this is what reopening the tab does.
        val b = marks()
        assertEquals(hp, b.of(793)[0], "the HP mark did not survive")
        assertEquals(speTwice, b.of(793)[5], "the SPE mark did not survive")
        // Untouched stats must not be invented on the way back in.
        assertEquals(0, b.of(793)[1])
        // A species never marked stays clean.
        assertEquals(0, b.of(412)[0])
    }

    @Test
    fun `a note persists and reloads`() {
        val a = marks()
        a.setNote(793, "outsped my lead")
        assertEquals("outsped my lead", marks().noteFor(793))
    }

    @Test
    fun `a new run clears sightings along with the marks`() {
        val m = marks()
        m.seeOnRoute(17, 261)
        m.cycle(261, 0)
        m.setNote(261, "outsped me")
        m.clear()

        val again = marks()
        assertEquals(emptySet(), again.seenOnRoute(17))
        assertEquals("", again.noteFor(261))
        assertFalse(again.hasAny(261))
    }

    @Test
    fun `nonsense ids are refused rather than stored`() {
        val m = marks()
        assertFalse(m.seeOnRoute(0, 261))
        assertFalse(m.seeOnRoute(17, 0))
    }
}
