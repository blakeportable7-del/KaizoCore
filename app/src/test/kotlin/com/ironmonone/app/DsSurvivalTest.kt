package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Survival's Pokemon Center limit on DS (the modes audit): the DS tracker's own counter comes on
 * at the rules' limit once per run, the 8th badge adds the bonus once, and a run the app meets
 * mid-way keeps what the player has.
 */
class DsSurvivalTest {
    private val dir: File = Files.createTempDirectory("dssurv").toFile()
    private fun marks() = StatMarks(File(dir, "marks.txt"))

    @BeforeTest fun setUp() { TrackerOptions.dsPokecenterHeals = false }
    @AfterTest fun tearDown() { TrackerOptions.dsPokecenterHeals = false; dir.deleteRecursively() }

    @Test fun `a Survival run switches the counter on at 10, once`() {
        val m = marks()
        assertTrue(PcHeals.observeDsSurvival(m, 0, PcHeals.Limit.SURVIVAL))
        assertTrue(TrackerOptions.dsPokecenterHeals)
        assertEquals(10, m.dsPokecenterCount())
        TrackerOptions.dsPokecenterHeals = false        // the player switches it off
        m.bumpDsPokecenter(false)                        // and uses a heal
        assertFalse(PcHeals.observeDsSurvival(m, 1, PcHeals.Limit.SURVIVAL))
        assertFalse(TrackerOptions.dsPokecenterHeals, "armed once per run: the player's choice stands")
        assertEquals(9, marks().dsPokecenterCount(), "kept with the run")
    }

    @Test fun `Survival Revival starts at 5`() {
        val m = marks()
        PcHeals.observeDsSurvival(m, 1, PcHeals.Limit.REVIVAL)
        assertEquals(5, m.dsPokecenterCount())
    }

    @Test fun `the 8th badge adds the bonus heal once`() {
        val m = marks()
        PcHeals.observeDsSurvival(m, 0, PcHeals.Limit.SURVIVAL)
        assertFalse(PcHeals.observeDsSurvival(m, 7, PcHeals.Limit.SURVIVAL))
        assertTrue(PcHeals.observeDsSurvival(m, 8, PcHeals.Limit.SURVIVAL))
        assertEquals(11, m.dsPokecenterCount())
        assertFalse(PcHeals.observeDsSurvival(m, 9, PcHeals.Limit.SURVIVAL))
        assertEquals(11, marks().dsPokecenterCount(), "once, and kept")
    }

    @Test fun `a run met mid-way keeps its count and gets no second bonus`() {
        val m = marks()
        repeat(4) { m.bumpDsPokecenter(false) }          // 6 left, counted by hand before the update
        assertTrue(PcHeals.observeDsSurvival(m, 9, PcHeals.Limit.SURVIVAL))
        assertEquals(6, m.dsPokecenterCount())
        assertFalse(PcHeals.observeDsSurvival(m, 10, PcHeals.Limit.SURVIVAL))
        assertEquals(6, m.dsPokecenterCount())
    }

    /** HeartGold and SoulSilver (2026-09-30): the 7 heals for Kanto once the Johto League is beaten, once. */
    @Test fun `beating the Johto League adds the 7 heals for Kanto, once, on Survival only`() {
        val m = marks()
        PcHeals.observeDsSurvival(m, 0, PcHeals.Limit.SURVIVAL)
        PcHeals.observeDsSurvival(m, 8, PcHeals.Limit.SURVIVAL)
        repeat(6) { m.bumpDsPokecenter(false) }          // 11 with the badge bonus, 6 used: 5 left
        assertFalse(PcHeals.observeDsSurvival(m, 8, PcHeals.Limit.SURVIVAL, leagueBeaten = false))
        assertTrue(PcHeals.observeDsSurvival(m, 8, PcHeals.Limit.SURVIVAL, leagueBeaten = true))
        assertEquals(12, m.dsPokecenterCount(), "5 left, and 7 for Kanto")
        assertFalse(PcHeals.observeDsSurvival(m, 9, PcHeals.Limit.SURVIVAL, leagueBeaten = true))
        assertEquals(12, marks().dsPokecenterCount(), "once, and kept")
        // Survival Revival has no such rule.
        val r = StatMarks(File(dir, "revival/marks.txt"))  // a run of its own: the DS values sit beside the marks file
        PcHeals.observeDsSurvival(r, 0, PcHeals.Limit.REVIVAL)
        assertFalse(PcHeals.observeDsSurvival(r, 0, PcHeals.Limit.REVIVAL, leagueBeaten = true))
        assertEquals(5, r.dsPokecenterCount())
        // A run met already past the League (the app updated mid-run) keeps what the player counted.
        val late = StatMarks(File(dir, "late/marks.txt"))
        repeat(2) { late.bumpDsPokecenter(true) }
        assertTrue(PcHeals.observeDsSurvival(late, 9, PcHeals.Limit.SURVIVAL, leagueBeaten = true))
        assertEquals(12, late.dsPokecenterCount(), "no second 7")
        // Play hands the tracker's League byte over.
        assertTrue("PcHeals.limitForLastRunCached(), s.leagueBeaten)" in File("src/main/kotlin/com/ironmonone/app/PlayScreen.kt").readText())
    }

    @Test fun `not a Survival run, nothing, and a new run starts over`() {
        val m = marks()
        assertFalse(PcHeals.observeDsSurvival(m, 8, null))
        assertFalse(TrackerOptions.dsPokecenterHeals)
        PcHeals.observeDsSurvival(m, 0, PcHeals.Limit.SURVIVAL)
        m.clear()
        assertTrue(PcHeals.observeDsSurvival(m, 0, PcHeals.Limit.REVIVAL), "the new run arms again")
        assertEquals(5, m.dsPokecenterCount())
    }

    /**
     * rc32 audit P2 #91: Tracker Setup's CLEAR TRACKED DATA tidies the notes in the middle of a run. It wiped the DS run's
     * counters too, and the next read armed Survival again at 10: the heals already used were back.
     */
    @Test fun `clearing tracked data keeps the heals used, the Hidden Power type and the progress`() {
        val m = marks()
        PcHeals.observeDsSurvival(m, 0, PcHeals.Limit.SURVIVAL)
        repeat(7) { m.bumpDsPokecenter(false) }          // 3 left
        repeat(5) { m.stepDsHiddenPower(forward = true) } // Bug, Dark, Dragon, Electric, Fighting, Fire
        m.noteDsProgress(1)
        m.setNote(25, "outspeeds")
        m.clear(keepRunCounters = true)
        assertEquals("", m.noteFor(25), "the notes go")
        assertFalse(PcHeals.observeDsSurvival(m, 0, PcHeals.Limit.SURVIVAL), "not armed again")
        assertEquals(3, m.dsPokecenterCount())
        assertEquals("FIRE", m.dsHiddenPowerType())
        assertEquals(1, m.dsProgress())
        val read = marks()
        assertEquals(Triple(3, "FIRE", 1), Triple(read.dsPokecenterCount(), read.dsHiddenPowerType(), read.dsProgress()), "and on disk")
        // A new run's clear still starts them over.
        m.clear()
        val fresh = marks()
        assertEquals(Triple(10, "BUG", 0), Triple(fresh.dsPokecenterCount(), fresh.dsHiddenPowerType(), fresh.dsProgress()))
    }

    private val gear get() = File("src/main/kotlin/com/ironmonone/app/TrackerGearDialog.kt").readText()

    @Test fun `Tracker Setup's YES, CLEAR is the clear that keeps them`() {
        assertTrue("marks.clear(keepRunCounters = true); clears++" in gear)
    }

    /** rc32 audit P3 #70: the same StatMarks object after the clear, so a list remembered on it alone kept every name. */
    @Test fun `Tracker Setup's Notebook list is built again after a clear`() {
        assertTrue("val noted = remember(marks, clears)" in gear, "the Notebook list was remembered on the marks alone")
    }
}
