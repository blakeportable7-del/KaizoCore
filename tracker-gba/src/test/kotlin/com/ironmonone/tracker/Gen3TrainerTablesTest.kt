package com.ironmonone.tracker

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * TrainerData.buildData runs a different setup per game (TrainerData.lua:131-144), and Ruby and
 * Sapphire's (setupTrainersAsRubySapphire, TrainerData.lua:503-638) disagrees with Emerald's on 229
 * of its 692 ids. Ruby and Sapphire used to load Emerald's tables, so Courtney (599) was taken for
 * Brendan and set the run's rival, Archie and Maxie were a Hiker and a Triathlete, and every Boss
 * count and log filter keyed on them was wrong (parity audit, 2026-09-28).
 */
class Gen3TrainerTablesTest {
    private fun tracker(map: GameMap) = GbaTracker(MemoryReader { _, n -> ByteArray(n) }, map)

    @Test
    fun `Ruby and Sapphire use their own classes and groups`() {
        for (map in listOf(GameMap.RUBY_U, GameMap.SAPPHIRE_U)) {
            val t = tracker(map)
            // classToTrainers: Archie = { 1, 34, 35 }, Maxie = { 566, 601, 602 }, Courtney = { 599, 600 }.
            for (id in listOf(1, 34, 35)) assertEquals("Archie" to "Boss", t.trainerClassName(id) to t.trainerGroup(id), "${map.name} $id")
            for (id in listOf(566, 601, 602)) assertEquals("Maxie" to "Boss", t.trainerClassName(id) to t.trainerGroup(id), "${map.name} $id")
            for (id in listOf(599, 600)) assertEquals("Courtney", t.trainerClassName(id), "${map.name} $id")
            assertEquals("GymLeader8", t.trainerClassName(272))
            // R/S has 692 trainers; Emerald's extra ids (694-855) are nobody here.
            assertNull(t.trainerClassName(768)); assertNull(t.trainerClassName(804))
            assertEquals("Other", t.trainerGroup(804))
        }
    }

    @Test
    fun `Ruby and Sapphire rivals are 520-537 and 661-666 only`() {
        val t = tracker(GameMap.RUBY_U)
        // TrainerData.lua:611-635.
        assertEquals((520..537).toList() + (661..666).toList(), (1..900).filter { t.whichRival(it) != null })
        assertEquals("Brendan Left", t.whichRival(661)); assertEquals("May Right", t.whichRival(666))
        // Courtney is a boss, not Brendan: she no longer decides which rival this run has.
        assertNull(t.whichRival(599)); assertNull(t.whichRival(600))
        assertNull(tracker(GameMap.SAPPHIRE_U).whichRival(768))
    }

    @Test
    fun `Emerald keeps its own table`() {
        val t = tracker(GameMap.EMERALD_U)
        // setupTrainersAsEmerald: Archie = { 34 }, 1 is a Hiker, 599 and 768 are rivals (TrainerData.lua:679-784).
        assertEquals("Hiker", t.trainerClassName(1)); assertEquals("Archie", t.trainerClassName(34))
        assertEquals("Triathlete", t.trainerClassName(566)); assertEquals("Other", t.trainerGroup(566))
        assertEquals("Brendan Right", t.whichRival(599)); assertEquals("May Middle", t.whichRival(768))
        assertEquals("Steven", t.trainerClassName(804))
        assertEquals(30, (1..900).count { t.whichRival(it) != null })
    }

    @Test
    fun `FRLG 666 is the Beauty the ROM says, not BirdKeeper by hash order`() {
        // TrainerData.lua lists 666 under Beauty and inside BirdKeeper's {662, 668}; pairs() decides
        // which wins in the PC tracker. The FireRed ROM's own trainer 666 is BEAUTY GRACE.
        val t = tracker(GameMap.FIRERED_U_V10)
        assertEquals("Beauty", t.trainerClassName(666))
        assertEquals("BirdKeeper", t.trainerClassName(665))
    }
}
