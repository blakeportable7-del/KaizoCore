package com.ironmonone.app

import kotlin.test.Test
import kotlin.test.assertEquals

/** StatsScreen's ten rows, in the reference's order; PCs used is two game stats added. */
class StatsRowsTest {
    @Test
    fun `rows follow the reference and a game without stats reads zeros`() {
        val rows = StatsRows.build(37) { i -> mapOf(15 to 4, 16 to 2, 9 to 12, 8 to 40, 11 to 3, 38 to 5, 0 to 6, 5 to 9999, 27 to 1)[i] ?: 0 }
        assertEquals(listOf("Play Time", "Total Attempts", "PCs Used", "Trainer Battles", "Wild Encounters", "Pokemon Caught", "Shop Purchases", "Game Saves", "Total Steps", "Struggles Used"), rows.map { it.first })
        assertEquals("37", rows[1].second); assertEquals("6", rows[2].second); assertEquals("9999", rows[8].second)
        assertEquals("0", StatsRows.build(1, null)[3].second)
    }
}
