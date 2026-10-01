package com.ironmonone.app

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** The DS encounter frame's rules (HoverFrameFactory) and its record (Tracker.updateEncounterData). */
class DsEncounterFrameTest {
    private val names = mapOf(16 to "Pidgey", 19 to "Rattata", 161 to "Sentret", 21 to "Spearow")

    @Test
    fun `tracked rows sort by their levels, then by name`() {
        // sortTrackedEncounters: element by element; a list that runs out first sorts first.
        val seen = mapOf(19 to listOf(3, 5), 16 to listOf(3), 161 to listOf(2), 21 to listOf(3))
        assertEquals(listOf(161, 16, 21, 19), sortTrackedEncounters(seen) { names.getValue(it) })
    }

    @Test
    fun `levels read as a range only where the area's data is ranges`() {
        assertEquals("Lv 3, 5", trackedLevelsText(listOf(3, 5), usesRange = false))
        assertEquals("Level 3 - 7", trackedLevelsText(listOf(3, 5, 7), usesRange = true))
        assertEquals("?", trackedLevelsText(null, usesRange = false))
    }

    @Test
    fun `encounters are kept per area with sorted distinct levels, survive a reload, and clear on a new run`() {
        val dir = kotlin.io.path.createTempDirectory("dsenc").toFile()
        try {
            val m = StatMarks(File(dir, "marks.txt"))
            m.seeDsEncounter("Route 29", 16, 5)
            m.seeDsEncounter("Route 29", 16, 3)
            m.seeDsEncounter("Route 29", 16, 5)
            m.seeDsEncounter("Route 30", 19, 4)
            val again = StatMarks(File(dir, "marks.txt"))
            assertEquals(mapOf(16 to listOf(3, 5)), again.dsEncountersIn("Route 29"))
            assertEquals(mapOf(19 to listOf(4)), again.dsEncountersIn("Route 30"))
            again.clear()
            assertEquals(emptyMap(), StatMarks(File(dir, "marks.txt")).dsEncountersIn("Route 29"))
        } finally { dir.deleteRecursively() }
    }
}
