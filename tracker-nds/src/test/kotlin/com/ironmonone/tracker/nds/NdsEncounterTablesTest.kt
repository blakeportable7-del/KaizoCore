package com.ironmonone.tracker.nds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** LOCATION_DATA encounters, as the DS tracker's encounter frame reads them (2026-09-28). */
class NdsEncounterTablesTest {
    @Test
    fun `each table loads and keeps the reference's slots`() {
        // LocationData.lua, Platinum: Route 202 has 4 species; slot 1 is Lv3 5%, Lv5 30%, Lv6 5%.
        val r202 = NdsEncounterTables.area("DPPT", "Route 202")!!
        assertEquals(4, r202.totalPokemon)
        assertEquals(listOf("Level 3 (5%)", "Level 5 (30%)", "Level 6 (5%)"), r202.slots[0].map { it.label() })
        assertTrue(!r202.usesRange)
        assertEquals(5, NdsEncounterTables.table("pt").size)
        assertEquals(12, NdsEncounterTables.table("hgss").size)
        assertEquals(7, NdsEncounterTables.table("bw").size)
        assertEquals(4, NdsEncounterTables.table("b2w2").size)
        // HeartGold/SoulSilver's contest areas are keyed by weekday, as the tracker renames them.
        assertEquals(10, NdsEncounterTables.area("HGSS", "Tues Bug Catching")?.totalPokemon)
        assertEquals(3, NdsEncounterTables.area("HGSS", "Route 29")?.totalPokemon)
    }

    @Test
    fun `an area without vanilla data has no frame`() {
        assertNull(NdsEncounterTables.area("DPPT", "Route 210"))
        assertNull(NdsEncounterTables.area("DPPT", ""))
        assertNull(NdsEncounterTables.area("GSC", "Route 29"))
    }
}
