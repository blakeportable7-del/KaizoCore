package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * NotebookTrainersByArea and NotebookIndexScreen counts over the shipped
 * route tables: excluded trainers (rematches, dummies) never count, Sevii
 * areas stay out of FRLG unless asked, completed areas drop out, and the
 * species denominator is the reference's 386.
 */
class NotebookTest {
    private fun tracker(map: GameMap, defeatedFlags: Set<Int> = emptySet()): GbaTracker {
        // Flags live at SaveBlock1 + gameFlagsOffset; a fixed SaveBlock1 keeps the test simple.
        val m = map.copy(saveBlock1Fixed = 0x02025734, saveBlock1Ptr = 0)
        val ram = HashMap<Long, Byte>()
        for (id in defeatedFlags) {
            val flag = 0x500 + id; val a = m.saveBlock1Fixed + m.gameFlagsOffset + flag / 8
            ram[a] = ((ram[a]?.toInt() ?: 0) or (1 shl (flag % 8))).toByte()
        }
        return GbaTracker(MemoryReader { a, n -> ByteArray(n) { ram[a + it] ?: 0 } }, m)
    }

    @Test
    fun `excluded and rival trainers are filtered and Sevii is optional on FRLG`() {
        val t = tracker(GameMap.FIRERED_U_V10)
        assertFalse(t.trainerCounts(50))    // in 1..88, dummy
        assertTrue(t.trainerCounts(102))
        assertTrue(t.trainerCounts(326))    // rival, before the choice is known
        val withoutSevii = t.notebookAreas(includeSevii = false, includeCompleted = true)
        val withSevii = t.notebookAreas(includeSevii = true, includeCompleted = true)
        assertTrue(withoutSevii.all { it.routeId < 230 })
        assertTrue(withSevii.size > withoutSevii.size)
        assertEquals(386, t.notebookSpeciesTotal())
    }

    @Test
    fun `a beaten area drops out unless completed areas are shown`() {
        val fresh = tracker(GameMap.EMERALD_U)
        val route = fresh.notebookAreas(true, true).first { it.total == 1 }
        val id = fresh.trainersOnRoute(route.routeId).first { fresh.trainerCounts(it) }
        val done = tracker(GameMap.EMERALD_U, setOf(id))
        assertTrue(done.notebookAreas(true, false).none { it.routeId == route.routeId })
        assertEquals(1, done.notebookAreas(true, true).first { it.routeId == route.routeId }.defeated)
        assertEquals(1, done.notebookTrainerTotals(true).first)
        assertEquals(fresh.notebookTrainerTotals(true).second, done.notebookTrainerTotals(true).second)
    }
}
