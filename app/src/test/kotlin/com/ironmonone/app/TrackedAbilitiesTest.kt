package com.ironmonone.app

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tracked abilities follow the PC tracker's Tracker.TrackAbility: the first
 * seen fills slot 1, a different one slot 2, and after that nothing changes.
 * They are kept across the run (reloaded from the file). Before 2026-09-27 one
 * was kept and a later reveal overwrote it.
 */
class TrackedAbilitiesTest {
    private fun marks(dir: File) = StatMarks(File(dir, "marks.txt"))

    @Test
    fun `first, then a different second, then nothing`() {
        val dir = Files.createTempDirectory("abil").toFile()
        val m = marks(dir)
        assertTrue(m.revealAbility(25, "Static"))
        assertFalse(m.revealAbility(25, "Static"), "the same one again changes nothing")
        assertTrue(m.revealAbility(25, "Lightning Rod"))
        assertFalse(m.revealAbility(25, "Overgrow"), "a third is ignored, as the reference does")
        assertEquals(listOf("Static", "Lightning Rod"), m.abilitiesFor(25))
        assertEquals("Static", m.abilityFor(25))
        assertEquals("Lightning Rod", m.secondAbilityFor(25))
        assertFalse(m.revealAbility(4, "?"), "an unresolved name is not tracked")
    }

    @Test
    fun `kept for the whole run, and the old one-name file still loads`() {
        val dir = Files.createTempDirectory("abil2").toFile()
        marks(dir).apply { revealAbility(1, "Overgrow"); revealAbility(1, "Chlorophyll") }
        assertEquals(listOf("Overgrow", "Chlorophyll"), marks(dir).abilitiesFor(1))

        DiskWriter.drain()   // the saves above are on their way to disk (rc32 audit P2 #90): there before the old file goes over them
        File(dir, "abilities.txt").writeText("7:Torrent\n")
        val old = marks(dir)
        assertEquals("Torrent", old.abilityFor(7))
        assertNull(old.secondAbilityFor(7))
    }
}
