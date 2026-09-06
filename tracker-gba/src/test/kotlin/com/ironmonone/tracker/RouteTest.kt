package com.ironmonone.tracker

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The route tables, extracted from the reference tracker's RouteData.lua.
 *
 * Extraction is the risky part, not the lookup: FRLG writes its routes as a
 * table literal while RSE assigns them one at a time and expresses some ids as
 * "N + offset", which is why a first pass pulled 192 routes for one game and
 * zero for the other while looking like it had worked.
 */
class RouteTest {

    private val cleanRom = File(
        "C:/Users/bepor/IronMonOne/.vendor/clean-emerald/Pokemon - Emerald Version (USA, Europe).gba"
    )

    private fun tracker(): GbaTracker? {
        if (!cleanRom.exists()) return null
        val rom = cleanRom.readBytes()
        return GbaTracker(MemoryReader { address, length ->
            val off = (address - 0x08000000L).toInt()
            if (address >= 0x08000000L && off >= 0 && off + length <= rom.size)
                rom.copyOfRange(off, off + length)
            else ByteArray(0)
        })
    }

    @Test
    fun `the RSE table loads and names the map the starter is chosen on`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        // Map 17 is RouteData.Locations.IsInLab for RSE - Route 101, where
        // Birch's bag is. If this is wrong the ball picker shows in the wrong
        // place, which is exactly what the id is for.
        assertEquals("Route 101", t.routeInfo(17)?.first)
        // All three: Poochyena 263 and Zigzagoon 261 are written in the Lua as
        // {Ruby, Sapphire, Emerald} tuples, which the first extractor DROPPED
        // - Emerald's first route was missing two of its three species and
        // looked fine. The tuple resolves at index 2 (Emerald).
        val land = t.routeInfo(17)?.second ?: emptyList()
        assertTrue(land.containsAll(listOf(261, 263, 265)), "Route 101: $land")
    }

    @Test
    fun `a city with only water encounters still resolves`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        assertEquals("Petalburg City", t.routeInfo(1)?.first)
    }

    @Test
    fun `the table is populated, not silently empty`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        // An extraction that produced nothing would still let every lookup
        // return null and every caller behave "gracefully".
        val found = (0..300).count { t.routeInfo(it) != null }
        assertTrue(found > 150, "expected the full RSE route table, got $found")
    }

    @Test
    fun `trainer route data loads and agrees with the route table`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        // Emerald's Petalburg Gym is map 65 in RouteData.CanObtainBadge; rather
        // than assert one hand-picked map, check the table is populated and
        // that every trainer listed resolves to a known class.
        val mapped = (0..300).filter { t.trainersOnRoute(it).isNotEmpty() }
        assertTrue(mapped.size > 50, "expected many mapped routes, got ${mapped.size}")

        val allIds = mapped.flatMap { t.trainersOnRoute(it) }
        assertTrue(allIds.isNotEmpty())
        // A class lookup that resolved nothing would leave every trainer
        // "Other" and the boss counts permanently zero.
        val known = allIds.count { t.trainerClassName(it) != null }
        assertTrue(
            known > allIds.size / 2,
            "most trainers should resolve to a class: $known of ${allIds.size}",
        )
        assertTrue(allIds.any { t.trainerGroup(it) in BOSS_GROUPS }, "expected some bosses")
    }

    @Test
    fun `move and ability descriptions load and are the reference's own text`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        // Move 1 is Pound, ability 1 is Stench - the first row of each table,
        // so an off-by-one in the extraction lands here first.
        assertEquals(
            "Deals damage and has no secondary effect.",
            t.moveDescription(1),
        )
        assertTrue(t.abilityDescription(1)?.startsWith("While at the head of the party") == true)
        // Gen 3 has 354 moves and 77 abilities; an extraction that stopped
        // early would still answer for the low ids and look fine.
        assertTrue(t.moveDescription(354) != null, "last move should have a description")
        assertTrue(t.abilityDescription(77) != null, "last ability should have a description")
        assertEquals(null, t.moveDescription(9999))
    }

    @Test
    fun `species extras and computed weaknesses are right`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        // Bulbasaur: evolves at 16, weighs 6.9kg. Internal id 1.
        assertEquals("16", t.evolution(1))
        assertEquals("6.9", t.weight(1))
        // Venusaur is fully evolved, so there is no method to report.
        assertEquals(null, t.evolution(3))

        // Grass/Poison: quadruple from Psychic? No - Psychic hits Poison for 2
        // and Grass for 1, so 2x. The 4x here is Flying is not either. Check
        // the real one: Fire is 2x on Grass, 1x on Poison = 2x.
        val eff = t.effectivenessAgainst(1)
        assertTrue(eff[2.0]?.contains("Fire") == true, "Fire should be 2x on Bulbasaur")
        assertTrue(eff[2.0]?.contains("Flying") == true, "Flying should be 2x")
        assertTrue(eff[2.0]?.contains("Psychic") == true, "Psychic should be 2x")
        // Grass resists Water; Poison resists Grass, so Grass is quartered.
        assertTrue(eff[0.25]?.contains("Grass") == true, "Grass should be 1/4 on Grass/Poison")
        assertTrue(eff[0.5]?.contains("Water") == true, "Water should be resisted")
    }

    @Test
    fun `encounter areas keep their split and use the reference's labels`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        // Petalburg City is water-only: surfing plus the three rods, and no
        // walking encounters at all. If the areas were being merged this would
        // come back as one bucket.
        val areas = t.routeEncounterAreas(1)
        assertTrue(areas.containsKey("Surfing"), "expected Surfing, got ${areas.keys}")
        assertTrue(areas.containsKey("Old Rod"), "expected Old Rod, got ${areas.keys}")
        assertTrue(!areas.containsKey("Walking"), "Petalburg City has no land encounters")

        // Route 101 is the opposite: land only.
        val r101 = t.routeEncounterAreas(17)
        assertTrue(r101.containsKey("Walking"), "expected Walking, got ${r101.keys}")

        // The flat list must be the same species, just collapsed.
        val flat = t.routeInfo(1)?.second ?: emptyList()
        assertEquals(areas.values.flatten().toSet(), flat.toSet())
    }

    @Test
    fun `an unknown map id is null rather than invented`() {
        val t = tracker() ?: run { println("SKIP: clean dump missing"); return }
        assertEquals(null, t.routeInfo(9999))
    }
}
